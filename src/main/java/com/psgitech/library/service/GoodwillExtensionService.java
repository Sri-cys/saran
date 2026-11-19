package com.psgitech.library.service;

import com.psgitech.library.dto.librarian.TrustStandingDTO;
import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.GoodwillExtension;
import com.psgitech.library.model.NotificationDraft;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.GoodwillExtensionRepository;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating the one-time-per-student Goodwill Due Date Extension (+2 days).
 * Enforces business rules:
 * 1. Exactly one goodwill extension per student lifetime (enforced by DB UNIQUE constraint + pre-check).
 * 2. Student must have EXEMPLARY Trust Standing.
 * 3. Book must have zero waiting reservations.
 * 4. Loan must be open (ACTIVE) and non-overdue.
 * 5. Transferred loans' original due dates are never modified.
 * 6. Mandatory reason required.
 * 7. Complete audit logging and notification.
 */
public class GoodwillExtensionService {
    private static final Logger LOGGER = Logger.getLogger(GoodwillExtensionService.class.getName());

    private final GoodwillExtensionRepository extensionRepo;
    private final BorrowRepository borrowRepo;
    private final ActivityLogService logService;
    private final TrustStandingService trustStandingService;

    public GoodwillExtensionService() {
        this.extensionRepo = new GoodwillExtensionRepository();
        this.borrowRepo = new BorrowRepository();
        this.logService = new ActivityLogService();
        this.trustStandingService = new TrustStandingService(this.extensionRepo);
    }

    public GoodwillExtensionService(GoodwillExtensionRepository extensionRepo,
                                  BorrowRepository borrowRepo,
                                  ActivityLogService logService) {
        this(extensionRepo, borrowRepo, logService, new TrustStandingService(extensionRepo));
    }

    public GoodwillExtensionService(GoodwillExtensionRepository extensionRepo,
                                  BorrowRepository borrowRepo,
                                  ActivityLogService logService,
                                  TrustStandingService trustStandingService) {
        this.extensionRepo = extensionRepo;
        this.borrowRepo = borrowRepo;
        this.logService = logService;
        this.trustStandingService = trustStandingService;
    }

    public boolean hasUsedGoodwillExtension(int studentId) {
        return extensionRepo.hasUsedGoodwillExtension(studentId);
    }

    public GoodwillExtension getExtensionForStudent(int studentId) {
        return extensionRepo.findByStudentId(studentId);
    }

    public List<GoodwillExtension> getRecentExtensions(int limit) {
        return extensionRepo.findRecentExtensions(limit > 0 ? limit : 20);
    }

    /**
     * Grants a one-time goodwill extension (+2 days) to an active loan.
     */
    public GoodwillExtension grantGoodwillExtension(int studentId, int transactionId, int extraDays,
                                                  String reason, int librarianId, String ipAddress) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A valid justification reason is required for granting a goodwill extension.");
        }
        if (extraDays <= 0 || extraDays > 14) {
            extraDays = 2; // Default +2 days extension per system specifications
        }

        // 1. Verify student has not already used their one-time extension
        if (extensionRepo.hasUsedGoodwillExtension(studentId)) {
            throw new IllegalStateException("Student #" + studentId + " has already been granted their one-time goodwill extension.");
        }

        // 2. Verify transaction exists, belongs to student, and is ACTIVE
        BorrowTransaction tx = borrowRepo.findById(transactionId);
        if (tx == null) {
            throw new IllegalArgumentException("Transaction #" + transactionId + " not found.");
        }
        if (tx.getStudentId() != studentId) {
            throw new IllegalArgumentException("Transaction #" + transactionId + " does not belong to student #" + studentId + ".");
        }
        if (tx.getStatus() != BorrowStatus.ACTIVE) {
            throw new IllegalStateException("Cannot grant goodwill extension: Loan is not in ACTIVE status (current status: " + tx.getStatus() + ").");
        }

        // 3. Verify loan is NOT overdue
        LocalDate today = LocalDate.now(com.psgitech.library.config.FineConfig.getLibraryZone());
        if (tx.getDueDate() != null && tx.getDueDate().isBefore(today)) {
            throw new IllegalStateException("Cannot grant goodwill extension on an overdue loan. Please settle outstanding dues.");
        }

        // 4. Verify student has EXEMPLARY trust standing
        TrustStandingDTO trust = trustStandingService.computeTrustStanding(studentId);
        if (trust != null && trust.getTier() != null && trust.getTier() != TrustStandingDTO.Tier.EXEMPLARY) {
            throw new IllegalStateException("Goodwill extensions are reserved strictly for students with Exemplary Trust Standing (Current tier: " + trust.getTier() + ").");
        }

        // 5. Verify book has ZERO waiting reservations and is not a transferred loan
        try (Connection checkConn = DBConnection.getConnection()) {
            // Check waiting reservations
            String resCountSql = "SELECT COUNT(*) FROM reservations WHERE book_id = ? AND status = 'WAITING'";
            try (PreparedStatement psRes = checkConn.prepareStatement(resCountSql)) {
                psRes.setInt(1, tx.getBookId());
                try (ResultSet rs = psRes.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new IllegalStateException("Cannot grant goodwill extension: " + rs.getInt(1) + " student(s) are actively waiting in queue for this title.");
                    }
                }
            }

            // Check if loan was transferred via student interchange
            String transferSql = "SELECT COUNT(*) FROM book_interchanges WHERE transaction_id = ? AND status = 'COMPLETED'";
            try (PreparedStatement psTrans = checkConn.prepareStatement(transferSql)) {
                psTrans.setInt(1, transactionId);
                try (ResultSet rs = psTrans.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new IllegalStateException("Goodwill extensions cannot be applied to transferred loans. Original due dates must be preserved.");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Validation error before applying goodwill extension", e);
            throw new RuntimeException("Validation error: " + e.getMessage(), e);
        }

        LocalDate previousDueDate = tx.getDueDate();
        if (previousDueDate == null) {
            previousDueDate = today;
        }
        LocalDate newDueDate = previousDueDate.plusDays(extraDays);

        GoodwillExtension ext = new GoodwillExtension(
                studentId, transactionId, extraDays, reason.trim(),
                previousDueDate, newDueDate, librarianId
        );

        // 6. Atomically update loan and insert goodwill extension
        try (Connection conn = DBConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // Update borrow_transactions due date
                String updateSql = "UPDATE borrow_transactions SET due_date = ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE transaction_id = ? AND status = 'ACTIVE'";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setDate(1, Date.valueOf(newDueDate));
                    ps.setInt(2, transactionId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        throw new IllegalStateException("Failed to update transaction due date; status may have changed concurrently.");
                    }
                }

                // Insert into goodwill_extensions
                extensionRepo.insert(ext, conn);

                // Send notification to student
                try {
                    NotificationDraft draft = NotificationDraft
                            .builder(NotificationType.LOAN_RENEWED, studentId, "GOODWILL_EXT:" + transactionId)
                            .entity("BORROW_TRANSACTION", transactionId)
                            .param("title", tx.getBookTitle() != null ? tx.getBookTitle() : "Book Loan")
                            .param("newDueDate", newDueDate.toString())
                            .param("extraDays", String.valueOf(extraDays))
                            .param("reason", reason.trim())
                            .build();
                    new com.psgitech.library.service.NotificationRecorder().record(conn, draft);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to record notification for goodwill extension, proceeding with transaction commit", e);
                }

                conn.commit();
                conn.setAutoCommit(originalAutoCommit);

            } catch (SQLException e) {
                conn.rollback();
                conn.setAutoCommit(originalAutoCommit);
                if (e.getErrorCode() == 1062 || (e.getMessage() != null && e.getMessage().contains("Duplicate entry"))) {
                    throw new IllegalStateException("Student has already used their one-time goodwill extension.", e);
                }
                throw new RuntimeException("Database error applying goodwill extension: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error applying goodwill extension", e);
        }

        // 7. Log staff activity
        logService.logEvent(
                "GOODWILL_EXTENSION_GRANTED",
                librarianId,
                "LIBRARIAN",
                "BORROW_TRANSACTION",
                String.valueOf(transactionId),
                "Granted " + extraDays + " days goodwill extension to student #" + studentId + " (New due date: " + newDueDate + "). Reason: " + reason.trim(),
                ipAddress
        );

        ext.setBookTitle(tx.getBookTitle());
        ext.setStudentName(tx.getStudentName());
        return ext;
    }
}
