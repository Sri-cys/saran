package com.psgitech.library.service;

import com.psgitech.library.config.FineConfig;
import com.psgitech.library.enums.AuditEventType;
import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.FineNotFoundException;
import com.psgitech.library.exception.InvalidFineOperationException;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.FineAdjustmentRepository;
import com.psgitech.library.repository.FineRepository;
import com.psgitech.library.repository.PaymentAuditRepository;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.TransactionRetryHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service managing overdue book fine calculations, waivers, summaries, and eligibility checks.
 */
public class FineService {
    private static final Logger LOGGER = Logger.getLogger(FineService.class.getName());

    private final FineRepository fineRepository;
    private final FineAdjustmentRepository fineAdjustmentRepository;
    private final PaymentAuditRepository auditRepository;
    private final BorrowRepository borrowRepository;
    private final FineCalculator fineCalculator;

    public FineService() {
        this.fineRepository = new FineRepository();
        this.fineAdjustmentRepository = new FineAdjustmentRepository();
        this.auditRepository = new PaymentAuditRepository();
        this.borrowRepository = new BorrowRepository();
        this.fineCalculator = new FineCalculator();
    }

    public FineService(FineRepository fineRepo, FineAdjustmentRepository adjRepo,
                       PaymentAuditRepository auditRepo, BorrowRepository borrowRepo,
                       FineCalculator fineCalc) {
        this.fineRepository = fineRepo;
        this.fineAdjustmentRepository = adjRepo;
        this.auditRepository = auditRepo;
        this.borrowRepository = borrowRepo;
        this.fineCalculator = fineCalc;
    }

    /**
     * Atomically creates a fine record inside the existing return transaction connection.
     * Evaluates calendar overdue days, chargeable days, and rate snapshot.
     */
    public FineRecord createFineForReturn(Connection conn, int txId, int studentId,
                                         int bookId, int copyId,
                                         LocalDate dueDate, LocalDate returnDate) throws SQLException {
        FineCalculation calc = fineCalculator.calculateFine(dueDate, returnDate);

        if (!calc.isFineIncurred()) {
            LOGGER.info("Book return for transaction #" + txId + " is on-time or within grace. No fine incurred.");
            return null;
        }

        LOGGER.info("Overdue return detected for transaction #" + txId + ". Overdue days: " +
                calc.getOverdueDays() + ", fine amount: INR " + calc.getFineAmount());

        FineRecord record = new FineRecord(txId, studentId, bookId, copyId, calc);
        FineRecord saved = fineRepository.createFine(conn, record);

        // Record audit log
        PaymentAuditEntry audit = new PaymentAuditEntry(
                saved.getFineId(),
                null,
                AuditEventType.FINE_CREATED,
                null,
                saved.getStatus().name(),
                saved.getOriginalAmount(),
                null,
                "SYSTEM",
                "Fine created on return of transaction #" + txId + " (" + calc.getOverdueDays() + " overdue days @ INR " + calc.getFinePerDay() + "/day)"
        );
        auditRepository.recordAuditEvent(conn, audit);

        // Record Feature 11: FINE_CREATED
        try {
            com.psgitech.library.model.NotificationDraft notifDraft = com.psgitech.library.model.NotificationDraft
                    .builder(com.psgitech.library.enums.NotificationType.FINE_CREATED, studentId, "FINE_CREATED:" + saved.getFineId())
                    .entity("FINE", saved.getFineId())
                    .param("amount", saved.getOriginalAmount() != null ? saved.getOriginalAmount().toPlainString() : "0.00")
                    .param("bookTitle", "Overdue Book Loan #" + txId)
                    .build();
            new NotificationRecorder().record(conn, notifDraft);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
            LOGGER.log(Level.WARNING, "Failed to record fine created notification", e);
        }

        return saved;
    }

    public FineRecord getFineById(int fineId) {
        FineRecord fine = fineRepository.findById(fineId);
        if (fine == null) {
            throw new FineNotFoundException("Fine record #" + fineId + " was not found.");
        }
        return fine;
    }

    public List<FineRecord> getFinesByStudent(int studentId) {
        return fineRepository.findFinesByStudent(studentId);
    }

    public Map<String, Object> getStudentFineSummary(int studentId) {
        List<FineRecord> fines = fineRepository.findFinesByStudent(studentId);
        BigDecimal totalOutstanding = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalWaived = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int unpaidCount = 0;

        for (FineRecord f : fines) {
            if (f.getStatus() == FineStatus.UNPAID || f.getStatus() == FineStatus.PARTIALLY_PAID) {
                unpaidCount++;
                totalOutstanding = totalOutstanding.add(f.getOutstandingAmount());
            }
            totalPaid = totalPaid.add(f.getPaidAmount());
            totalWaived = totalWaived.add(f.getWaivedAmount());
        }

        boolean borrowingBlocked = totalOutstanding.compareTo(FineConfig.getBorrowBlockThreshold()) > 0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalOutstanding", totalOutstanding.toPlainString());
        summary.put("totalPaid", totalPaid.toPlainString());
        summary.put("totalWaived", totalWaived.toPlainString());
        summary.put("unpaidCount", unpaidCount);
        summary.put("borrowingBlocked", borrowingBlocked);
        summary.put("blockThreshold", FineConfig.getBorrowBlockThreshold().toPlainString());
        return summary;
    }

    /**
     * Calculates projected fines for currently unreturned, active overdue borrowings.
     * These values are computed on-the-fly and never stored.
     */
    public List<Map<String, Object>> getProjectedFinesForStudent(int studentId) {
        List<BorrowTransaction> activeLoans = borrowRepository.findActiveByStudentId(studentId);
        List<Map<String, Object>> projectedList = new ArrayList<>();

        for (BorrowTransaction tx : activeLoans) {
            if (tx.getDueDate() != null) {
                FineCalculation calc = fineCalculator.calculateProjectedFine(tx.getDueDate());
                if (calc.isFineIncurred()) {
                    Map<String, Object> proj = new HashMap<>();
                    proj.put("transactionId", tx.getTransactionId());
                    proj.put("bookTitle", tx.getBookTitle());
                    proj.put("copyCode", tx.getCopyCode());
                    proj.put("dueDate", tx.getDueDate().toString());
                    proj.put("overdueDays", calc.getOverdueDays());
                    proj.put("projectedAmount", calc.getFineAmount().toPlainString());
                    proj.put("ratePerDay", calc.getFinePerDay().toPlainString());
                    proj.put("isProjected", true);
                    projectedList.add(proj);
                }
            }
        }
        return projectedList;
    }

    /**
     * Checks if a student has any outstanding fine balance above the blocking threshold.
     */
    public boolean hasOutstandingFines(int studentId, Connection conn) throws SQLException {
        return fineRepository.hasOutstandingFines(studentId, conn);
    }

    public boolean hasOutstandingFines(int studentId) {
        return fineRepository.hasOutstandingFines(studentId);
    }

    /**
     * Applies an administrative fine waiver. (ADMIN ONLY per Decision D7).
     */
    public FineRecord applyWaiver(int fineId, BigDecimal waiverAmount, String reason, int adminUserId, Role adminRole) {
        if (adminRole != Role.ADMIN) {
            throw new InvalidFineOperationException("Access Denied: Only Administrators can waive library fines.");
        }
        if (waiverAmount == null || waiverAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidFineOperationException("Waiver amount must be greater than zero.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidFineOperationException("Waiver reason is mandatory.");
        }

        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // Lock fine row FOR UPDATE
                FineRecord fine = fineRepository.lockFineById(conn, fineId);
                if (fine == null) {
                    throw new FineNotFoundException("Fine record #" + fineId + " not found.");
                }
                if (fine.getStatus() == FineStatus.PAID || fine.getStatus() == FineStatus.WAIVED) {
                    throw new InvalidFineOperationException("Fine #" + fineId + " is already fully settled (" + fine.getStatus() + ").");
                }
                if (waiverAmount.compareTo(fine.getOutstandingAmount()) > 0) {
                    throw new InvalidFineOperationException("Waiver amount (INR " + waiverAmount +
                            ") exceeds current outstanding fine (INR " + fine.getOutstandingAmount() + ").");
                }

                String oldStatus = fine.getStatus().name();
                fine.applyWaiver(waiverAmount);

                // Insert adjustment
                FineAdjustment adj = new FineAdjustment(fineId, waiverAmount, reason.trim(), adminUserId);
                fineAdjustmentRepository.createAdjustment(conn, adj);

                // Update fine
                int updated = fineRepository.updateFineAmountsAndStatus(conn, fineId, fine.getPaidAmount(),
                        fine.getWaivedAmount(), fine.getOutstandingAmount(), fine.getStatus());
                if (updated == 0) {
                    throw new InvalidFineOperationException("Concurrent modification detected while applying waiver.");
                }

                // Record audit
                PaymentAuditEntry audit = new PaymentAuditEntry(
                        fineId,
                        null,
                        AuditEventType.WAIVER_APPLIED,
                        oldStatus,
                        fine.getStatus().name(),
                        waiverAmount,
                        adminUserId,
                        adminRole.name(),
                        "Waiver of INR " + waiverAmount.toPlainString() + " applied by Admin #" + adminUserId + ". Reason: " + reason.trim()
                );
                auditRepository.recordAuditEvent(conn, audit);

                // Record Feature 11: FINE_WAIVED
                try {
                    com.psgitech.library.model.NotificationDraft notifDraft = com.psgitech.library.model.NotificationDraft
                            .builder(com.psgitech.library.enums.NotificationType.FINE_WAIVED, fine.getStudentId(), "FINE_WAIVED:" + fineId + ":" + waiverAmount)
                            .entity("FINE", fineId)
                            .param("amount", waiverAmount.toPlainString())
                            .param("bookTitle", "Library Fine #" + fineId)
                            .build();
                    new NotificationRecorder().record(conn, notifDraft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record fine waiver notification", e);
                }

                conn.commit();
                return fine;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "applyWaiver");
    }

    public List<FineRecord> getFinesPaginated(String regNoFilter, FineStatus statusFilter, int page, int pageSize) {
        return fineRepository.findFinesPaginated(regNoFilter, statusFilter, page, pageSize);
    }

    public int countFines(String regNoFilter, FineStatus statusFilter) {
        return fineRepository.countFines(regNoFilter, statusFilter);
    }
}

