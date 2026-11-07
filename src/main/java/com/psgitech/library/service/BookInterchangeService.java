package com.psgitech.library.service;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.config.FineConfig;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.InterchangeStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.*;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.TransactionRetryHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service managing Student-to-Student Book Interchanges.
 * Orchestrates multi-row locking in canonical sequence:
 * users -> books -> book_copies -> borrow_transactions -> reservations -> allocations -> fines -> book_interchanges.
 */
public class BookInterchangeService {
    private static final Logger LOGGER = Logger.getLogger(BookInterchangeService.class.getName());

    private final BookInterchangeRepository interchangeRepository;
    private final BorrowRepository borrowRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ReservationRepository reservationRepository;
    private final AllocationRepository allocationRepository;

    public BookInterchangeService() {
        this.interchangeRepository = new BookInterchangeRepository();
        this.borrowRepository = new BorrowRepository();
        this.userRepository = new UserRepository();
        this.bookRepository = new BookRepository();
        this.reservationRepository = new ReservationRepository();
        this.allocationRepository = new AllocationRepository();
    }

    public BookInterchangeService(BookInterchangeRepository interchangeRepository,
                                  BorrowRepository borrowRepository,
                                  UserRepository userRepository,
                                  BookRepository bookRepository,
                                  ReservationRepository reservationRepository,
                                  AllocationRepository allocationRepository) {
        this.interchangeRepository = interchangeRepository;
        this.borrowRepository = borrowRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.reservationRepository = reservationRepository;
        this.allocationRepository = allocationRepository;
    }

    /**
     * Initiates a student-to-student book interchange.
     * Leaves the borrowing transaction with the sender until explicit recipient acceptance.
     */
    public BookInterchange initiateInterchange(int senderUserId, int transactionId,
                                              String recipientRegisterNumber, String requestNote) {
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Valid borrowing transaction ID is required.");
        }
        if (recipientRegisterNumber == null || recipientRegisterNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient student register number is required.");
        }

        // 1. Resolve and validate recipient student
        Student recipient = userRepository.findByRegisterNumber(recipientRegisterNumber.trim());
        if (recipient == null) {
            throw new StudentNotFoundException("Recipient student with register number '" + recipientRegisterNumber + "' was not found.");
        }
        if (!recipient.isActive()) {
            throw new RecipientNotEligibleException("Recipient student account is inactive or suspended.");
        }
        if (recipient.getRole() != Role.STUDENT) {
            throw new RecipientNotEligibleException("Recipient is not a student (Role: " + recipient.getRole() + "). Interchanges are only permitted between students.");
        }
        if (senderUserId == recipient.getUserId()) {
            throw new InvalidInterchangeException("Sender and recipient cannot be the same student. Self-transfers are prohibited.");
        }

        // 2. Validate sender active borrowing transaction
        BorrowTransaction tx = borrowRepository.findById(transactionId);
        if (tx == null) {
            throw new BorrowTransactionNotFoundException("Borrowing transaction #" + transactionId + " does not exist.");
        }
        if (tx.getStudentId() != senderUserId) {
            throw new InterchangeNotAllowedException("You do not hold the active borrowing for transaction #" + transactionId + ".");
        }
        if (tx.getStatus() != com.psgitech.library.enums.BorrowStatus.ACTIVE) {
            throw new InterchangeNotAllowedException("Only ACTIVE book borrowings can be transferred.");
        }

        // 3. Due-date eligibility check using configured Asia/Kolkata library timezone
        ZoneId libraryZone = FineConfig.getLibraryZone();
        LocalDate today = LocalDate.now(libraryZone);
        if (today.isAfter(tx.getDueDate())) {
            throw new InterchangeNotAllowedException("This book is overdue (due date: " + tx.getDueDate() +
                    "). Overdue books cannot be transferred to another student. Please return the book to the library desk.");
        }

        // 4. Pre-check: Check if this transaction already has a pending REQUESTED interchange
        BookInterchange existingPending = interchangeRepository.findActiveByTransactionId(transactionId);
        if (existingPending != null) {
            // Idempotent return if identical request
            if (existingPending.getToStudentId() == recipient.getUserId()) {
                return existingPending;
            }
            throw new InvalidInterchangeException("A pending interchange request already exists for this borrowed book (Request #" +
                    existingPending.getInterchangeId() + " to " + existingPending.getToStudentName() + "). Cancel it before creating a new transfer.");
        }

        // 5. Pre-check: Reservation queue priority (Feature 4 & 5)
        int waitingReservations = countWaitingAndAllocatedReservations(tx.getBookId());
        if (waitingReservations > 0) {
            throw new ActiveReservationQueueException("Direct interchange is unavailable. There are currently " +
                    waitingReservations + " student(s) waiting in the official reservation queue for this title.");
        }

        // 6. Pre-check: Recipient borrowing capacity and same-book restriction
        if (borrowRepository.hasActiveBorrowingForBook(recipient.getUserId(), tx.getBookId())) {
            throw new RecipientNotEligibleException("Recipient student already holds an active copy of this book.");
        }
        if (BorrowingPolicy.hasOutstandingFines(recipient.getUserId())) {
            throw new RecipientNotEligibleException("Recipient student has unpaid library fines. Fines must be cleared before receiving books.");
        }
        int activeLoans = borrowRepository.countActiveByStudentId(recipient.getUserId());
        int activeAllocations = countActiveAllocationsForStudent(recipient.getUserId());
        int maxLimit = BorrowingPolicy.getMaxActiveBorrowingLimit();
        if ((activeLoans + activeAllocations) >= maxLimit) {
            throw new RecipientNotEligibleException("Recipient student has reached the maximum borrowing limit (" +
                    maxLimit + " active books/allocations).");
        }

        // 7. Persist REQUESTED interchange record
        BookInterchange interchange = new BookInterchange(
                transactionId, tx.getBookId(), tx.getCopyId(),
                senderUserId, recipient.getUserId(), senderUserId,
                tx.getIssueDate(), tx.getDueDate(), requestNote
        );
        interchange.setFromStudentName(tx.getStudentName());
        interchange.setFromStudentRegNo(tx.getRegisterNumber());
        interchange.setToStudentName(recipient.getName());
        interchange.setToStudentRegNo(recipient.getRegisterNumber());
        interchange.setBookTitle(tx.getBookTitle());
        interchange.setBookAuthor(tx.getBookAuthor());
        interchange.setCopyCode(tx.getCopyCode());
        interchange.setCoverImageUrl(tx.getCoverImageUrl());

        return interchangeRepository.insert(interchange);
    }

    /**
     * Executes atomic, concurrency-safe acceptance of an interchange request with optional physical OTP handover verification.
     */
    public BookInterchange acceptInterchange(int interchangeId, int recipientUserId, String handoverOtp) {
        OtpRepository otpRepo = new OtpRepository();
        OtpService otpService = new OtpService();
        try {
            OtpCode activeOtp = otpRepo.findActive(OtpPurpose.INTERCHANGE_HANDOVER, interchangeId);
            if (activeOtp != null) {
                if (handoverOtp == null || handoverOtp.trim().isEmpty()) {
                    throw new InvalidInterchangeException("Physical handover code is required to complete this transfer.");
                }
                boolean valid = otpService.verifyOtp(OtpPurpose.INTERCHANGE_HANDOVER, interchangeId, handoverOtp.trim());
                if (!valid) {
                    throw new InvalidInterchangeException("Invalid or expired physical handover code.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error verifying handover code: " + e.getMessage(), e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new InvalidInterchangeException(e.getMessage());
        }

        return acceptInterchange(interchangeId, recipientUserId);
    }

    /**
     * Executes atomic, concurrency-safe acceptance of an interchange request.
     * Transfers ownership of the existing borrowing transaction to the recipient.
     * Preserves original issue_date and due_date exactly.
     * Follows canonical lock sequence: users -> books -> book_copies -> borrow_transactions -> book_interchanges.
     */
    public BookInterchange acceptInterchange(int interchangeId, int recipientUserId) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;
            Integer originalIsolation = null;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                originalIsolation = conn.getTransactionIsolation();
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.setAutoCommit(false);

                // 1. Plain-read interchange to discover IDs
                String plainSql = "SELECT interchange_id, transaction_id, book_id, copy_id, " +
                        "from_student_id, to_student_id, status FROM book_interchanges WHERE interchange_id = ?";
                int transactionId;
                int bookId;
                int copyId;
                int fromStudentId;
                int toStudentId;

                try (PreparedStatement ps = conn.prepareStatement(plainSql)) {
                    ps.setInt(1, interchangeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InterchangeNotFoundException("Interchange request #" + interchangeId + " was not found.");
                        }
                        if (!InterchangeStatus.REQUESTED.name().equalsIgnoreCase(rs.getString("status"))) {
                            throw new InvalidInterchangeException("Interchange request #" + interchangeId +
                                    " is not in REQUESTED status (" + rs.getString("status") + ").");
                        }
                        transactionId = rs.getInt("transaction_id");
                        bookId = rs.getInt("book_id");
                        copyId = rs.getInt("copy_id");
                        fromStudentId = rs.getInt("from_student_id");
                        toStudentId = rs.getInt("to_student_id");
                    }
                }

                // Verify recipient authorization
                if (recipientUserId != toStudentId) {
                    throw new InterchangeNotAllowedException("You are not authorized to accept this interchange request.");
                }

                // 2. Canonical Lock Sequence:
                // 2a. Level 1: Lock both users in ascending user_id order to eliminate deadlocks
                int firstUser = Math.min(fromStudentId, toStudentId);
                int secondUser = Math.max(fromStudentId, toStudentId);

                lockUser(conn, firstUser);
                lockUser(conn, secondUser);

                // Re-verify recipient eligibility under exclusive user lock
                // Feature 6 Policy D10: Finalized unpaid fines block, but unreturned overdue books on other loans do not!
                if (BorrowingPolicy.hasOutstandingFines(toStudentId, conn)) {
                    throw new RecipientNotEligibleException("Recipient student has outstanding unpaid library fines. Clear dues before accepting transfer.");
                }

                // Re-verify recipient quota under user lock
                int recipientActiveLoans = countActiveLoans(conn, toStudentId);
                int recipientActiveAllocs = countActiveAllocations(conn, toStudentId);
                int maxLimit = BorrowingPolicy.getMaxActiveBorrowingLimit();
                if ((recipientActiveLoans + recipientActiveAllocs) >= maxLimit) {
                    throw new RecipientNotEligibleException("Recipient student has reached the maximum borrowing limit (" +
                            maxLimit + " books/allocations). Return books before accepting.");
                }

                // Re-verify same-book restriction under user lock
                if (hasActiveLoanForBook(conn, toStudentId, bookId)) {
                    throw new RecipientNotEligibleException("Recipient student already holds an active copy of this textbook.");
                }

                // 2b. Level 2: Lock parent book row
                String bookLockSql = "SELECT book_id, is_archived FROM books WHERE book_id = ? FOR UPDATE";
                try (PreparedStatement psBook = conn.prepareStatement(bookLockSql)) {
                    psBook.setInt(1, bookId);
                    try (ResultSet rs = psBook.executeQuery()) {
                        if (!rs.next()) throw new BookNotFoundException("Book #" + bookId + " not found.");
                        if (rs.getBoolean("is_archived")) {
                            throw new InterchangeNotAllowedException("Cannot interchange copies of an archived book title.");
                        }
                    }
                }

                // Re-verify reservation queue priority under book lock
                int waitingInQueue = countWaitingReservationsUnderLock(conn, bookId);
                if (waitingInQueue > 0) {
                    throw new ActiveReservationQueueException("Cannot complete interchange: " + waitingInQueue +
                            " student(s) entered the official reservation queue for this title.");
                }

                // 2c. Level 3: Lock physical copy row
                String copyLockSql = "SELECT copy_id, status FROM book_copies WHERE copy_id = ? FOR UPDATE";
                try (PreparedStatement psCopy = conn.prepareStatement(copyLockSql)) {
                    psCopy.setInt(1, copyId);
                    try (ResultSet rs = psCopy.executeQuery()) {
                        if (!rs.next()) throw new BookCopyUnavailableException("Copy #" + copyId + " not found.");
                        String copyStatus = rs.getString("status");
                        if (!CopyStatus.BORROWED.name().equalsIgnoreCase(copyStatus)) {
                            throw new InterchangeNotAllowedException("Physical copy is not in BORROWED status (current: " + copyStatus + ").");
                        }
                    }
                }

                // 2d. Level 4: Lock borrowing transaction row
                String txLockSql = "SELECT transaction_id, student_id, due_date, status FROM borrow_transactions WHERE transaction_id = ? FOR UPDATE";
                LocalDate dueDate;
                try (PreparedStatement psTx = conn.prepareStatement(txLockSql)) {
                    psTx.setInt(1, transactionId);
                    try (ResultSet rs = psTx.executeQuery()) {
                        if (!rs.next()) throw new BorrowTransactionNotFoundException("Borrowing transaction #" + transactionId + " not found.");
                        String txStatus = rs.getString("status");
                        if (!com.psgitech.library.enums.BorrowStatus.ACTIVE.name().equalsIgnoreCase(txStatus)) {
                            throw new InvalidInterchangeException("Borrowing transaction #" + transactionId +
                                    " is no longer ACTIVE (status: " + txStatus + "). It may have been returned.");
                        }
                        int currentStudentId = rs.getInt("student_id");
                        if (currentStudentId != fromStudentId) {
                            throw new InvalidInterchangeException("Borrowing transaction is no longer owned by the initiating student.");
                        }
                        dueDate = rs.getDate("due_date").toLocalDate();
                    }
                }

                // Re-verify recipient quota under Level 4 lock to ensure concurrent acceptance hasn't saturated quota
                int currentActiveLoans = countActiveLoans(conn, toStudentId);
                int currentActiveAllocs = countActiveAllocations(conn, toStudentId);
                if ((currentActiveLoans + currentActiveAllocs) >= maxLimit) {
                    throw new RecipientNotEligibleException("Recipient student has reached the maximum borrowing limit (" +
                            maxLimit + " books/allocations). Return books before accepting.");
                }

                // Re-verify due-date in Asia/Kolkata timezone: The particular book being transferred must not be overdue!
                ZoneId libraryZone = FineConfig.getLibraryZone();
                LocalDate today = LocalDate.now(libraryZone);
                if (today.isAfter(dueDate)) {
                    throw new InterchangeNotAllowedException("This book has become overdue (due date: " + dueDate +
                            "). Overdue books cannot be transferred; please return the copy to the library desk.");
                }

                // 2e. Level 11: Lock book_interchanges row
                BookInterchange lockedInterchange = interchangeRepository.lockById(conn, interchangeId);
                if (lockedInterchange == null || lockedInterchange.getStatus() != InterchangeStatus.REQUESTED) {
                    throw new InvalidInterchangeException("Interchange request is no longer in REQUESTED status.");
                }

                // 3. Conditional Update: Transfer borrowing transaction to recipient
                // Preserves issue_date, due_date, copy_id, book_id, transaction_id exactly!
                String transferSql = "UPDATE borrow_transactions SET student_id = ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE transaction_id = ? AND student_id = ? AND status = 'ACTIVE'";
                try (PreparedStatement psTransfer = conn.prepareStatement(transferSql)) {
                    psTransfer.setInt(1, toStudentId);
                    psTransfer.setInt(2, transactionId);
                    psTransfer.setInt(3, fromStudentId);
                    int affected = psTransfer.executeUpdate();
                    if (affected == 0) {
                        throw new InvalidInterchangeException("Failed to transfer borrowing transaction. State conflict detected.");
                    }
                }

                // 4. Conditional Update: Transition interchange to COMPLETED
                interchangeRepository.completeInterchange(conn, interchangeId, recipientUserId);

                // Commit the atomic transfer
                conn.commit();

                lockedInterchange.complete(recipientUserId);
                return lockedInterchange;

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
                    try {
                        if (originalIsolation != null) conn.setTransactionIsolation(originalIsolation);
                        conn.setAutoCommit(originalAutoCommit);
                        conn.close();
                    } catch (SQLException ignored) {}
                }
            }
        }, "acceptInterchange");
    }

    /**
     * Rejects a pending interchange request by the designated recipient.
     */
    public BookInterchange rejectInterchange(int interchangeId, int recipientUserId, String reason) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                BookInterchange interchange = interchangeRepository.lockById(conn, interchangeId);
                if (interchange == null) {
                    throw new InterchangeNotFoundException("Interchange request #" + interchangeId + " was not found.");
                }
                if (interchange.getToStudentId() != recipientUserId) {
                    throw new InterchangeNotAllowedException("You are not authorized to reject this interchange request.");
                }
                if (interchange.getStatus() != InterchangeStatus.REQUESTED) {
                    throw new InvalidInterchangeException("Interchange request is not in REQUESTED status (" + interchange.getStatus() + ").");
                }

                interchangeRepository.rejectInterchange(conn, interchangeId, recipientUserId, reason);
                conn.commit();

                interchange.reject(recipientUserId, reason);
                return interchange;

            } catch (SQLException | RuntimeException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "rejectInterchange");
    }

    /**
     * Cancels a pending interchange request by the initiating sender.
     */
    public BookInterchange cancelInterchange(int interchangeId, int senderUserId, String reason) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                BookInterchange interchange = interchangeRepository.lockById(conn, interchangeId);
                if (interchange == null) {
                    throw new InterchangeNotFoundException("Interchange request #" + interchangeId + " was not found.");
                }
                if (interchange.getFromStudentId() != senderUserId) {
                    throw new InterchangeNotAllowedException("You are not authorized to cancel this interchange request.");
                }
                if (interchange.getStatus() != InterchangeStatus.REQUESTED) {
                    throw new InvalidInterchangeException("Interchange request is not in REQUESTED status (" + interchange.getStatus() + ").");
                }

                interchangeRepository.cancelInterchange(conn, interchangeId, senderUserId, reason);
                conn.commit();

                interchange.cancel(senderUserId, reason);
                return interchange;

            } catch (SQLException | RuntimeException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "cancelInterchange");
    }

    // --- Query Operations ---

    public List<BookInterchange> getIncomingRequests(int studentId) {
        return interchangeRepository.findIncomingForStudent(studentId);
    }

    public List<BookInterchange> getOutgoingRequests(int studentId) {
        return interchangeRepository.findOutgoingForStudent(studentId);
    }

    public List<BookInterchange> getInterchangeHistory(int studentId) {
        return interchangeRepository.findHistoryForStudent(studentId);
    }

    public int getPendingIncomingCount(int studentId) {
        return interchangeRepository.countPendingIncomingForStudent(studentId);
    }

    public BookInterchange getInterchangeById(int interchangeId, int requestingUserId, boolean isStaff) {
        BookInterchange bi = interchangeRepository.findById(interchangeId);
        if (bi == null) {
            throw new InterchangeNotFoundException("Interchange request #" + interchangeId + " was not found.");
        }
        if (!isStaff && bi.getFromStudentId() != requestingUserId && bi.getToStudentId() != requestingUserId) {
            throw new InterchangeNotAllowedException("You do not have permission to view this interchange record.");
        }
        return bi;
    }

    public List<BookInterchange> getAllInterchanges(int limit, int offset, String statusFilter) {
        return interchangeRepository.findAll(limit, offset, statusFilter);
    }

    // --- Helper Validation Methods ---

    private void lockUser(Connection conn, int userId) throws SQLException {
        String sql = "SELECT user_id, active, role FROM users WHERE user_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new StudentNotFoundException("User #" + userId + " was not found.");
                if (!rs.getBoolean("active")) {
                    throw new StudentNotEligibleException("User account #" + userId + " is inactive or suspended.");
                }
            }
        }
    }

    private int countActiveLoans(Connection conn, int studentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM borrow_transactions WHERE student_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private int countActiveAllocations(Connection conn, int studentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM allocations WHERE student_id = ? AND status = 'ALLOCATED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private int countActiveAllocationsForStudent(int studentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return countActiveAllocations(conn, studentId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting active allocations", e);
            throw new RuntimeException("Database error checking student allocation capacity.", e);
        }
    }

    private boolean hasActiveLoanForBook(Connection conn, int studentId, int bookId) throws SQLException {
        String sql = "SELECT 1 FROM borrow_transactions WHERE student_id = ? AND book_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int countWaitingReservationsUnderLock(Connection conn, int bookId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reservations WHERE book_id = ? AND status IN ('WAITING', 'ALLOCATED')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private int countWaitingAndAllocatedReservations(int bookId) {
        try (Connection conn = DBConnection.getConnection()) {
            return countWaitingReservationsUnderLock(conn, bookId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking reservation queue for book #" + bookId, e);
            throw new RuntimeException("Database error checking reservation queue.", e);
        }
    }
}
