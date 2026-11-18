package com.psgitech.library.service;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating Book Borrowing and Return business logic.
 * Coordinates between UserRepository, BookRepository, and BorrowRepository.
 * Enforces student eligibility, active limits, same-book restrictions, and date calculations.
 */
public class BorrowingService {
    private final BorrowRepository borrowRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    public BorrowingService() {
        this.borrowRepository = new BorrowRepository();
        this.userRepository = new UserRepository();
        this.bookRepository = new BookRepository();
    }

    public BorrowingService(BorrowRepository borrowRepository, UserRepository userRepository, BookRepository bookRepository) {
        this.borrowRepository = borrowRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
    }

    /**
     * Issues a physical book copy to a student after comprehensive validation.
     * Calculates issue date and due date automatically on the backend.
     */
    public BorrowTransaction issueBook(String studentRegisterNumber, int copyId, int staffUserId) {
        if (studentRegisterNumber == null || studentRegisterNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Student register number is required.");
        }
        if (copyId <= 0) {
            throw new IllegalArgumentException("Valid physical copy ID is required.");
        }

        // 1. Validate student existence and role
        Student student = userRepository.findByRegisterNumber(studentRegisterNumber.trim());
        if (student == null) {
            throw new StudentNotFoundException("Student with register number '" + studentRegisterNumber + "' was not found.");
        }
        if (!student.isActive()) {
            throw new StudentNotEligibleException("Student account is inactive or suspended. Cannot borrow books.");
        }
        if (student.getRole() != Role.STUDENT) {
            throw new StudentNotEligibleException("Only registered students are eligible for student book borrowing.");
        }

        // 2. Validate copy and parent book
        BookCopy copy = null;
        Book parentBook = null;
        // Search among books to find copy and parent
        // We can query book_copies and parent book
        // In BorrowRepository, issueBookTransaction locks and checks book and copy inside JDBC transaction
        // But we resolve the bookId here first to pass to the transaction:
        int bookId = findBookIdByCopyId(copyId);
        if (bookId <= 0) {
            throw new BookCopyUnavailableException("Physical copy with ID " + copyId + " does not exist.");
        }

        parentBook = bookRepository.findById(bookId, false);
        if (parentBook == null) {
            throw new BookNotFoundException("Parent book for physical copy #" + copyId + " was not found.");
        }
        if (parentBook.isArchived()) {
            throw new InvalidBorrowingOperationException("Cannot borrow copies from an archived book title.");
        }

        // 3. Pre-check active borrowing capacity
        int activeCount = borrowRepository.countActiveByStudentId(student.getUserId());
        int maxLimit = BorrowingPolicy.getMaxActiveBorrowingLimit();
        if (activeCount >= maxLimit) {
            throw new BorrowingLimitExceededException("Student has reached the maximum borrowing limit (" + maxLimit + " active books).");
        }

        // 4. Pre-check same-book restriction
        if (borrowRepository.hasActiveBorrowingForBook(student.getUserId(), bookId)) {
            throw new InvalidBorrowingOperationException("Student already has an active copy of \"" + parentBook.getTitle() + "\". Multiple copies of the same book cannot be borrowed simultaneously.");
        }

        // 5. Automatic date calculations (14 days standard duration)
        LocalDate issueDate = LocalDate.now();
        LocalDate dueDate = BorrowingPolicy.calculateDueDate(issueDate);

        // 6. Execute atomic transaction in repository (with pessimistic locking)
        return borrowRepository.issueBookTransaction(student.getUserId(), bookId, copyId, issueDate, dueDate, staffUserId);
    }

    /**
     * Allows an authenticated eligible student to directly borrow an available book copy.
     * Automatically resolves the first available physical copy and calculates loan duration.
     */
    public BorrowTransaction borrowBookForStudent(int studentUserId, int bookId) {
        if (studentUserId <= 0) {
            throw new IllegalArgumentException("Valid student ID is required.");
        }
        if (bookId <= 0) {
            throw new IllegalArgumentException("Valid book ID is required.");
        }

        User student = userRepository.findById(studentUserId);
        if (student == null) {
            throw new StudentNotFoundException("Student with ID " + studentUserId + " was not found.");
        }
        if (!student.isActive()) {
            throw new StudentNotEligibleException("Student account is inactive or suspended. Cannot borrow books.");
        }
        if (student.getRole() != Role.STUDENT) {
            throw new StudentNotEligibleException("Only registered students are eligible for student book borrowing.");
        }

        Book parentBook = bookRepository.findById(bookId, false);
        if (parentBook == null) {
            throw new BookNotFoundException("Book with ID " + bookId + " was not found in catalog.");
        }
        if (parentBook.isArchived()) {
            throw new InvalidBorrowingOperationException("Cannot borrow copies from an archived book title.");
        }

        // Check active borrowing capacity
        int activeCount = borrowRepository.countActiveByStudentId(studentUserId);
        int maxLimit = BorrowingPolicy.getMaxActiveBorrowingLimit();
        if (activeCount >= maxLimit) {
            throw new BorrowingLimitExceededException("You have reached the maximum borrowing limit (" + maxLimit + " active books). Return a book before borrowing another.");
        }

        // Check same-book restriction
        if (borrowRepository.hasActiveBorrowingForBook(studentUserId, bookId)) {
            throw new InvalidBorrowingOperationException("You already have an active copy of \"" + parentBook.getTitle() + "\". Multiple copies of the same book cannot be borrowed simultaneously.");
        }

        // Find first available physical copy
        int copyId = bookRepository.findFirstAvailableCopyId(bookId);
        if (copyId <= 0) {
            throw new BookCopyUnavailableException("All physical copies of \"" + parentBook.getTitle() + "\" are currently borrowed or on hold. Please place a reservation instead.");
        }

        // Calculate due date according to effective dynamic loan policy
        LocalDate issueDate = LocalDate.now();
        com.psgitech.library.policy.LoanPolicy policy = com.psgitech.library.policy.LoanPolicyManager.getInstance().effectivePolicy(bookId);
        int loanDays = (policy != null) ? policy.getLoanDays() : BorrowingPolicy.getStandardBorrowingDurationDays();
        LocalDate dueDate = issueDate.plusDays(loanDays);

        return borrowRepository.issueBookTransaction(studentUserId, bookId, copyId, issueDate, dueDate, studentUserId);
    }

    public BorrowTransaction getTransactionById(int transactionId) {
        return borrowRepository.findById(transactionId);
    }

    public BorrowRepository getBorrowRepository() {
        return borrowRepository;
    }

    /**
     * Records the return of an active borrowing transaction.
     * Calculates overdue days, marks transaction RETURNED, and resets copy to AVAILABLE.
     */
    public BorrowTransaction returnBook(int transactionId, int staffUserId) {
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Valid transaction ID is required.");
        }

        BorrowTransaction existing = borrowRepository.findById(transactionId);
        if (existing == null) {
            throw new BorrowTransactionNotFoundException("Borrowing transaction #" + transactionId + " was not found.");
        }
        if (existing.getStatus() == BorrowStatus.RETURNED) {
            throw new InvalidBorrowingOperationException("Transaction #" + transactionId + " has already been returned. Duplicate return recording is prohibited.");
        }
        if (existing.getStatus() == BorrowStatus.LOST) {
            throw new InvalidBorrowingOperationException("Transaction #" + transactionId + " was closed as LOST. Cannot process standard return for a lost book.");
        }

        LocalDate returnDate = LocalDate.now();
        return borrowRepository.returnBookTransaction(transactionId, staffUserId, returnDate);
    }

    public BorrowTransaction returnBook(int transactionId, int staffUserId, LocalDate returnDate,
                                        String condition, com.psgitech.library.enums.DamageSeverity severity,
                                        com.psgitech.library.enums.CopyDisposition disposition,
                                        BigDecimal assessedValue, String note) {
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Valid transaction ID is required.");
        }

        BorrowTransaction existing = borrowRepository.findById(transactionId);
        if (existing == null) {
            throw new BorrowTransactionNotFoundException("Borrowing transaction #" + transactionId + " was not found.");
        }
        if (existing.getStatus() == BorrowStatus.RETURNED) {
            throw new InvalidBorrowingOperationException("Transaction #" + transactionId + " has already been returned. Duplicate return recording is prohibited.");
        }
        if (existing.getStatus() == BorrowStatus.LOST) {
            throw new InvalidBorrowingOperationException("Transaction #" + transactionId + " was closed as LOST. Cannot process standard return for a lost book.");
        }

        LocalDate effectiveReturnDate = (returnDate != null) ? returnDate : LocalDate.now();
        return borrowRepository.returnBookTransaction(transactionId, staffUserId, effectiveReturnDate,
                condition, severity, disposition, assessedValue, note);
    }

    /**
     * Looks up student details, current active borrow count, and remaining capacity for staff issue desk.
     */
    public Map<String, Object> lookupStudentForBorrowing(String registerNumber, Integer selectedBookId) {
        if (registerNumber == null || registerNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Register number is required for lookup.");
        }

        Student student = userRepository.findByRegisterNumber(registerNumber.trim());
        if (student == null) {
            throw new StudentNotFoundException("Student with register number '" + registerNumber + "' not found.");
        }

        int activeCount = borrowRepository.countActiveByStudentId(student.getUserId());
        int maxLimit = BorrowingPolicy.getMaxActiveBorrowingLimit();
        int remainingCapacity = Math.max(0, maxLimit - activeCount);
        boolean isEligible = student.isActive() && remainingCapacity > 0;

        boolean alreadyHasSameBook = false;
        if (selectedBookId != null && selectedBookId > 0) {
            alreadyHasSameBook = borrowRepository.hasActiveBorrowingForBook(student.getUserId(), selectedBookId);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("studentId", student.getUserId());
        data.put("studentName", student.getName());
        data.put("registerNumber", student.getRegisterNumber());
        data.put("department", student.getDepartment());
        data.put("year", student.getYear());
        data.put("accountStatus", student.isActive() ? "ACTIVE" : "INACTIVE");
        data.put("activeBorrowings", activeCount);
        data.put("maxLimit", maxLimit);
        data.put("remainingCapacity", remainingCapacity);
        data.put("isEligible", isEligible);
        data.put("alreadyHasSameBook", alreadyHasSameBook);

        return data;
    }

    /**
     * Looks up active transactions for the staff return desk by copy code, transaction ID, or student regNo.
     */
    public List<BorrowTransaction> lookupActiveForReturn(String query) {
        if (query == null || query.trim().isEmpty()) {
            return borrowRepository.findAllActive(null);
        }

        String trimmed = query.trim();

        // 1. Check if numeric transaction ID
        try {
            int txId = Integer.parseInt(trimmed);
            BorrowTransaction tx = borrowRepository.findById(txId);
            if (tx != null && tx.getStatus() == BorrowStatus.ACTIVE) {
                return List.of(tx);
            }
        } catch (NumberFormatException ignored) {}

        // 2. Check if physical copy code
        BorrowTransaction byCopy = borrowRepository.findActiveByCopyCode(trimmed);
        if (byCopy != null) {
            return List.of(byCopy);
        }

        // 3. Check by student register number
        List<BorrowTransaction> byStudent = borrowRepository.findActiveByRegisterNumber(trimmed);
        if (!byStudent.isEmpty()) {
            return byStudent;
        }

        // 4. General search across active transactions
        return borrowRepository.findAllActive(trimmed);
    }

    /**
     * Retrieves currently active borrowings for a student.
     */
    public List<BorrowTransaction> getStudentActiveBorrowings(int studentId) {
        List<BorrowTransaction> list = borrowRepository.findActiveByStudentId(studentId);
        LocalDate today = LocalDate.now();
        for (BorrowTransaction tx : list) {
            tx.computeDynamicMetrics(today);
        }
        return list;
    }

    /**
     * Retrieves all borrowing history for a student.
     */
    public List<BorrowTransaction> getStudentBorrowingHistory(int studentId) {
        List<BorrowTransaction> list = borrowRepository.findHistoryByStudentId(studentId);
        LocalDate today = LocalDate.now();
        for (BorrowTransaction tx : list) {
            tx.computeDynamicMetrics(today);
        }
        return list;
    }

    /**
     * Retrieves all active borrowings across the system (staff view).
     */
    public List<BorrowTransaction> getAllActiveBorrowings(String query) {
        List<BorrowTransaction> list = borrowRepository.findAllActive(query);
        LocalDate today = LocalDate.now();
        for (BorrowTransaction tx : list) {
            tx.computeDynamicMetrics(today);
        }
        return list;
    }

    /**
     * Retrieves all historical borrowings across the system (staff view).
     */
    public List<BorrowTransaction> getAllBorrowingHistory(String query) {
        List<BorrowTransaction> list = borrowRepository.findAllHistory(query);
        LocalDate today = LocalDate.now();
        for (BorrowTransaction tx : list) {
            tx.computeDynamicMetrics(today);
        }
        return list;
    }

    /**
     * Retrieves all currently overdue active borrowings (staff view).
     */
    public List<BorrowTransaction> getOverdueBorrowings() {
        List<BorrowTransaction> list = borrowRepository.findOverdueTransactions();
        LocalDate today = LocalDate.now();
        for (BorrowTransaction tx : list) {
            tx.computeDynamicMetrics(today);
        }
        return list;
    }

    /**
     * Calculates overdue days between due date and target date.
     * Returns 0 if not overdue.
     */
    public static long calculateOverdueDays(LocalDate dueDate, LocalDate evaluationDate) {
        if (dueDate == null) return 0;
        if (evaluationDate == null) evaluationDate = LocalDate.now();
        if (evaluationDate.isAfter(dueDate)) {
            return ChronoUnit.DAYS.between(dueDate, evaluationDate);
        }
        return 0;
    }

    /**
     * Renews an active book borrowing transaction by extending its due date by standard borrowing duration.
     * Enforces single renewal limit, zero waiting reservations, zero pending interchanges, and fine clearance.
     */
    public BorrowTransaction renewLoan(int transactionId, int studentId, int actingUserId, Role actingRole) {
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Valid transaction ID is required for renewal.");
        }
        return borrowRepository.renewLoan(transactionId, studentId, actingUserId, actingRole);
    }

    /**
     * Helper to find book_id for a given copy_id using JDBC.
     */
    private int findBookIdByCopyId(int copyId) {
        String sql = "SELECT book_id FROM book_copies WHERE copy_id = ?";
        try (java.sql.Connection conn = com.psgitech.library.util.DBConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, copyId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("book_id");
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Database error finding book ID for copy #" + copyId, e);
        }
        return -1;
    }
}

