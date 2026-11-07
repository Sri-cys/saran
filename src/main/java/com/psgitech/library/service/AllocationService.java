package com.psgitech.library.service;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.AllocationStatus;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.ReservationStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.interfaces.AllocationStrategy;
import com.psgitech.library.interfaces.EligibilityChecker;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.AllocationRepository;
import com.psgitech.library.repository.ReservationRepository;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.TransactionRetryHelper;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core domain service managing automatic book allocations.
 * Coordinates candidate selection, copy status transitions, desk collection,
 * and allocation releases.
 */
public class AllocationService {
    private static final Logger LOGGER = Logger.getLogger(AllocationService.class.getName());

    private final AllocationRepository allocationRepository;
    private final ReservationRepository reservationRepository;
    private final AllocationStrategy allocationStrategy;

    public AllocationService() {
        this.allocationRepository = new AllocationRepository();
        this.reservationRepository = new ReservationRepository();
        this.allocationStrategy = new FIFOAllocationStrategy();
    }

    public AllocationService(AllocationRepository allocationRepository,
                             ReservationRepository reservationRepository,
                             AllocationStrategy allocationStrategy) {
        this.allocationRepository = allocationRepository;
        this.reservationRepository = reservationRepository;
        this.allocationStrategy = allocationStrategy;
    }

    /**
     * Executes automatic allocation for a returned physical copy within the caller's transaction.
     * If an eligible waiting reservation exists, transitions the copy to ALLOCATED and links it.
     * If queue is empty or no candidates are eligible, transitions the copy to AVAILABLE.
     */
    public AllocationResult allocateReturnedCopy(Connection conn, int copyId, int bookId, Integer returnTransactionId)
            throws SQLException {

        // 1. Retrieve copy code for logging and result reporting
        String copyCode = "";
        String copyQuery = "SELECT copy_code FROM book_copies WHERE copy_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(copyQuery)) {
            ps.setInt(1, copyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    copyCode = rs.getString("copy_code");
                }
            }
        }

        // 2. Lock waiting reservations for this book in FIFO sequence
        List<Reservation> waitingList = reservationRepository.lockWaitingByBookFifo(conn, bookId);
        ReservationQueue queue = new ReservationQueue(bookId, waitingList);

        // 3. Evaluate candidates using AllocationStrategy and EligibilityChecker
        DefaultEligibilityChecker checker = new DefaultEligibilityChecker(bookId);
        Optional<Reservation> selectedOpt = allocationStrategy.selectNext(queue, checker, conn);

        if (selectedOpt.isPresent()) {
            Reservation selected = selectedOpt.get();
            int reservationId = selected.getReservationId();
            int studentId = selected.getStudentId();

            LOGGER.info("Allocating copy " + copyCode + " (ID: " + copyId + ") to student ID " + studentId +
                    " (Reservation #" + reservationId + ")");

            // 4a. Update reservation WAITING -> ALLOCATED
            reservationRepository.markAllocated(conn, reservationId);

            // 4b. Update physical copy status to ALLOCATED (conditional guard on BORROWED or ALLOCATED)
            String updateCopySql = "UPDATE book_copies SET status = 'ALLOCATED', updated_at = CURRENT_TIMESTAMP " +
                    "WHERE copy_id = ? AND status IN ('BORROWED', 'ALLOCATED')";
            try (PreparedStatement psCopy = conn.prepareStatement(updateCopySql)) {
                psCopy.setInt(1, copyId);
                int affected = psCopy.executeUpdate();
                if (affected == 0) {
                    throw new AllocationFailedException("Physical copy #" + copyId + " could not be marked ALLOCATED.");
                }
            }

            // 4c. Insert new allocation record with 24-hour collection deadline
            int collectionHours = BorrowingPolicy.getAllocationCollectionHours();
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Timestamp expiresAt = Timestamp.from(Instant.now().plus(collectionHours, ChronoUnit.HOURS));

            AllocationRecord record = new AllocationRecord(reservationId, studentId, bookId, copyId, returnTransactionId, expiresAt);
            record.setAllocatedAt(now);
            record.setCopyCode(copyCode);
            record.setBookTitle(selected.getBookTitle());
            record.setBookIsbn(selected.getBookIsbn());
            record.setStudentName(selected.getStudentName());
            record.setRegisterNumber(selected.getRegisterNumber());

            AllocationRecord inserted = allocationRepository.insert(conn, record);

            return AllocationResult.allocated(copyId, copyCode, inserted);

        } else {
            // No eligible student found in queue -> copy transitions to AVAILABLE
            LOGGER.info("No eligible waiting reservations for book ID " + bookId +
                    ". Copy " + copyCode + " (ID: " + copyId + ") transitioning to AVAILABLE.");

            String makeAvailSql = "UPDATE book_copies SET status = 'AVAILABLE', updated_at = CURRENT_TIMESTAMP " +
                    "WHERE copy_id = ? AND status IN ('BORROWED', 'ALLOCATED')";
            try (PreparedStatement psAvail = conn.prepareStatement(makeAvailSql)) {
                psAvail.setInt(1, copyId);
                int affected = psAvail.executeUpdate();
                if (affected == 0) {
                    throw new AllocationFailedException("Physical copy #" + copyId + " could not be transitioned to AVAILABLE.");
                }
            }

            return AllocationResult.madeAvailable(copyId, copyCode);
        }
    }

    /**
     * Validates that an ALLOCATED physical copy can be collected by the given student.
     * Verifies that the copy is held for this student and re-verifies student eligibility.
     * Follows canonical lock sequence: locks reservation row FOR UPDATE, then allocation row FOR UPDATE.
     */
    public AllocationRecord validateCollection(Connection conn, int copyId, int studentId) throws SQLException {
        AllocationRecord activeAlloc = allocationRepository.findActiveByCopy(conn, copyId);
        if (activeAlloc == null) {
            throw new InvalidAllocationException("No active allocation found for copy #" + copyId + ".");
        }

        if (activeAlloc.getStudentId() != studentId) {
            throw new InvalidAllocationException("This physical copy is currently held for another student (Reservation #" +
                    activeAlloc.getReservationId() + ").");
        }

        // Canonical Lock Order: lock reservation row FOR UPDATE FIRST
        reservationRepository.lockById(conn, activeAlloc.getReservationId());

        // Lock allocation row FOR UPDATE SECOND
        AllocationRecord lockedAlloc = allocationRepository.lockActiveByCopy(conn, copyId);
        if (lockedAlloc == null || lockedAlloc.getStudentId() != studentId) {
            throw new InvalidAllocationException("Allocation is no longer active for this student.");
        }

        // Condition 10: Collection must re-validate eligibility at collection time (including fines)
        revalidateStudentForCollection(conn, studentId, lockedAlloc.getBookId(), lockedAlloc.getAllocationId());
        return lockedAlloc;
    }

    /**
     * Completes collection transition: updates allocation to COLLECTED and reservation to FULFILLED.
     */
    public void completeCollection(Connection conn, int allocationId, int reservationId, int newBorrowTxId) throws SQLException {
        Timestamp collectedAt = new Timestamp(System.currentTimeMillis());
        allocationRepository.markCollected(conn, allocationId, newBorrowTxId, collectedAt);
        reservationRepository.markFulfilled(conn, reservationId);
    }

    /**
     * Validates and completes collection when an ALLOCATED copy is issued to a student at the desk.
     */
    public void validateAndCollect(Connection conn, int copyId, int studentId, int newBorrowTxId) throws SQLException {
        AllocationRecord activeAlloc = validateCollection(conn, copyId, studentId);
        completeCollection(conn, activeAlloc.getAllocationId(), activeAlloc.getReservationId(), newBorrowTxId);
    }

    /**
     * Releases an ALLOCATED reservation and reallocates the physical copy to the next eligible waiter,
     * or transitions it to AVAILABLE if no waiter remains.
     */
    public AllocationResult releaseAllocation(int reservationId, int actingUserId, Role actingRole) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // 1. Plain-read reservation and active allocation on the SAME connection (NO FOR UPDATE before book lock)
                int bookId = -1;
                String plainResSql = "SELECT book_id, student_id, status FROM reservations WHERE reservation_id = ?";
                try (PreparedStatement psPlainRes = conn.prepareStatement(plainResSql)) {
                    psPlainRes.setInt(1, reservationId);
                    try (ResultSet rs = psPlainRes.executeQuery()) {
                        if (!rs.next()) {
                            throw new AllocationNotFoundException("Reservation #" + reservationId + " not found.");
                        }
                        bookId = rs.getInt("book_id");
                    }
                }

                int copyId = -1;
                String plainAllocSql = "SELECT copy_id FROM allocations WHERE reservation_id = ? AND status = 'ALLOCATED'";
                try (PreparedStatement psPlainAlloc = conn.prepareStatement(plainAllocSql)) {
                    psPlainAlloc.setInt(1, reservationId);
                    try (ResultSet rs = psPlainAlloc.executeQuery()) {
                        if (!rs.next()) {
                            throw new AllocationNotFoundException("Active allocation for reservation #" + reservationId + " not found.");
                        }
                        copyId = rs.getInt("copy_id");
                    }
                }

                // 2. Canonical lock sequence: Lock book row FOR UPDATE on 'conn'
                String bookLockSql = "SELECT book_id, is_archived FROM books WHERE book_id = ? FOR UPDATE";
                try (PreparedStatement psBook = conn.prepareStatement(bookLockSql)) {
                    psBook.setInt(1, bookId);
                    try (ResultSet rs = psBook.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookNotFoundException("Book #" + bookId + " not found.");
                        }
                    }
                }

                // 3. Lock physical copy row FOR UPDATE on 'conn'
                String copyLockSql = "SELECT copy_id, copy_code, status FROM book_copies WHERE copy_id = ? FOR UPDATE";
                try (PreparedStatement psCopy = conn.prepareStatement(copyLockSql)) {
                    psCopy.setInt(1, copyId);
                    try (ResultSet rs = psCopy.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookCopyUnavailableException("Copy #" + copyId + " not found.");
                        }
                    }
                }

                // 4. Lock reservation row FOR UPDATE on 'conn'
                Reservation lockedRes = reservationRepository.lockById(conn, reservationId);
                if (lockedRes == null) {
                    throw new AllocationNotFoundException("Reservation #" + reservationId + " not found.");
                }

                // 5. Lock active allocation record FOR UPDATE on 'conn'
                AllocationRecord activeAlloc = allocationRepository.findActiveByReservation(conn, reservationId);
                if (activeAlloc == null) {
                    throw new AllocationNotFoundException("Active allocation for reservation #" + reservationId + " not found.");
                }

                // 6. Re-verify status and ownership under locks
                if (actingRole == Role.STUDENT && lockedRes.getStudentId() != actingUserId) {
                    throw new AllocationNotFoundException("Reservation #" + reservationId + " not found.");
                }

                if (lockedRes.getStatus() != ReservationStatus.ALLOCATED) {
                    throw new InvalidAllocationException("Reservation #" + reservationId + " is in " +
                            lockedRes.getStatus() + " status and cannot be released as an active allocation.");
                }

                // 8. Update allocation to CANCELLED
                Timestamp cancelledAt = new Timestamp(System.currentTimeMillis());
                allocationRepository.markCancelled(conn, activeAlloc.getAllocationId(), actingUserId, cancelledAt);

                // 9. Update reservation to CANCELLED
                reservationRepository.markCancelledFromAllocated(conn, reservationId, actingUserId, cancelledAt);

                // 10. Re-run automatic allocation for the freed copy
                AllocationResult outcome = allocateReturnedCopy(conn, copyId, bookId, null);

                conn.commit();
                return outcome;

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
        }, "releaseAllocation");
    }

    /**
     * Checks if a physical copy is currently ALLOCATED to an active student.
     */
    public boolean isCopyAllocated(int copyId) {
        AllocationRecord active = allocationRepository.findActiveByCopy(copyId);
        return active != null && active.getStatus() == AllocationStatus.ALLOCATED;
    }

    public AllocationRecord getActiveAllocationForCopy(int copyId) {
        return allocationRepository.findActiveByCopy(copyId);
    }

    public List<AllocationRecord> getActiveAllocationsByStudent(int studentId) {
        return allocationRepository.findAllocatedByStudent(studentId);
    }

    public List<AllocationRecord> listHoldShelfAllocations(AllocationStatus status, Integer bookId, int page, int limit) {
        int offset = (Math.max(page, 1) - 1) * limit;
        return allocationRepository.findByStatus(status != null ? status : AllocationStatus.ALLOCATED, bookId, limit, offset);
    }

    public int countHoldShelfAllocations(AllocationStatus status, Integer bookId) {
        return allocationRepository.countByStatus(status != null ? status : AllocationStatus.ALLOCATED, bookId);
    }

    /**
     * Helper revalidating student eligibility at desk collection time.
     */
    private void revalidateStudentForCollection(Connection conn, int studentId, int bookId, int currentAllocationId)
            throws SQLException {
        // 1. Account active check
        String userSql = "SELECT active, role FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(userSql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean("active")) {
                    throw new InvalidAllocationException("Student account is inactive or suspended. Cannot collect book.");
                }
            }
        }

        // 2. Outstanding fines check (Condition 6)
        if (BorrowingPolicy.hasOutstandingFines(studentId, conn)) {
            throw new InvalidAllocationException("Student has outstanding library fines. Clear fines before collecting allocated books.");
        }

        // 3. Borrowing quota check: active loans + OTHER active allocations < limit
        String loanSql = "SELECT COUNT(*) FROM borrow_transactions WHERE student_id = ? AND status = 'ACTIVE'";
        int activeLoans = 0;
        try (PreparedStatement ps = conn.prepareStatement(loanSql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) activeLoans = rs.getInt(1);
            }
        }

        String allocSql = "SELECT COUNT(*) FROM allocations WHERE student_id = ? AND status = 'ALLOCATED' AND allocation_id != ?";
        int otherAllocs = 0;
        try (PreparedStatement ps = conn.prepareStatement(allocSql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, currentAllocationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) otherAllocs = rs.getInt(1);
            }
        }

        int maxLimit = BorrowingPolicy.getMaxActiveBorrowingLimit();
        if ((activeLoans + otherAllocs) >= maxLimit) {
            throw new InvalidAllocationException("Student has reached the maximum borrowing limit (" + maxLimit +
                    " books). Return existing books before collecting this reservation.");
        }

        // 4. Duplicate loan check: student cannot already have an active loan of this book
        String sameBookSql = "SELECT COUNT(*) FROM borrow_transactions WHERE student_id = ? AND book_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sameBookSql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new InvalidAllocationException("Student already holds an active borrowed copy of this book.");
                }
            }
        }
    }

    /**
     * Default EligibilityChecker evaluating candidate student constraints inside return/release transaction.
     */
    private class DefaultEligibilityChecker implements EligibilityChecker {
        private final int bookId;
        private String lastRejectionReason = "";

        public DefaultEligibilityChecker(int bookId) {
            this.bookId = bookId;
        }

        @Override
        public boolean isEligible(Reservation reservation, Connection conn) {
            int studentId = reservation.getStudentId();
            try {
                // 1. Verify student exists and account is active (plain SELECT, no user lock)
                String userSql = "SELECT active, role FROM users WHERE user_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                    ps.setInt(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            lastRejectionReason = "Student #" + studentId + " not found.";
                            return false;
                        }
                        if (!rs.getBoolean("active")) {
                            lastRejectionReason = "Student #" + studentId + " account is inactive.";
                            return false;
                        }
                    }
                }

                // 2. Shared outstanding fines check (Condition 6)
                if (BorrowingPolicy.hasOutstandingFines(studentId, conn)) {
                    lastRejectionReason = "Student #" + studentId + " has unpaid library fines.";
                    return false;
                }

                // 3. Verify student does not already hold an active borrow transaction for this book
                String activeLoanSql = "SELECT COUNT(*) FROM borrow_transactions WHERE student_id = ? AND book_id = ? AND status = 'ACTIVE'";
                try (PreparedStatement ps = conn.prepareStatement(activeLoanSql)) {
                    ps.setInt(1, studentId);
                    ps.setInt(2, bookId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            lastRejectionReason = "Student #" + studentId + " already has an active borrow for book #" + bookId + ".";
                            return false;
                        }
                    }
                }

                // 4. Check borrowing quota: active loans + currently ALLOCATED allocations < maxLimit
                String countLoanSql = "SELECT COUNT(*) FROM borrow_transactions WHERE student_id = ? AND status = 'ACTIVE'";
                int loans = 0;
                try (PreparedStatement ps = conn.prepareStatement(countLoanSql)) {
                    ps.setInt(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) loans = rs.getInt(1);
                    }
                }

                String countAllocSql = "SELECT COUNT(*) FROM allocations WHERE student_id = ? AND status = 'ALLOCATED'";
                int allocs = 0;
                try (PreparedStatement ps = conn.prepareStatement(countAllocSql)) {
                    ps.setInt(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) allocs = rs.getInt(1);
                    }
                }

                int maxLimit = BorrowingPolicy.getMaxActiveBorrowingLimit();
                if ((loans + allocs) >= maxLimit) {
                    lastRejectionReason = "Student #" + studentId + " is at borrowing quota (" +
                            (loans + allocs) + "/" + maxLimit + ").";
                    return false;
                }

                return true;

            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Database error during candidate eligibility evaluation", e);
                lastRejectionReason = "Database evaluation error: " + e.getMessage();
                return false;
            }
        }

        @Override
        public String getLastRejectionReason() {
            return lastRejectionReason;
        }
    }
}
