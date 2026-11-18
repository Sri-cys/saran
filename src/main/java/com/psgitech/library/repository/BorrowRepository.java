package com.psgitech.library.repository;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.CaseStatus;
import com.psgitech.library.enums.CaseType;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.BookInterchange;
import com.psgitech.library.model.AllocationRecord;
import com.psgitech.library.model.AllocationResult;
import com.psgitech.library.model.CaseEvent;
import com.psgitech.library.model.CompensationBreakdown;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.model.LoanRenewalRecord;
import com.psgitech.library.model.LossDamageCase;
import com.psgitech.library.service.AllocationService;
import com.psgitech.library.service.CompensationCalculator;
import com.psgitech.library.service.FineService;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.TransactionRetryHelper;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object / Repository for Book Borrowing transactions.
 * Enforces JDBC transaction isolation, pessimistic locking (SELECT ... FOR UPDATE),
 * consistent lock ordering (student then copy), and atomic status synchronization.
 */
public class BorrowRepository {
    private static final Logger LOGGER = Logger.getLogger(BorrowRepository.class.getName());

    /**
     * Executes an atomic, concurrency-safe Book Issuance transaction.
     * Enforces pessimistic locking on student and copy rows, borrowing quota,
     * and the same-book restriction inside the transactional boundary.
     */
    public BorrowTransaction issueBookTransaction(int studentId, int bookId, int copyId,
                                                 LocalDate issueDate, LocalDate dueDate,
                                                 int issuedBy) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();

                // 1. Condition 5: Plain-read copy to learn book_id and copy_code
                String plainCopySql = "SELECT copy_id, copy_code, book_id, status FROM book_copies WHERE copy_id = ?";
                int detectedBookId = -1;
                String detectedCopyCode = "";
                String detectedStatus = "";
                try (PreparedStatement psPlain = conn.prepareStatement(plainCopySql)) {
                    psPlain.setInt(1, copyId);
                    try (ResultSet rs = psPlain.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookCopyUnavailableException("Physical copy with ID " + copyId + " was not found.");
                        }
                        detectedBookId = rs.getInt("book_id");
                        detectedCopyCode = rs.getString("copy_code");
                        detectedStatus = rs.getString("status");
                    }
                }

                if (detectedBookId != bookId) {
                    throw new InvalidBorrowingOperationException("Physical copy " + detectedCopyCode + " does not belong to book ID " + bookId + ".");
                }

                conn.setAutoCommit(false);

                // 2. Canonical lock sequence: users -> books -> book_copies
                // 2a. Lock student row in users
                String studentLockSql = "SELECT user_id, name, role, active, register_number FROM users WHERE user_id = ? FOR UPDATE";
                try (PreparedStatement psStudent = conn.prepareStatement(studentLockSql)) {
                    psStudent.setInt(1, studentId);
                    try (ResultSet rs = psStudent.executeQuery()) {
                        if (!rs.next()) {
                            throw new StudentNotFoundException("Student with ID " + studentId + " does not exist.");
                        }
                        boolean active = rs.getBoolean("active");
                        String role = rs.getString("role");
                        if (!active) {
                            throw new StudentNotEligibleException("Student account is inactive or suspended. Cannot issue books.");
                        }
                        if (!"STUDENT".equalsIgnoreCase(role)) {
                            throw new StudentNotEligibleException("User is not a student (Role: " + role + "). Only students may borrow books.");
                        }
                    }
                }

                // 2b. Condition 6: Check outstanding fines
                if (BorrowingPolicy.hasOutstandingFines(studentId, conn)) {
                    throw new StudentNotEligibleException("Student has outstanding unpaid library fines. Clear dues before borrowing.");
                }
                if (BorrowingPolicy.hasUnresolvedCase(studentId, conn)) {
                    throw new StudentNotEligibleException("Student has an unresolved lost or damaged book case. Clear obligations before borrowing.");
                }

                // 2c. Lock book row in books
                String bookLockSql = "SELECT book_id, title, is_archived FROM books WHERE book_id = ? FOR UPDATE";
                String bookTitle = "";
                try (PreparedStatement psBook = conn.prepareStatement(bookLockSql)) {
                    psBook.setInt(1, bookId);
                    try (ResultSet rs = psBook.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookNotFoundException("Book with ID " + bookId + " does not exist.");
                        }
                        if (rs.getBoolean("is_archived")) {
                            throw new InvalidBorrowingOperationException("Cannot borrow copies from an archived book title.");
                        }
                        bookTitle = rs.getString("title");
                    }
                }

                // 2d. Lock copy row in book_copies
                String copyLockSql = "SELECT copy_id, copy_code, book_id, status FROM book_copies WHERE copy_id = ? FOR UPDATE";
                String copyCode = "";
                String copyStatus = "";
                try (PreparedStatement psCopy = conn.prepareStatement(copyLockSql)) {
                    psCopy.setInt(1, copyId);
                    try (ResultSet rs = psCopy.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookCopyUnavailableException("Physical copy with ID " + copyId + " was not found.");
                        }
                        copyCode = rs.getString("copy_code");
                        copyStatus = rs.getString("status");
                    }
                }

                // 3. Verify physical copy is AVAILABLE or ALLOCATED to this student
                boolean isAllocatedCollection = CopyStatus.ALLOCATED.name().equalsIgnoreCase(copyStatus);
                if (!CopyStatus.AVAILABLE.name().equalsIgnoreCase(copyStatus) && !isAllocatedCollection) {
                    throw new BookCopyUnavailableException("Physical copy " + copyCode + " is currently " + copyStatus + " and cannot be issued.");
                }

                // 4. Verify no ACTIVE transaction exists for this physical copy
                String activeCopyCheckSql = "SELECT transaction_id FROM borrow_transactions WHERE copy_id = ? AND status = 'ACTIVE'";
                try (PreparedStatement psActiveCopy = conn.prepareStatement(activeCopyCheckSql)) {
                    psActiveCopy.setInt(1, copyId);
                    try (ResultSet rs = psActiveCopy.executeQuery()) {
                        if (rs.next()) {
                            throw new BookCopyUnavailableException("Physical copy " + copyCode + " already has an active borrowing record (Transaction #" + rs.getInt("transaction_id") + ").");
                        }
                    }
                }

                // If this is an allocated collection, validate hold and re-validate student BEFORE inserting
                AllocationRecord activeAlloc = null;
                AllocationService allocService = null;
                if (isAllocatedCollection) {
                    allocService = new AllocationService();
                    activeAlloc = allocService.validateCollection(conn, copyId, studentId);
                }

                // 5. Count student's active borrowings and check borrowing limit
                String countActiveSql = "SELECT COUNT(*) FROM borrow_transactions WHERE student_id = ? AND status = 'ACTIVE'";
                int activeCount = 0;
                try (PreparedStatement psCount = conn.prepareStatement(countActiveSql)) {
                    psCount.setInt(1, studentId);
                    try (ResultSet rs = psCount.executeQuery()) {
                        if (rs.next()) {
                            activeCount = rs.getInt(1);
                        }
                    }
                }

                int maxLimit = BorrowingPolicy.getMaxActiveBorrowingLimit();
                if (activeCount >= maxLimit) {
                    throw new BorrowingLimitExceededException("Student has reached the maximum borrowing limit (" + maxLimit + " active books). Return existing books before borrowing more.");
                }

                // 6. Enforce same-book restriction: student cannot have two active copies of the same book
                String sameBookSql = "SELECT bt.transaction_id, bc.copy_code FROM borrow_transactions bt " +
                        "JOIN book_copies bc ON bt.copy_id = bc.copy_id " +
                        "WHERE bt.student_id = ? AND bt.book_id = ? AND bt.status = 'ACTIVE'";
                try (PreparedStatement psSame = conn.prepareStatement(sameBookSql)) {
                    psSame.setInt(1, studentId);
                    psSame.setInt(2, bookId);
                    try (ResultSet rs = psSame.executeQuery()) {
                        if (rs.next()) {
                            String existingCopy = rs.getString("copy_code");
                            throw new InvalidBorrowingOperationException("Student already has an active copy of this textbook (" + existingCopy + "). Students cannot borrow multiple copies of the same book simultaneously.");
                        }
                    }
                }

                // Feature 12: Dynamic Loan Policy resolution inside transaction under books lock
                com.psgitech.library.policy.LoanPolicy loanPolicy = com.psgitech.library.policy.LoanPolicyManager.getInstance().resolveForIssue(conn, bookId, studentId);
                String policyCode = loanPolicy.getCode();
                int loanDays = loanPolicy.getLoanDays();
                LocalDate effectiveDueDate = (loanPolicy.isHighDemand() || dueDate == null) ? issueDate.plusDays(loanDays) : dueDate;

                // 7. Insert the borrowing transaction
                String insertSql = "INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by, policy_code, loan_days) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)";
                int generatedId;
                try (PreparedStatement psInsert = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    psInsert.setInt(1, studentId);
                    psInsert.setInt(2, bookId);
                    psInsert.setInt(3, copyId);
                    psInsert.setDate(4, Date.valueOf(issueDate));
                    psInsert.setDate(5, Date.valueOf(effectiveDueDate));
                    psInsert.setInt(6, issuedBy);
                    psInsert.setString(7, policyCode);
                    psInsert.setInt(8, loanDays);

                    int rows = psInsert.executeUpdate();
                    if (rows == 0) {
                        throw new SQLException("Failed to insert borrowing transaction record.");
                    }

                    try (ResultSet gk = psInsert.getGeneratedKeys()) {
                        if (gk.next()) {
                            generatedId = gk.getInt(1);
                        } else {
                            throw new SQLException("Failed to obtain generated transaction ID.");
                        }
                    }
                }

                // 8. Update copy status to BORROWED with conditional guard
                if (isAllocatedCollection) {
                    // Desk collection of allocated copy: mark COLLECTED and FULFILLED
                    allocService.completeCollection(conn, activeAlloc.getAllocationId(), activeAlloc.getReservationId(), generatedId);

                    String updateCopySql = "UPDATE book_copies SET status = 'BORROWED', updated_at = CURRENT_TIMESTAMP WHERE copy_id = ? AND status = 'ALLOCATED'";
                    try (PreparedStatement psUpdateCopy = conn.prepareStatement(updateCopySql)) {
                        psUpdateCopy.setInt(1, copyId);
                        int updatedRows = psUpdateCopy.executeUpdate();
                        if (updatedRows == 0) {
                            throw new BookCopyUnavailableException("Physical copy status could not be changed to BORROWED from ALLOCATED.");
                        }
                    }
                } else {
                    // Regular available copy issue
                    String updateCopySql = "UPDATE book_copies SET status = 'BORROWED', updated_at = CURRENT_TIMESTAMP WHERE copy_id = ? AND status = 'AVAILABLE'";
                    try (PreparedStatement psUpdateCopy = conn.prepareStatement(updateCopySql)) {
                        psUpdateCopy.setInt(1, copyId);
                        int updatedRows = psUpdateCopy.executeUpdate();
                        if (updatedRows == 0) {
                            throw new BookCopyUnavailableException("Physical copy status could not be changed to BORROWED. It may have been modified concurrently.");
                        }
                    }
                }

                // Record Feature 11: LOAN_ISSUED
                try {
                    com.psgitech.library.model.NotificationDraft notifDraft = com.psgitech.library.model.NotificationDraft
                            .builder(com.psgitech.library.enums.NotificationType.LOAN_ISSUED, studentId, "LOAN_ISSUED:" + generatedId)
                            .entity("BORROW_TRANSACTION", generatedId)
                            .param("bookTitle", bookTitle)
                            .param("dueDate", effectiveDueDate != null ? effectiveDueDate.toString() : "")
                            .param("loanDays", String.valueOf(loanDays))
                            .param("isHighDemand", String.valueOf(loanPolicy.isHighDemand()))
                            .param("policyLabel", loanPolicy.getLabel())
                            .build();
                    new com.psgitech.library.service.NotificationRecorder().record(conn, notifDraft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record loan issued notification", e);
                }

                // Commit the complete transaction
                conn.commit();

                BorrowTransaction tx = new BorrowTransaction(studentId, bookId, copyId, issueDate, effectiveDueDate, issuedBy);
                tx.setTransactionId(generatedId);
                tx.setPolicyCode(policyCode);
                tx.setLoanDays(loanDays);
                tx.setStatus(BorrowStatus.ACTIVE);
                tx.setBookTitle(bookTitle);
                tx.setCopyCode(copyCode);
                tx.computeDynamicMetrics(LocalDate.now());

                return tx;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } catch (RuntimeException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "issueBookTransaction");
    }

    /**
     * Executes an atomic, concurrency-safe Book Return transaction.
     * Follows canonical lock sequence: users -> books -> book_copies -> borrow_transactions -> reservations -> allocations -> fines -> book_interchanges.
     * Enforces stale-borrower verification and retry loop if ownership transferred concurrently via interchange.
     * Atomically executes automatic allocation and fine assessment within the same transaction.
     */
    public BorrowTransaction returnBookTransaction(int transactionId, int returnedBy, LocalDate returnDate) {
        return returnBookTransaction(transactionId, returnedBy, returnDate, "GOOD", null, null, null, null);
    }

    /**
     * Executes an atomic, concurrency-safe Book Return transaction with optional damage assessment.
     * Follows canonical lock sequence: users -> books -> book_copies -> borrow_transactions -> reservations -> allocations -> fines -> case -> book_interchanges.
     * Enforces stale-borrower verification and retry loop if ownership transferred concurrently via interchange.
     * Atomically executes automatic allocation and fine assessment within the same transaction.
     */
    public BorrowTransaction returnBookTransaction(int transactionId, int returnedBy, LocalDate returnDate,
                                                   String condition, com.psgitech.library.enums.DamageSeverity severity,
                                                   com.psgitech.library.enums.CopyDisposition disposition,
                                                   BigDecimal assessedValue, String note) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            int maxBorrowerRetries = 3;
            for (int attempt = 1; attempt <= maxBorrowerRetries; attempt++) {
                Connection conn = null;
                boolean originalAutoCommit = true;

                try {
                    conn = DBConnection.getConnection();
                    originalAutoCommit = conn.getAutoCommit();
                    conn.setAutoCommit(false);

                    // 1. Plain-read transaction to learn candidate student_id, book_id, copy_id
                    String plainTxSql = "SELECT transaction_id, student_id, book_id, copy_id, issue_date, due_date, status " +
                            "FROM borrow_transactions WHERE transaction_id = ?";
                    int copyId;
                    int candidateStudentId;
                    int bookId;
                    LocalDate issueDate;
                    LocalDate dueDate;

                    try (PreparedStatement psTx = conn.prepareStatement(plainTxSql)) {
                        psTx.setInt(1, transactionId);
                        try (ResultSet rs = psTx.executeQuery()) {
                            if (!rs.next()) {
                                throw new BorrowTransactionNotFoundException("Borrowing transaction #" + transactionId + " does not exist.");
                            }
                            String currentStatus = rs.getString("status");
                            if (BorrowStatus.RETURNED.name().equalsIgnoreCase(currentStatus)) {
                                throw new InvalidBorrowingOperationException("This book copy has already been returned. Duplicate return recording is prohibited.");
                            }
                            if (BorrowStatus.LOST.name().equalsIgnoreCase(currentStatus)) {
                                throw new InvalidBorrowingOperationException("Transaction #" + transactionId + " was closed as LOST. Cannot process standard return for a lost book.");
                            }
                            if (!BorrowStatus.ACTIVE.name().equalsIgnoreCase(currentStatus)) {
                                throw new InvalidBorrowingOperationException("Transaction #" + transactionId + " is not in ACTIVE status (" + currentStatus + ").");
                            }

                            candidateStudentId = rs.getInt("student_id");
                            bookId = rs.getInt("book_id");
                            copyId = rs.getInt("copy_id");
                            issueDate = rs.getDate("issue_date").toLocalDate();
                            dueDate = rs.getDate("due_date").toLocalDate();
                        }
                    }

                    // 2. Canonical lock sequence: users -> books -> book_copies -> borrow_transactions
                    // 2a. Level 1: Lock candidate borrower row in users FIRST
                    String userLockSql = "SELECT user_id, active FROM users WHERE user_id = ? FOR UPDATE";
                    try (PreparedStatement psUser = conn.prepareStatement(userLockSql)) {
                        psUser.setInt(1, candidateStudentId);
                        try (ResultSet rs = psUser.executeQuery()) {
                            if (!rs.next()) {
                                throw new StudentNotFoundException("Student #" + candidateStudentId + " not found.");
                            }
                        }
                    }

                    // 2b. Level 2: Lock parent book row in books SECOND
                    String bookLockSql = "SELECT book_id, title, replacement_price, created_at FROM books WHERE book_id = ? FOR UPDATE";
                    String bookTitle = "";
                    BigDecimal bookReplacementPrice = null;
                    LocalDate bookCreatedAt = null;
                    try (PreparedStatement psBook = conn.prepareStatement(bookLockSql)) {
                        psBook.setInt(1, bookId);
                        try (ResultSet rs = psBook.executeQuery()) {
                            if (!rs.next()) {
                                throw new BookNotFoundException("Book #" + bookId + " not found.");
                            }
                            bookTitle = rs.getString("title");
                            bookReplacementPrice = rs.getBigDecimal("replacement_price");
                            Timestamp ts = rs.getTimestamp("created_at");
                            if (ts != null) bookCreatedAt = ts.toLocalDateTime().toLocalDate();
                        }
                    }

                    // 2c. Level 3: Lock physical copy row in book_copies THIRD
                    String copyLockSql = "SELECT copy_id, copy_code, status, created_at FROM book_copies WHERE copy_id = ? FOR UPDATE";
                    String copyCode = "";
                    LocalDate copyCreatedAt = null;
                    try (PreparedStatement psCopy = conn.prepareStatement(copyLockSql)) {
                        psCopy.setInt(1, copyId);
                        try (ResultSet rs = psCopy.executeQuery()) {
                            if (!rs.next()) {
                                throw new BookCopyUnavailableException("Associated physical copy #" + copyId + " not found.");
                            }
                            String copyStatus = rs.getString("status");
                            copyCode = rs.getString("copy_code");
                            Timestamp ts = rs.getTimestamp("created_at");
                            if (ts != null) copyCreatedAt = ts.toLocalDateTime().toLocalDate();
                            if (!CopyStatus.BORROWED.name().equalsIgnoreCase(copyStatus)) {
                                LOGGER.warning("Copy " + copyCode + " had unexpected status '" + copyStatus + "' during return.");
                            }
                        }
                    }

                    // 2d. Level 4: Lock borrow_transactions row FOURTH
                    String txLockSql = "SELECT transaction_id, student_id, status FROM borrow_transactions WHERE transaction_id = ? FOR UPDATE";
                    int lockedStudentId;
                    try (PreparedStatement psTx = conn.prepareStatement(txLockSql)) {
                        psTx.setInt(1, transactionId);
                        try (ResultSet rs = psTx.executeQuery()) {
                            if (!rs.next()) {
                                throw new BorrowTransactionNotFoundException("Borrowing transaction #" + transactionId + " does not exist.");
                            }
                            String currentStatus = rs.getString("status");
                            if (BorrowStatus.LOST.name().equalsIgnoreCase(currentStatus)) {
                                throw new InvalidBorrowingOperationException("Transaction #" + transactionId + " was closed as LOST. Cannot process standard return for a lost book.");
                            }
                            if (!BorrowStatus.ACTIVE.name().equalsIgnoreCase(currentStatus)) {
                                throw new InvalidBorrowingOperationException("Transaction #" + transactionId + " is not ACTIVE (" + currentStatus + ").");
                            }
                            lockedStudentId = rs.getInt("student_id");
                        }
                    }

                    // 3. Stale borrower verification: Did ownership transfer concurrently via interchange?
                    if (lockedStudentId != candidateStudentId) {
                        LOGGER.warning("Concurrent interchange detected during return of Tx #" + transactionId +
                                ". Borrower changed from #" + candidateStudentId + " to #" + lockedStudentId +
                                ". Rolling back and retrying with current borrower.");
                        conn.rollback();
                        conn.setAutoCommit(originalAutoCommit);
                        conn.close();
                        conn = null;
                        continue; // Re-attempt with fresh transaction: next plain-read gets candidate = lockedStudentId
                    }

                    // 4. Update borrow_transactions to RETURNED
                    java.time.ZoneId libraryZone = com.psgitech.library.config.FineConfig.getLibraryZone();
                    LocalDate effectiveReturnDate = (returnDate != null) ? returnDate : LocalDate.now(libraryZone);
                    String updateTxSql = "UPDATE borrow_transactions SET status = 'RETURNED', return_date = ?, returned_by = ?, updated_at = CURRENT_TIMESTAMP " +
                            "WHERE transaction_id = ? AND student_id = ? AND status = 'ACTIVE'";
                    try (PreparedStatement psUpdateTx = conn.prepareStatement(updateTxSql)) {
                        psUpdateTx.setDate(1, Date.valueOf(effectiveReturnDate));
                        psUpdateTx.setInt(2, returnedBy);
                        psUpdateTx.setInt(3, transactionId);
                        psUpdateTx.setInt(4, lockedStudentId);

                        int affected = psUpdateTx.executeUpdate();
                        if (affected == 0) {
                            throw new InvalidBorrowingOperationException("Failed to update transaction status. It may have been completed concurrently.");
                        }
                    }

                    // Feature 14 Decision D14: Auto-cancel open REPORTED case if book is returned (Found)
                    LossDamageCaseRepository caseRepo = new LossDamageCaseRepository();
                    int cancelledCases = caseRepo.cancelReportedCaseForTransaction(conn, transactionId, returnedBy, "Auto-cancelled: Book copy returned to library desk (FOUND)");
                    if (cancelledCases > 0) {
                        LOGGER.info("Auto-cancelled " + cancelledCases + " REPORTED case(s) for returned transaction #" + transactionId);
                        try {
                            com.psgitech.library.model.NotificationDraft foundDraft = com.psgitech.library.model.NotificationDraft
                                    .builder(com.psgitech.library.enums.NotificationType.CASE_CANCELLED, lockedStudentId, "CASE_FOUND:" + transactionId)
                                    .entity("BORROW_TRANSACTION", transactionId)
                                    .param("reason", "Book returned to circulation desk (Found)")
                                    .build();
                            new com.psgitech.library.service.NotificationRecorder().record(conn, foundDraft);
                        } catch (SQLException e) {
                            if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                            LOGGER.log(Level.WARNING, "Failed to record case cancelled notification on found return", e);
                        }
                    }

                    // 5. Handling copy status and allocation (Normal GOOD return vs DAMAGED return)
                    boolean isDamaged = "DAMAGED".equalsIgnoreCase(condition);
                    AllocationResult allocOutcome = null;
                    LossDamageCase damagedCaseResult = null;

                    if (!isDamaged) {
                        // Feature 5: Execute automatic allocation inside the return transaction (Levels 5 & 6)
                        AllocationService allocService = new AllocationService();
                        allocOutcome = allocService.allocateReturnedCopy(conn, copyId, bookId, transactionId);
                    } else {
                        // Feature 14: Damaged Book at return desk (Decisions D2, D3, D6, D10)
                        if (severity == null) {
                            throw new IllegalArgumentException("Damage severity (MINOR, MAJOR, UNUSABLE) is required when book is returned DAMAGED.");
                        }
                        com.psgitech.library.enums.CopyDisposition disp = (disposition != null) ? disposition : com.psgitech.library.enums.CopyDisposition.WITHDRAWN;
                        if (disp == com.psgitech.library.enums.CopyDisposition.RETURNED_TO_CIRCULATION) {
                            if (severity != com.psgitech.library.enums.DamageSeverity.MINOR) {
                                throw new IllegalArgumentException("Only MINOR damage may be returned to circulation; major/unusable damage must be WITHDRAWN.");
                            }
                            AllocationService allocService = new AllocationService();
                            allocOutcome = allocService.allocateReturnedCopy(conn, copyId, bookId, transactionId);
                        } else {
                            // WITHDRAWN: copy transitions BORROWED -> ARCHIVED, no allocation
                            String retireCopySql = "UPDATE book_copies SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP WHERE copy_id = ? AND status = 'BORROWED'";
                            try (PreparedStatement psRetire = conn.prepareStatement(retireCopySql)) {
                                psRetire.setInt(1, copyId);
                                psRetire.executeUpdate();
                            }
                        }

                        // Determine replacement price
                        BigDecimal priceToUse = bookReplacementPrice;
                        if (priceToUse == null || priceToUse.compareTo(BigDecimal.ZERO) <= 0) {
                            if (assessedValue == null || assessedValue.compareTo(BigDecimal.ZERO) <= 0) {
                                throw new MissingReplacementPriceException("Catalog book has no replacement price. Assessed replacement value must be provided.");
                            }
                            priceToUse = assessedValue;
                        }

                        LocalDate acquiredDate = (copyCreatedAt != null) ? copyCreatedAt : bookCreatedAt;
                        CompensationCalculator calc = new CompensationCalculator();
                        CompensationBreakdown breakdown = calc.calculate(CaseType.DAMAGED, severity, priceToUse, acquiredDate);

                        // Create case in ASSESSED status
                        LossDamageCase damagedCase = LossDamageCase.createDamagedAssessment(
                                transactionId, lockedStudentId, bookId, copyId, returnedBy, severity, disp, breakdown, note);
                        damagedCaseResult = caseRepo.insertCase(conn, damagedCase);

                        CaseEvent caseEvent = new CaseEvent(
                                damagedCaseResult.getCaseId(),
                                "CASE_ASSESSED",
                                null,
                                CaseStatus.ASSESSED.name(),
                                returnedBy,
                                "STAFF",
                                "Damaged book assessed at return. Severity: " + severity + ", Disposition: " + disp + ", Amount: INR " + breakdown.getComputedAmount()
                        );
                        caseRepo.recordEvent(conn, caseEvent);

                        try {
                            com.psgitech.library.model.NotificationDraft dmgDraft = com.psgitech.library.model.NotificationDraft
                                    .builder(com.psgitech.library.enums.NotificationType.CASE_ASSESSED, lockedStudentId, "CASE_ASSESSED:" + damagedCaseResult.getCaseId())
                                    .entity("CASE", damagedCaseResult.getCaseId())
                                    .param("bookTitle", bookTitle)
                                    .param("amount", breakdown.getComputedAmount().toPlainString())
                                    .build();
                            new com.psgitech.library.service.NotificationRecorder().record(conn, dmgDraft);
                        } catch (SQLException e) {
                            if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                            LOGGER.log(Level.WARNING, "Failed to record damaged case notification", e);
                        }
                    }

                    // 6. Feature 6: Calculate and record fine if overdue for verified borrower (Level 7)
                    FineService fineService = new FineService();
                    FineRecord fineRecord = fineService.createFineForReturn(conn, transactionId, lockedStudentId, bookId, copyId, dueDate, effectiveReturnDate);

                    // 7. Feature 8: Cancel any pending REQUESTED interchange record for this transaction (Level 11)
                    BookInterchangeRepository interchangeRepo = new BookInterchangeRepository();
                    // Check if there is an active pending interchange before cancelling
                    BookInterchange pendingInterchange = interchangeRepo.findActiveByTransactionId(transactionId);
                    int cancelledInterchanges = interchangeRepo.cancelPendingForTransaction(conn, transactionId, returnedBy, "Auto-cancelled: Book copy returned to library desk");

                    // Record Feature 11: LOAN_RETURNED
                    try {
                        com.psgitech.library.service.NotificationRecorder recorder = new com.psgitech.library.service.NotificationRecorder();
                        com.psgitech.library.model.NotificationDraft returnDraft = com.psgitech.library.model.NotificationDraft
                                .builder(com.psgitech.library.enums.NotificationType.LOAN_RETURNED, lockedStudentId, "LOAN_RETURNED:" + transactionId)
                                .entity("BORROW_TRANSACTION", transactionId)
                                .param("bookTitle", bookTitle)
                                .build();
                        recorder.record(conn, returnDraft);

                        // If pending interchange was cancelled, notify target student
                        if (cancelledInterchanges > 0 && pendingInterchange != null) {
                            com.psgitech.library.model.NotificationDraft invDraft = com.psgitech.library.model.NotificationDraft
                                    .builder(com.psgitech.library.enums.NotificationType.INTERCHANGE_INVALIDATED, pendingInterchange.getToStudentId(), "INTERCHANGE_INVALIDATED:" + pendingInterchange.getInterchangeId())
                                    .entity("INTERCHANGE", pendingInterchange.getInterchangeId())
                                    .param("bookTitle", bookTitle)
                                    .build();
                            recorder.record(conn, invDraft);
                        }
                    } catch (SQLException e) {
                        if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                        LOGGER.log(Level.WARNING, "Failed to record loan return notification", e);
                    }

                    // Commit the complete transaction
                    conn.commit();

                    BorrowTransaction tx = new BorrowTransaction(transactionId, lockedStudentId, bookId, copyId,
                            issueDate, dueDate, effectiveReturnDate, BorrowStatus.RETURNED, 0, returnedBy);
                    tx.setCopyCode(copyCode);
                    tx.setBookTitle(bookTitle);
                    tx.setAllocationResult(allocOutcome);
                    tx.setFineRecord(fineRecord);
                    tx.setLossDamageCase(damagedCaseResult);
                    tx.computeDynamicMetrics(effectiveReturnDate);

                    return tx;

                } catch (SQLException e) {
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                    }
                    throw e;
                } catch (RuntimeException e) {
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                    }
                    throw e;
                } finally {
                    if (conn != null) {
                        try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                    }
                }
            }
            throw new InvalidBorrowingOperationException("Failed to return book after repeated concurrent ownership transfers.");
        }, "returnBookTransaction");
    }


    /**
     * Finds a transaction by primary ID with full joined metadata.
     */
    public BorrowTransaction findById(int transactionId) {
        String sql = getBaseJoinedSelect() + " WHERE bt.transaction_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToTransaction(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding transaction by ID: " + transactionId, e);
            throw new RuntimeException("Database error retrieving borrowing transaction.", e);
        }
        return null;
    }

    /**
     * Finds active transaction for a specific copy ID.
     */
    public BorrowTransaction findActiveByCopyId(int copyId) {
        String sql = getBaseJoinedSelect() + " WHERE bt.copy_id = ? AND bt.status = 'ACTIVE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, copyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToTransaction(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding active transaction by copy ID: " + copyId, e);
            throw new RuntimeException("Database error finding active copy borrowing.", e);
        }
        return null;
    }

    /**
     * Finds active transaction by physical copy code (e.g. PSG-BK1-C01).
     */
    public BorrowTransaction findActiveByCopyCode(String copyCode) {
        if (copyCode == null || copyCode.trim().isEmpty()) return null;
        String sql = getBaseJoinedSelect() + " WHERE LOWER(bc.copy_code) = LOWER(?) AND bt.status = 'ACTIVE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, copyCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToTransaction(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding active transaction by copy code: " + copyCode, e);
            throw new RuntimeException("Database error finding active borrowing by copy code.", e);
        }
        return null;
    }

    /**
     * Retrieves all active transactions for a student.
     */
    public List<BorrowTransaction> findActiveByStudentId(int studentId) {
        String sql = getBaseJoinedSelect() + " WHERE bt.student_id = ? AND bt.status = 'ACTIVE' ORDER BY bt.due_date ASC";
        List<BorrowTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving active borrowings for student: " + studentId, e);
            throw new RuntimeException("Database error retrieving student active borrowings.", e);
        }
        return list;
    }

    /**
     * Retrieves all historical borrowing records (both returned, active, and transferred out) for a student.
     */
    public List<BorrowTransaction> findHistoryByStudentId(int studentId) {
        String sql = getBaseJoinedSelect() + " WHERE (bt.student_id = ? OR bi.from_student_id = ?) ORDER BY bt.created_at DESC";
        List<BorrowTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BorrowTransaction tx = mapRowToTransaction(rs);
                    int currentOwner = rs.getInt("student_id");
                    int fromStudent = rs.getInt("from_student_id");
                    if (!rs.wasNull() && fromStudent == studentId && currentOwner != studentId) {
                        tx.setTransferred(true);
                    }
                    list.add(tx);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving borrowing history for student: " + studentId, e);
            throw new RuntimeException("Database error retrieving student borrowing history.", e);
        }
        return list;
    }

    /**
     * Counts current active borrowing transactions for a student.
     */
    public int countActiveByStudentId(int studentId) {
        String sql = "SELECT COUNT(*) FROM borrow_transactions WHERE student_id = ? AND status = 'ACTIVE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting active borrowings for student: " + studentId, e);
            throw new RuntimeException("Database error counting active borrowings.", e);
        }
        return 0;
    }

    /**
     * Checks if a student currently has an active borrowing for the specified book.
     */
    public boolean hasActiveBorrowingForBook(int studentId, int bookId) {
        String sql = "SELECT 1 FROM borrow_transactions WHERE student_id = ? AND book_id = ? AND status = 'ACTIVE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking active same-book borrowing", e);
            throw new RuntimeException("Database error checking same-book borrowing.", e);
        }
    }

    /**
     * Retrieves all active borrowing transactions across the library with optional keyword search.
     */
    public List<BorrowTransaction> findAllActive(String query) {
        StringBuilder sql = new StringBuilder(getBaseJoinedSelect());
        sql.append(" WHERE bt.status = 'ACTIVE'");

        boolean hasQuery = query != null && !query.trim().isEmpty();
        if (hasQuery) {
            sql.append(" AND (LOWER(b.title) LIKE ? OR LOWER(u.name) LIKE ? OR LOWER(u.register_number) LIKE ? OR LOWER(bc.copy_code) LIKE ?)");
        }
        sql.append(" ORDER BY bt.due_date ASC");

        List<BorrowTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (hasQuery) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                ps.setString(1, pattern);
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                ps.setString(4, pattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching all active borrowings", e);
            throw new RuntimeException("Database error fetching active borrowings.", e);
        }
        return list;
    }

    /**
     * Retrieves all completed / historical transactions across the library.
     */
    public List<BorrowTransaction> findAllHistory(String query) {
        StringBuilder sql = new StringBuilder(getBaseJoinedSelect());
        boolean hasQuery = query != null && !query.trim().isEmpty();
        if (hasQuery) {
            sql.append(" WHERE (LOWER(b.title) LIKE ? OR LOWER(u.name) LIKE ? OR LOWER(u.register_number) LIKE ? OR LOWER(bc.copy_code) LIKE ?)");
        }
        sql.append(" ORDER BY bt.transaction_id DESC");

        List<BorrowTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (hasQuery) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                ps.setString(1, pattern);
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                ps.setString(4, pattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching borrowing history", e);
            throw new RuntimeException("Database error fetching borrowing history.", e);
        }
        return list;
    }

    /**
     * Retrieves all ACTIVE transactions that are currently overdue (due_date < current date).
     */
    public List<BorrowTransaction> findOverdueTransactions() {
        String sql = getBaseJoinedSelect() + " WHERE bt.status = 'ACTIVE' AND bt.due_date < CURRENT_DATE() ORDER BY bt.due_date ASC";
        List<BorrowTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToTransaction(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching overdue transactions", e);
            throw new RuntimeException("Database error fetching overdue borrowings.", e);
        }
        return list;
    }

    /**
     * Finds active transactions matching a student's register number.
     */
    public List<BorrowTransaction> findActiveByRegisterNumber(String regNo) {
        if (regNo == null || regNo.trim().isEmpty()) return new ArrayList<>();
        String sql = getBaseJoinedSelect() + " WHERE LOWER(u.register_number) = LOWER(?) AND bt.status = 'ACTIVE' ORDER BY bt.due_date ASC";
        List<BorrowTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regNo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding active transactions by regNo: " + regNo, e);
            throw new RuntimeException("Database error finding active transactions.", e);
        }
        return list;
    }

    public List<BorrowTransaction> findDueSoonTransactions() {
        String sql = getBaseJoinedSelect() + " WHERE bt.status = 'ACTIVE' AND bt.due_date BETWEEN CURRENT_DATE() AND DATE_ADD(CURRENT_DATE(), INTERVAL 3 DAY) ORDER BY bt.due_date ASC";
        List<BorrowTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToTransaction(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching due soon transactions", e);
        }
        return list;
    }

    public List<BorrowTransaction> findRecentlyIssuedTransactions(int limit) {
        String sql = getBaseJoinedSelect() + " WHERE bt.status = 'ACTIVE' ORDER BY bt.issue_date DESC, bt.transaction_id DESC LIMIT ?";
        List<BorrowTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching recently issued transactions", e);
        }
        return list;
    }

    public List<BorrowTransaction> findRecentlyReturnedTransactions(int limit) {
        String sql = getBaseJoinedSelect() + " WHERE bt.status = 'RETURNED' ORDER BY bt.return_date DESC, bt.transaction_id DESC LIMIT ?";
        List<BorrowTransaction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching recently returned transactions", e);
        }
        return list;
    }

    private String getBaseJoinedSelect() {
        return "SELECT bt.transaction_id, bt.student_id, bt.book_id, bt.copy_id, " +
                "bt.issue_date, bt.due_date, bt.return_date, bt.status, bt.issued_by, bt.returned_by, " +
                "bt.policy_code, bt.loan_days, " +
                "bt.renewal_count, bt.last_renewed_at, bt.created_at, bt.updated_at, " +
                "u.name AS student_name, u.register_number, u.department AS student_department, u.`year` AS student_year, " +
                "b.title AS book_title, b.author AS book_author, b.cover_image_url, " +
                "bc.copy_code, " +
                "bi.from_student_id, bi.to_student_id, bi.completed_at AS transfer_timestamp, " +
                "ut.name AS transfer_recipient_name, " +
                "uf.name AS original_borrower_name " +
                "FROM borrow_transactions bt " +
                "JOIN users u ON bt.student_id = u.user_id " +
                "JOIN books b ON bt.book_id = b.book_id " +
                "JOIN book_copies bc ON bt.copy_id = bc.copy_id " +
                "LEFT JOIN book_interchanges bi ON bt.transaction_id = bi.transaction_id AND bi.status = 'COMPLETED' " +
                "LEFT JOIN users ut ON bi.to_student_id = ut.user_id " +
                "LEFT JOIN users uf ON bi.from_student_id = uf.user_id";
    }

    private BorrowTransaction mapRowToTransaction(ResultSet rs) throws SQLException {
        BorrowTransaction tx = new BorrowTransaction();
        tx.setTransactionId(rs.getInt("transaction_id"));
        tx.setStudentId(rs.getInt("student_id"));
        tx.setBookId(rs.getInt("book_id"));
        tx.setCopyId(rs.getInt("copy_id"));

        Date issueSqlDate = rs.getDate("issue_date");
        if (issueSqlDate != null) tx.setIssueDate(issueSqlDate.toLocalDate());

        Date dueSqlDate = rs.getDate("due_date");
        if (dueSqlDate != null) tx.setDueDate(dueSqlDate.toLocalDate());

        Date returnSqlDate = rs.getDate("return_date");
        if (returnSqlDate != null) tx.setReturnDate(returnSqlDate.toLocalDate());

        tx.setStatus(BorrowStatus.fromString(rs.getString("status")));
        tx.setIssuedBy(rs.getInt("issued_by"));

        int returnedBy = rs.getInt("returned_by");
        if (!rs.wasNull()) tx.setReturnedBy(returnedBy);

        tx.setCreatedAt(rs.getTimestamp("created_at"));
        tx.setUpdatedAt(rs.getTimestamp("updated_at"));

        try {
            tx.setRenewalCount(rs.getInt("renewal_count"));
            tx.setLastRenewedAt(rs.getTimestamp("last_renewed_at"));
        } catch (SQLException ignored) {
            tx.setRenewalCount(0);
        }

        try {
            String pCode = rs.getString("policy_code");
            tx.setPolicyCode(pCode != null ? pCode : "STANDARD");
            int ld = rs.getInt("loan_days");
            if (!rs.wasNull()) {
                tx.setLoanDays(ld);
            } else {
                tx.setLoanDays(14);
            }
        } catch (SQLException ignored) {
            tx.setPolicyCode("STANDARD");
            tx.setLoanDays(14);
        }

        tx.setStudentName(rs.getString("student_name"));
        tx.setRegisterNumber(rs.getString("register_number"));
        tx.setStudentDepartment(rs.getString("student_department"));

        int yr = rs.getInt("student_year");
        if (!rs.wasNull()) tx.setStudentYear(yr);

        tx.setBookTitle(rs.getString("book_title"));
        tx.setBookAuthor(rs.getString("book_author"));
        tx.setCopyCode(rs.getString("copy_code"));
        tx.setCoverImageUrl(rs.getString("cover_image_url"));

        // Feature 8 Interchange metadata mapping
        try {
            int toStudent = rs.getInt("to_student_id");
            if (!rs.wasNull()) {
                String recName = rs.getString("transfer_recipient_name");
                String origName = rs.getString("original_borrower_name");
                Timestamp xferTs = rs.getTimestamp("transfer_timestamp");
                LocalDate xferDate = (xferTs != null) ? xferTs.toLocalDateTime().toLocalDate() : null;

                tx.setTransferRecipientName(recName);
                tx.setOriginalBorrowerName(origName);
                tx.setTransferDate(xferDate);

                if (toStudent == tx.getStudentId()) {
                    tx.setAcquiredViaInterchange(true);
                }
            }
        } catch (SQLException ignored) {
            // Interchange columns omitted in specialized queries
        }

        tx.computeDynamicMetrics(LocalDate.now());

        return tx;
    }

    public BorrowTransaction renewLoan(int transactionId, int studentId, int actingUserId, Role actingRole) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // 1. Snapshot query to identify keys
                int candidateStudentId;
                int bookId;
                int copyId;
                LocalDate currentDueDate;
                int currentRenewalCount;

                String snapshotSql = "SELECT student_id, book_id, copy_id, due_date, status, renewal_count " +
                                     "FROM borrow_transactions WHERE transaction_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(snapshotSql)) {
                    ps.setInt(1, transactionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new BorrowTransactionNotFoundException("Borrowing transaction #" + transactionId + " not found.");
                        }
                        String currentStatus = rs.getString("status");
                        if (!BorrowStatus.ACTIVE.name().equalsIgnoreCase(currentStatus)) {
                            throw new InvalidBorrowingOperationException("Only active book borrowings can be renewed (Current status: " + currentStatus + ").");
                        }
                        candidateStudentId = rs.getInt("student_id");
                        bookId = rs.getInt("book_id");
                        copyId = rs.getInt("copy_id");
                        Date dueSql = rs.getDate("due_date");
                        currentDueDate = (dueSql != null) ? dueSql.toLocalDate() : null;
                        currentRenewalCount = rs.getInt("renewal_count");
                    }
                }

                // Student ownership check
                if (actingRole == Role.STUDENT && candidateStudentId != studentId) {
                    throw new InvalidBorrowingOperationException("Access Denied: You may only renew your own active book loans.");
                }

                // 2. Canonical Lock Sequence: users -> books -> book_copies -> borrow_transactions
                // 2a. Lock user
                try (PreparedStatement psUser = conn.prepareStatement("SELECT user_id, active FROM users WHERE user_id = ? FOR UPDATE")) {
                    psUser.setInt(1, candidateStudentId);
                    try (ResultSet rs = psUser.executeQuery()) {
                        if (!rs.next()) {
                            throw new StudentNotFoundException("Student #" + candidateStudentId + " not found.");
                        }
                        if (!rs.getBoolean("active")) {
                            throw new AccountInactiveException("Student account is inactive.");
                        }
                    }
                }

                // 2b. Lock book
                String bookTitle = "Book Loan";
                try (PreparedStatement psBook = conn.prepareStatement("SELECT book_id, title, is_archived FROM books WHERE book_id = ? FOR UPDATE")) {
                    psBook.setInt(1, bookId);
                    try (ResultSet rs = psBook.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookNotFoundException("Book #" + bookId + " not found.");
                        }
                        if (rs.getString("title") != null) {
                            bookTitle = rs.getString("title");
                        }
                        if (rs.getBoolean("is_archived")) {
                            throw new InvalidBorrowingOperationException("Cannot renew loan for an archived catalog title.");
                        }
                    }
                }

                // 2c. Lock copy
                try (PreparedStatement psCopy = conn.prepareStatement("SELECT copy_id, status FROM book_copies WHERE copy_id = ? FOR UPDATE")) {
                    psCopy.setInt(1, copyId);
                    try (ResultSet rs = psCopy.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookCopyUnavailableException("Physical copy #" + copyId + " not found.");
                        }
                    }
                }

                // 2d. Lock transaction
                try (PreparedStatement psTx = conn.prepareStatement("SELECT transaction_id, status, due_date, renewal_count FROM borrow_transactions WHERE transaction_id = ? FOR UPDATE")) {
                    psTx.setInt(1, transactionId);
                    try (ResultSet rs = psTx.executeQuery()) {
                        if (!rs.next()) {
                            throw new BorrowTransactionNotFoundException("Transaction #" + transactionId + " not found.");
                        }
                        if (!BorrowStatus.ACTIVE.name().equalsIgnoreCase(rs.getString("status"))) {
                            throw new InvalidBorrowingOperationException("Transaction is no longer ACTIVE.");
                        }
                        Date lockedDue = rs.getDate("due_date");
                        if (lockedDue != null) currentDueDate = lockedDue.toLocalDate();
                        currentRenewalCount = rs.getInt("renewal_count");
                    }
                }

                // 3. Validation: Max renewals (Decision: 1 renewal max)
                if (currentRenewalCount >= 1) {
                    throw new InvalidBorrowingOperationException("This book loan has already been renewed the maximum allowed number of times (1).");
                }

                // 4. Validation: Cannot renew if already overdue
                if (currentDueDate != null && currentDueDate.isBefore(LocalDate.now())) {
                    throw new InvalidBorrowingOperationException("Overdue loans cannot be renewed. Please return the book at the circulation desk.");
                }

                // 5a. Validation: Cannot renew if book is under high-demand policy (Decision D4)
                com.psgitech.library.policy.LoanPolicy effectivePolicy = com.psgitech.library.policy.LoanPolicyManager.getInstance().effectivePolicy(conn, bookId);
                if (effectivePolicy != null && !effectivePolicy.canRenew()) {
                    throw new InvalidBorrowingOperationException("HIGH_DEMAND_POLICY: This book is in high demand, so renewals are not available.");
                }

                // 5b. Validation: Cannot renew if students are waiting in the reservation queue
                String resSql = "SELECT COUNT(*) FROM reservations WHERE book_id = ? AND status = 'WAITING'";
                try (PreparedStatement psRes = conn.prepareStatement(resSql)) {
                    psRes.setInt(1, bookId);
                    try (ResultSet rs = psRes.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new InvalidBorrowingOperationException("Cannot renew: There are students waiting in the reservation queue for this book.");
                        }
                    }
                }

                // 6. Validation: Cannot renew if there is a pending interchange on this copy
                String interSql = "SELECT COUNT(*) FROM book_interchanges WHERE copy_id = ? AND status = 'PENDING'";
                try (PreparedStatement psInter = conn.prepareStatement(interSql)) {
                    psInter.setInt(1, copyId);
                    try (ResultSet rs = psInter.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new InvalidBorrowingOperationException("Cannot renew: This physical copy has a pending interchange transfer request.");
                        }
                    }
                }

                // 7. Validation: Cannot renew if student has outstanding unpaid fines
                String fineSql = "SELECT COUNT(*) FROM fines WHERE student_id = ? AND status IN ('UNPAID', 'PARTIALLY_PAID') AND outstanding_amount > 0";
                try (PreparedStatement psFine = conn.prepareStatement(fineSql)) {
                    psFine.setInt(1, candidateStudentId);
                    try (ResultSet rs = psFine.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new InvalidBorrowingOperationException("Cannot renew: You have outstanding unpaid fines that must be cleared first.");
                        }
                    }
                }

                // 7b. Validation: Cannot renew if loan has an open loss/damage case or student has unresolved cases
                LossDamageCaseRepository caseRepo = new LossDamageCaseRepository();
                if (caseRepo.hasOpenCaseForTransaction(transactionId, conn)) {
                    throw new InvalidBorrowingOperationException("LOSS_CASE_OPEN: An open lost or damaged case exists for this loan; renewal is not permitted.");
                }
                if (caseRepo.hasUnresolvedCase(candidateStudentId, conn)) {
                    throw new InvalidBorrowingOperationException("Cannot renew: You have an unresolved lost or damaged book case.");
                }

                // 8. Execute Renewal Mutation
                LocalDate newDueDate = (currentDueDate != null ? currentDueDate : LocalDate.now()).plusDays(BorrowingPolicy.DEFAULT_BORROWING_DAYS);
                String updateSql = "UPDATE borrow_transactions SET due_date = ?, renewal_count = renewal_count + 1, last_renewed_at = NOW() WHERE transaction_id = ?";
                try (PreparedStatement psUpd = conn.prepareStatement(updateSql)) {
                    psUpd.setDate(1, Date.valueOf(newDueDate));
                    psUpd.setInt(2, transactionId);
                    psUpd.executeUpdate();
                }

                // Record in loan_renewals audit table
                LoanRenewalRecord renewalRec = new LoanRenewalRecord(
                        transactionId, candidateStudentId, currentDueDate, newDueDate, currentRenewalCount + 1, actingUserId
                );
                RenewalRepository renewalRepo = new RenewalRepository();
                renewalRepo.saveRenewal(conn, renewalRec);

                // Record Feature 11: LOAN_RENEWED
                try {
                    com.psgitech.library.model.NotificationDraft notifDraft = com.psgitech.library.model.NotificationDraft
                            .builder(com.psgitech.library.enums.NotificationType.LOAN_RENEWED, candidateStudentId, "LOAN_RENEWED:" + transactionId + ":" + (currentRenewalCount + 1))
                            .entity("BORROW_TRANSACTION", transactionId)
                            .param("bookTitle", bookTitle)
                            .param("dueDate", newDueDate.toString())
                            .build();
                    new com.psgitech.library.service.NotificationRecorder().record(conn, notifDraft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record loan renewal notification", e);
                }

                conn.commit();
                LOGGER.info("Successfully renewed loan #" + transactionId + " to new due date: " + newDueDate);

                return findById(transactionId);

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { LOGGER.log(Level.SEVERE, "Rollback failed", ex); }
                }
                throw e;
            } catch (Exception e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { LOGGER.log(Level.SEVERE, "Rollback failed", ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "renewLoan");
    }
}
