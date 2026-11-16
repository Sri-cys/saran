package com.psgitech.library.service;

import com.psgitech.library.config.FineConfig;
import com.psgitech.library.config.LossDamageConfig;
import com.psgitech.library.enums.*;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.*;
import com.psgitech.library.policy.MonetaryCompensation;
import com.psgitech.library.policy.PenaltyResolution;
import com.psgitech.library.policy.PhysicalReplacement;
import com.psgitech.library.repository.*;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.TransactionRetryHelper;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating Lost and Damaged book replacement lifecycle.
 * Follows canonical lock order and wraps mutations in TransactionRetryHelper.
 */
public class LossDamageService {
    private static final Logger LOGGER = Logger.getLogger(LossDamageService.class.getName());

    private final LossDamageCaseRepository caseRepository;
    private final FineRepository fineRepository;
    private final BookRepository bookRepository;
    private final BorrowRepository borrowRepository;
    private final AllocationService allocationService;
    private final CompensationCalculator calculator;
    private final NotificationRecorder notificationRecorder;

    public LossDamageService() {
        this.caseRepository = new LossDamageCaseRepository();
        this.fineRepository = new FineRepository();
        this.bookRepository = new BookRepository();
        this.borrowRepository = new BorrowRepository();
        this.allocationService = new AllocationService();
        this.calculator = new CompensationCalculator();
        this.notificationRecorder = new NotificationRecorder();
    }

    public LossDamageService(LossDamageCaseRepository caseRepo, FineRepository fineRepo,
                             BookRepository bookRepo, BorrowRepository borrowRepo,
                             AllocationService allocService, CompensationCalculator calc,
                             NotificationRecorder notifRecorder) {
        this.caseRepository = caseRepo;
        this.fineRepository = fineRepo;
        this.bookRepository = bookRepo;
        this.borrowRepository = borrowRepo;
        this.allocationService = allocService;
        this.calculator = calc;
        this.notificationRecorder = notifRecorder;
    }

    public LossDamageCase reportLost(int transactionId, int studentId, String note) {
        return reportLost(transactionId, studentId, note, studentId, "STUDENT");
    }

    public LossDamageCase reportLost(int transactionId, int studentId, String note, int actorId, String actorRole) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // 1. Lock student user row FIRST (Canonical Lock Order)
                String userSql = "SELECT user_id, active FROM users WHERE user_id = ? FOR UPDATE";
                try (PreparedStatement psUser = conn.prepareStatement(userSql)) {
                    psUser.setInt(1, studentId);
                    try (ResultSet rs = psUser.executeQuery()) {
                        if (!rs.next()) throw new StudentNotFoundException("Student #" + studentId + " not found.");
                        if (!rs.getBoolean("active")) throw new AccountInactiveException("Student account is inactive.");
                    }
                }

                // 2. Lock borrow_transactions row
                String txSql = "SELECT transaction_id, student_id, book_id, copy_id, status FROM borrow_transactions WHERE transaction_id = ? FOR UPDATE";
                int bookId, copyId;
                try (PreparedStatement psTx = conn.prepareStatement(txSql)) {
                    psTx.setInt(1, transactionId);
                    try (ResultSet rs = psTx.executeQuery()) {
                        if (!rs.next()) throw new BorrowTransactionNotFoundException("Borrow transaction #" + transactionId + " not found.");
                        if (rs.getInt("student_id") != studentId) {
                            throw new BorrowTransactionNotFoundException("Transaction #" + transactionId + " does not belong to student #" + studentId);
                        }
                        String st = rs.getString("status");
                        if (!BorrowStatus.ACTIVE.name().equalsIgnoreCase(st)) {
                            throw new InvalidBorrowingOperationException("Cannot report loss: transaction is not ACTIVE (current: " + st + ").");
                        }
                        bookId = rs.getInt("book_id");
                        copyId = rs.getInt("copy_id");
                    }
                }

                // 3. Verify copy is BORROWED
                String copySql = "SELECT copy_id, status FROM book_copies WHERE copy_id = ? FOR UPDATE";
                try (PreparedStatement psCopy = conn.prepareStatement(copySql)) {
                    psCopy.setInt(1, copyId);
                    try (ResultSet rs = psCopy.executeQuery()) {
                        if (!rs.next()) throw new BookCopyUnavailableException("Copy #" + copyId + " not found.");
                        String cs = rs.getString("status");
                        if (!CopyStatus.BORROWED.name().equalsIgnoreCase(cs)) {
                            throw new InvalidBorrowingOperationException("Physical copy is not in BORROWED status (current: " + cs + ").");
                        }
                    }
                }

                // 4. Check for open case (idempotent duplicate returns existing)
                LossDamageCase existing = caseRepository.findOpenCaseByTransactionId(conn, transactionId);
                if (existing != null) {
                    conn.rollback();
                    return existing;
                }

                // 5. Check for pending interchange
                String interSql = "SELECT interchange_id FROM book_interchanges WHERE transaction_id = ? AND status = 'REQUESTED'";
                try (PreparedStatement psInter = conn.prepareStatement(interSql)) {
                    psInter.setInt(1, transactionId);
                    try (ResultSet rs = psInter.executeQuery()) {
                        if (rs.next()) {
                            throw new InvalidBorrowingOperationException("Cannot report loss: an interchange request is currently pending for this book.");
                        }
                    }
                }

                // 6. Insert case in REPORTED status
                LossDamageCase c = LossDamageCase.createReported(transactionId, studentId, bookId, copyId, actorId, note);
                LossDamageCase inserted = caseRepository.insertCase(conn, c);

                // 7. Audit event
                CaseEvent event = new CaseEvent(
                        inserted.getCaseId(),
                        "CASE_REPORTED",
                        null,
                        CaseStatus.REPORTED.name(),
                        actorId,
                        actorRole,
                        (actorId == studentId ? "Student" : actorRole) + " reported book as lost. Note: " + (note != null ? note : "None")
                );
                caseRepository.recordEvent(conn, event);

                // 8. In-app staff notification
                try {
                    NotificationDraft draft = NotificationDraft
                            .builder(NotificationType.CASE_REPORTED, studentId, "CASE_REPORTED:" + inserted.getCaseId())
                            .entity("CASE", inserted.getCaseId())
                            .param("bookId", String.valueOf(bookId))
                            .build();
                    notificationRecorder.record(conn, draft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record case reported notification", e);
                }

                conn.commit();
                return inserted;

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
        }, "reportLost");
    }

    public LossDamageCase assessLost(int caseId, BigDecimal assessedValue, String note, int assessorId, String assessorRole) {
        return assessLost(caseId, null, assessorId, assessorRole, assessedValue, note);
    }

    public LossDamageCase assessLost(Integer caseId, Integer transactionId, int assessorId, String assessorRole,
                                    BigDecimal assessedValue, String note) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // Preliminary discovery
                int txId = 0;
                LossDamageCase existingCase = null;
                if (caseId != null && caseId > 0) {
                    existingCase = caseRepository.lockCaseById(conn, caseId);
                    if (existingCase == null) throw new CaseNotFoundException("Case #" + caseId + " not found.");
                    txId = existingCase.getBorrowTransactionId();
                } else if (transactionId != null && transactionId > 0) {
                    txId = transactionId;
                    existingCase = caseRepository.findOpenCaseByTransactionId(conn, txId);
                } else {
                    throw new IllegalArgumentException("Either caseId or transactionId must be provided.");
                }

                // 1. Lock student row FIRST
                int studentId;
                int bookId;
                int copyId;
                String userSql = "SELECT u.user_id, bt.student_id, bt.book_id, bt.copy_id, bt.status " +
                        "FROM borrow_transactions bt " +
                        "JOIN users u ON bt.student_id = u.user_id " +
                        "WHERE bt.transaction_id = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                    ps.setInt(1, txId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BorrowTransactionNotFoundException("Transaction #" + txId + " not found.");
                        studentId = rs.getInt("student_id");
                        bookId = rs.getInt("book_id");
                        copyId = rs.getInt("copy_id");
                        String status = rs.getString("status");
                        if (!BorrowStatus.ACTIVE.name().equalsIgnoreCase(status)) {
                            throw new InvalidBorrowingOperationException("Cannot assess loss: loan is not ACTIVE (current: " + status + ").");
                        }
                    }
                }

                // 2. Lock book row SECOND
                BigDecimal replacementPrice = null;
                String bookTitle = "";
                LocalDate bookCreated = null;
                String bookSql = "SELECT title, replacement_price, created_at FROM books WHERE book_id = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(bookSql)) {
                    ps.setInt(1, bookId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BookNotFoundException("Book #" + bookId + " not found.");
                        bookTitle = rs.getString("title");
                        replacementPrice = rs.getBigDecimal("replacement_price");
                        Timestamp ts = rs.getTimestamp("created_at");
                        if (ts != null) bookCreated = ts.toLocalDateTime().toLocalDate();
                    }
                }

                // 3. Lock copy row THIRD
                LocalDate copyCreated = null;
                String copySql = "SELECT copy_id, copy_code, status, created_at FROM book_copies WHERE copy_id = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(copySql)) {
                    ps.setInt(1, copyId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BookCopyUnavailableException("Copy #" + copyId + " not found.");
                        String cs = rs.getString("status");
                        if (!CopyStatus.BORROWED.name().equalsIgnoreCase(cs)) {
                            LOGGER.warning("Copy #" + copyId + " status was " + cs + " during loss assessment.");
                        }
                        Timestamp ts = rs.getTimestamp("created_at");
                        if (ts != null) copyCreated = ts.toLocalDateTime().toLocalDate();
                    }
                }

                // Determine price to use
                BigDecimal priceToUse = replacementPrice;
                if (priceToUse == null || priceToUse.compareTo(BigDecimal.ZERO) <= 0) {
                    if (assessedValue == null || assessedValue.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new MissingReplacementPriceException("Catalog book has no replacement price. Assessed replacement value must be provided.");
                    }
                    priceToUse = assessedValue;
                }

                // Determine acquisition date for age
                LocalDate acquiredDate = (copyCreated != null) ? copyCreated : bookCreated;

                // 4. Compute compensation breakdown
                CompensationBreakdown breakdown = calculator.calculate(CaseType.LOST, null, priceToUse, acquiredDate);

                // 5. Close loan as LOST
                ZoneId libraryZone = FineConfig.getLibraryZone();
                LocalDate today = LocalDate.now(libraryZone);
                String closeTxSql = "UPDATE borrow_transactions SET status = 'LOST', return_date = ?, returned_by = ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE transaction_id = ? AND status = 'ACTIVE'";
                try (PreparedStatement ps = conn.prepareStatement(closeTxSql)) {
                    ps.setDate(1, Date.valueOf(today));
                    ps.setInt(2, assessorId);
                    ps.setInt(3, txId);
                    int affected = ps.executeUpdate();
                    if (affected == 0) {
                        throw new InvalidBorrowingOperationException("Failed to close loan as LOST. Concurrent modification detected.");
                    }
                }

                // 6. Retire physical copy as ARCHIVED
                String retireCopySql = "UPDATE book_copies SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP " +
                        "WHERE copy_id = ? AND status = 'BORROWED'";
                try (PreparedStatement ps = conn.prepareStatement(retireCopySql)) {
                    ps.setInt(1, copyId);
                    int affected = ps.executeUpdate();
                    if (affected == 0) {
                        LOGGER.warning("Could not set copy #" + copyId + " to ARCHIVED from BORROWED (may already be updated).");
                    }
                }

                // 7. Cancel any pending interchange
                String cancelInterSql = "UPDATE book_interchanges SET status = 'CANCELLED', rejection_reason = 'Cancelled: book confirmed lost', " +
                        "updated_at = CURRENT_TIMESTAMP WHERE transaction_id = ? AND status = 'REQUESTED'";
                try (PreparedStatement ps = conn.prepareStatement(cancelInterSql)) {
                    ps.setInt(1, txId);
                    ps.executeUpdate();
                }

                // 8. Update or insert case row in ASSESSED status
                LossDamageCase resultCase;
                if (existingCase != null) {
                    CaseStatus oldStatus = existingCase.getStatus();
                    existingCase.assessLost(assessorId, breakdown);
                    if (note != null && !note.trim().isEmpty()) {
                        existingCase.setReportNote(note.trim());
                    }
                    caseRepository.updateCase(conn, existingCase, oldStatus);
                    resultCase = existingCase;
                } else {
                    // Librarian directly recording loss
                    LossDamageCase directCase = new LossDamageCase();
                    directCase.setCaseType(CaseType.LOST);
                    directCase.setStatusDirectly(CaseStatus.ASSESSED);
                    directCase.setBorrowTransactionId(txId);
                    directCase.setStudentId(studentId);
                    directCase.setBookId(bookId);
                    directCase.setCopyId(copyId);
                    directCase.setReportedBy(assessorId);
                    directCase.setReportedAt(new Timestamp(System.currentTimeMillis()));
                    directCase.setReportNote(note);
                    directCase.assessLost(assessorId, breakdown);
                    resultCase = caseRepository.insertCase(conn, directCase);
                }

                // 9. Audit event
                CaseEvent event = new CaseEvent(
                        resultCase.getCaseId(),
                        "CASE_ASSESSED",
                        CaseStatus.REPORTED.name(),
                        CaseStatus.ASSESSED.name(),
                        assessorId,
                        assessorRole,
                        "Lost book assessed. Price: INR " + priceToUse + ", Final Amount: INR " + breakdown.getComputedAmount()
                );
                caseRepository.recordEvent(conn, event);

                // 10. Financial notification to student (email mandatory per D16)
                try {
                    NotificationDraft draft = NotificationDraft
                            .builder(NotificationType.CASE_ASSESSED, studentId, "CASE_ASSESSED:" + resultCase.getCaseId())
                            .entity("CASE", resultCase.getCaseId())
                            .param("bookTitle", bookTitle)
                            .param("amount", breakdown.getComputedAmount().toPlainString())
                            .build();
                    notificationRecorder.record(conn, draft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record case assessed notification", e);
                }

                conn.commit();
                return resultCase;

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
        }, "assessLost");
    }

    public LossDamageCase choosePayment(int caseId, int studentId) {
        return choosePayment(caseId, studentId, "STUDENT");
    }

    public LossDamageCase choosePayment(int caseId, int studentId, String studentRole) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // 1. Lock student user row FIRST
                String userSql = "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                    ps.setInt(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new StudentNotFoundException("Student #" + studentId + " not found.");
                    }
                }

                // 2. Lock case row
                LossDamageCase caseRecord = caseRepository.lockCaseById(conn, caseId);
                if (caseRecord == null) throw new CaseNotFoundException("Case #" + caseId + " not found.");
                if (caseRecord.getStudentId() != studentId && !"ADMIN".equalsIgnoreCase(studentRole) && !"LIBRARIAN".equalsIgnoreCase(studentRole)) {
                    throw new CaseNotFoundException("Case #" + caseId + " does not belong to student #" + studentId);
                }

                // 3. Resolve using MonetaryCompensation strategy
                PenaltyResolution.ResolutionContext context = new PenaltyResolution.ResolutionContext(studentId, studentRole);
                MonetaryCompensation strategy = new MonetaryCompensation(caseRepository, fineRepository);
                strategy.resolve(conn, caseRecord, context);

                conn.commit();
                return caseRecord;

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
        }, "choosePayment");
    }

    public LossDamageCase chooseReplacement(int caseId, int studentId) {
        return chooseReplacement(caseId, studentId, "STUDENT");
    }

    public LossDamageCase chooseReplacement(int caseId, int studentId, String studentRole) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                String userSql = "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                    ps.setInt(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new StudentNotFoundException("Student #" + studentId + " not found.");
                    }
                }

                LossDamageCase caseRecord = caseRepository.lockCaseById(conn, caseId);
                if (caseRecord == null) throw new CaseNotFoundException("Case #" + caseId + " not found.");
                if (caseRecord.getStudentId() != studentId && !"ADMIN".equalsIgnoreCase(studentRole) && !"LIBRARIAN".equalsIgnoreCase(studentRole)) {
                    throw new CaseNotFoundException("Case #" + caseId + " does not belong to student #" + studentId);
                }

                LocalDate deadline = LocalDate.now(FineConfig.getLibraryZone())
                        .plusDays(LossDamageConfig.getReplacementDeadlineDays());

                CaseStatus oldStatus = caseRecord.getStatus();
                caseRecord.chooseReplacement(deadline);
                caseRepository.updateCase(conn, caseRecord, oldStatus);

                CaseEvent event = new CaseEvent(
                        caseId,
                        "CHOSE_REPLACEMENT",
                        oldStatus.name(),
                        CaseStatus.REPLACEMENT_PENDING.name(),
                        studentId,
                        studentRole,
                        "Student selected physical book replacement. Deadline: " + deadline
                );
                caseRepository.recordEvent(conn, event);

                conn.commit();
                return caseRecord;

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
        }, "chooseReplacement");
    }

    public LossDamageCase receiveReplacement(int caseId, String edition, String note, int staffId, String staffRole) {
        return receiveReplacement(caseId, staffId, staffRole);
    }

    public LossDamageCase receiveReplacement(int caseId, int staffId, String staffRole) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                LossDamageCase caseRecord = caseRepository.lockCaseById(conn, caseId);
                if (caseRecord == null) throw new CaseNotFoundException("Case #" + caseId + " not found.");

                CaseStatus oldStatus = caseRecord.getStatus();
                caseRecord.receiveReplacement();
                caseRepository.updateCase(conn, caseRecord, oldStatus);

                CaseEvent event = new CaseEvent(
                        caseId,
                        "REPLACEMENT_RECEIVED",
                        oldStatus.name(),
                        CaseStatus.REPLACEMENT_SUBMITTED.name(),
                        staffId,
                        staffRole,
                        "Replacement copy handed in at circulation desk; submitted for verification."
                );
                caseRepository.recordEvent(conn, event);

                conn.commit();
                return caseRecord;

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
        }, "receiveReplacement");
    }

    public LossDamageCase acceptReplacement(int caseId, String scannedIsbn, String condition, String notes, int staffId, String staffRole) {
        boolean conditionOk = ("NEW".equalsIgnoreCase(condition) || "LIKE_NEW".equalsIgnoreCase(condition));
        return acceptReplacement(caseId, scannedIsbn, true, conditionOk, staffId, staffRole);
    }

    public LossDamageCase acceptReplacement(int caseId, String scannedIsbn, boolean titleConfirmed,
                                            boolean conditionOk, int staffId, String staffRole) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                LossDamageCase caseRecord = caseRepository.lockCaseById(conn, caseId);
                if (caseRecord == null) throw new CaseNotFoundException("Case #" + caseId + " not found.");

                // 1. Lock student row
                String userSql = "SELECT user_id FROM users WHERE user_id = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                    ps.setInt(1, caseRecord.getStudentId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new StudentNotFoundException("Student #" + caseRecord.getStudentId() + " not found.");
                    }
                }

                // 2. Lock book row
                String bookSql = "SELECT book_id FROM books WHERE book_id = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(bookSql)) {
                    ps.setInt(1, caseRecord.getBookId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BookNotFoundException("Book #" + caseRecord.getBookId() + " not found.");
                    }
                }

                // 3. Resolve using PhysicalReplacement strategy
                PenaltyResolution.ResolutionContext context = new PenaltyResolution.ResolutionContext(staffId, staffRole);
                context.setScannedIsbn(scannedIsbn);
                context.setTitleConfirmed(titleConfirmed);
                context.setConditionOk(conditionOk);

                PhysicalReplacement strategy = new PhysicalReplacement(caseRepository, bookRepository, allocationService);
                strategy.resolve(conn, caseRecord, context);

                conn.commit();
                return caseRecord;

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
        }, "acceptReplacement");
    }

    public LossDamageCase rejectReplacement(int caseId, String reason, int staffId, String staffRole) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                LossDamageCase caseRecord = caseRepository.lockCaseById(conn, caseId);
                if (caseRecord == null) throw new CaseNotFoundException("Case #" + caseId + " not found.");

                CaseStatus oldStatus = caseRecord.getStatus();
                caseRecord.rejectReplacement(reason);
                caseRepository.updateCase(conn, caseRecord, oldStatus);

                CaseEvent event = new CaseEvent(
                        caseId,
                        "REPLACEMENT_REJECTED",
                        oldStatus.name(),
                        CaseStatus.ASSESSED.name(),
                        staffId,
                        staffRole,
                        "Replacement copy rejected. Reason: " + reason
                );
                caseRepository.recordEvent(conn, event);

                try {
                    NotificationDraft draft = NotificationDraft
                            .builder(NotificationType.REPLACEMENT_REJECTED, caseRecord.getStudentId(), "REP_REJECTED:" + caseId)
                            .entity("CASE", caseId)
                            .param("reason", reason)
                            .build();
                    notificationRecorder.record(conn, draft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record replacement rejected notification", e);
                }

                conn.commit();
                return caseRecord;

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
        }, "rejectReplacement");
    }

    public LossDamageCase convertToPayment(int caseId, String reason, int staffId, String staffRole) {
        return choosePayment(caseId, staffId, staffRole);
    }

    public LossDamageCase convertToPayment(int caseId, int staffId, String staffRole) {
        return choosePayment(caseId, staffId, staffRole);
    }

    public LossDamageCase cancelReported(int caseId, String reason, int actorId, String actorRole) {
        return cancelReported(caseId, actorId, actorRole, reason);
    }

    public LossDamageCase cancelReported(int caseId, int actorId, String actorRole, String reason) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                LossDamageCase caseRecord = caseRepository.lockCaseById(conn, caseId);
                if (caseRecord == null) throw new CaseNotFoundException("Case #" + caseId + " not found.");

                CaseStatus oldStatus = caseRecord.getStatus();
                caseRecord.cancel(reason);
                caseRepository.updateCase(conn, caseRecord, oldStatus);

                CaseEvent event = new CaseEvent(
                        caseId,
                        "CASE_CANCELLED",
                        oldStatus.name(),
                        CaseStatus.CANCELLED.name(),
                        actorId,
                        actorRole,
                        "Reported case cancelled. Reason: " + reason
                );
                caseRepository.recordEvent(conn, event);

                try {
                    NotificationDraft draft = NotificationDraft
                            .builder(NotificationType.CASE_CANCELLED, caseRecord.getStudentId(), "CASE_CANCELLED:" + caseId)
                            .entity("CASE", caseId)
                            .param("reason", reason)
                            .build();
                    notificationRecorder.record(conn, draft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record case cancelled notification", e);
                }

                conn.commit();
                return caseRecord;

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
        }, "cancelReported");
    }

    public LossDamageCase overrideAmount(int caseId, BigDecimal newAmount, String reason, int adminId, String adminRole) {
        if (!"ADMIN".equalsIgnoreCase(adminRole)) {
            throw new IllegalArgumentException("Only Administrators are permitted to override compensation amounts.");
        }
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                LossDamageCase caseRecord = caseRepository.lockCaseById(conn, caseId);
                if (caseRecord == null) throw new CaseNotFoundException("Case #" + caseId + " not found.");

                CaseStatus oldStatus = caseRecord.getStatus();
                BigDecimal oldAmount = caseRecord.getFinalAmount();
                caseRecord.overrideAmount(adminId, newAmount, reason);

                // If a fine row was already created, update fine original and outstanding amount if unpartially paid
                if (caseRecord.getFineId() != null) {
                    FineRecord fine = fineRepository.lockFineById(conn, caseRecord.getFineId());
                    if (fine != null) {
                        if (fine.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                            throw new InvalidFineOperationException("Cannot override amount: payments have already been recorded for this fine.");
                        }
                        fine.setOriginalAmount(newAmount);
                        fine.setOutstandingAmount(newAmount.subtract(fine.getWaivedAmount()));
                        fineRepository.updateFineAmountsAndStatus(conn, fine.getFineId(),
                                fine.getPaidAmount(), fine.getWaivedAmount(), fine.getOutstandingAmount(), fine.getStatus());
                    }
                }

                caseRepository.updateCase(conn, caseRecord, oldStatus);

                CaseEvent event = new CaseEvent(
                        caseId,
                        "AMOUNT_OVERRIDDEN",
                        oldStatus.name(),
                        caseRecord.getStatus().name(),
                        adminId,
                        adminRole,
                        "Compensation amount overridden from INR " + oldAmount + " to INR " + newAmount + ". Reason: " + reason
                );
                caseRepository.recordEvent(conn, event);

                conn.commit();
                return caseRecord;

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
        }, "overrideAmount");
    }

    /**
     * Case settlement hook called on the same transaction connection when a COMPENSATION fine is settled in full.
     */
    public void handleFineSettled(Connection conn, int fineId, BigDecimal paidAmount, BigDecimal waivedAmount) throws SQLException {
        String findCaseSql = "SELECT case_id FROM loss_damage_cases WHERE fine_id = ? FOR UPDATE";
        int caseId = 0;
        try (PreparedStatement ps = conn.prepareStatement(findCaseSql)) {
            ps.setInt(1, fineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    caseId = rs.getInt("case_id");
                }
            }
        }
        if (caseId == 0) return;

        LossDamageCase caseRecord = caseRepository.lockCaseById(conn, caseId);
        if (caseRecord == null) return;

        CaseStatus oldStatus = caseRecord.getStatus();
        if (oldStatus == CaseStatus.RESOLVED_PAID || oldStatus == CaseStatus.WAIVED) {
            return; // Already terminal
        }

        if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            caseRecord.markResolvedPaid(0);
        } else if (waivedAmount != null && waivedAmount.compareTo(BigDecimal.ZERO) > 0) {
            caseRecord.markWaived(0);
        } else {
            caseRecord.markResolvedPaid(0);
        }

        caseRepository.updateCase(conn, caseRecord, oldStatus);

        CaseEvent event = new CaseEvent(
                caseId,
                caseRecord.getStatus() == CaseStatus.WAIVED ? "CASE_WAIVED" : "CASE_RESOLVED_PAID",
                oldStatus.name(),
                caseRecord.getStatus().name(),
                0,
                "SYSTEM",
                "Case resolved via full fine settlement (Paid: INR " + paidAmount + ", Waived: INR " + waivedAmount + ")."
        );
        caseRepository.recordEvent(conn, event);

        try {
            NotificationDraft draft = NotificationDraft
                    .builder(NotificationType.CASE_RESOLVED, caseRecord.getStudentId(), "CASE_RESOLVED:" + caseId)
                    .entity("CASE", caseId)
                    .param("resolution", caseRecord.getStatus().name())
                    .build();
            notificationRecorder.record(conn, draft);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
            LOGGER.log(Level.WARNING, "Failed to record case resolved notification", e);
        }
    }

    public LossDamageCase getCaseById(int caseId) {
        return caseRepository.findCaseById(caseId);
    }

    public List<LossDamageCase> getMyCases(int studentId) {
        return caseRepository.findCasesByStudentId(studentId);
    }

    public List<CaseEvent> getCaseEvents(int caseId) {
        return caseRepository.findEventsByCaseId(caseId);
    }

    public PagedResult<LossDamageCase> getCases(String status, String type, String registerNumber,
                                               LocalDate fromDate, LocalDate toDate, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<LossDamageCase> list = caseRepository.findCases(status, type, registerNumber, fromDate, toDate, pageSize, offset);
        int total = caseRepository.countCases(status, type, registerNumber, fromDate, toDate);
        return new PagedResult<>(list, total, page, pageSize);
    }
}
