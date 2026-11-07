package com.psgitech.library;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.AllocationStatus;
import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.ReservationStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.AllocationRepository;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.ReservationRepository;
import com.psgitech.library.service.AllocationService;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.service.ReservationService;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.TransactionRetryHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Comprehensive JUnit Test Suite for Feature 5: Automatic Book Allocation.
 * Covers Part 12 test scenarios and specific conditions (FIFO skip, desk collection,
 * eligibility re-validation, allocation release, unique constraints, and deadlock retry).
 */
public class AllocationServiceTest {

    private AllocationService allocationService;
    private BorrowingService borrowingService;
    private ReservationService reservationService;
    private BorrowRepository borrowRepository;
    private ReservationRepository reservationRepository;
    private AllocationRepository allocationRepository;
    private BookRepository bookRepository;

    @Before
    public void setUp() {
        BorrowingPolicy.resetToDefaults();
        this.allocationRepository = new AllocationRepository();
        this.reservationRepository = new ReservationRepository();
        this.allocationService = new AllocationService();
        this.borrowRepository = new BorrowRepository();
        this.borrowingService = new BorrowingService();
        this.reservationService = new ReservationService();
        this.bookRepository = new BookRepository();
        resetDatabaseState();
    }

    @After
    public void tearDown() {
        resetDatabaseState();
    }

    private void resetDatabaseState() {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM book_interchanges");
            stmt.executeUpdate("DELETE FROM payment_audit_log");
            stmt.executeUpdate("DELETE FROM fine_adjustments");
            stmt.executeUpdate("DELETE FROM payments");
            stmt.executeUpdate("DELETE FROM fines");
            stmt.executeUpdate("DELETE FROM allocations");
            stmt.executeUpdate("DELETE FROM borrow_transactions WHERE transaction_id > 2");
            stmt.executeUpdate("UPDATE borrow_transactions SET status = 'ACTIVE', return_date = NULL WHERE transaction_id = 1");
            stmt.executeUpdate("UPDATE borrow_transactions SET status = 'RETURNED' WHERE transaction_id = 2");
            stmt.executeUpdate("DELETE FROM reservations");
            stmt.executeUpdate("UPDATE book_copies SET status = 'AVAILABLE' WHERE copy_id != 1");
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1");
            stmt.executeUpdate("UPDATE users SET active = TRUE WHERE user_id IN (4, 5)");
            stmt.executeUpdate("UPDATE users SET active = FALSE WHERE user_id = 6");
        } catch (SQLException ignored) {}
    }

    /**
     * Scenario 1: Book returned with waiting reservations ->
     * copy becomes ALLOCATED (not AVAILABLE), first student allocated, second stays WAITING.
     */
    @Test
    public void testReturn_AllocatesToWaitingStudent_FIFO() throws Exception {
        int bookId = 2; // Clean Code
        int copyId = 5; // PSG-BK2-C01
        int staffId = 2;

        // 1. Mark copy as BORROWED under active transaction
        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        assertNotNull(tx);

        // 2. Mark remaining copies of book 2 as BORROWED so reservation queue opens
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
            stmt.executeUpdate("UPDATE users SET active = TRUE WHERE user_id = 6");
        }

        // 3. Students 5 and 6 reserve the book in FIFO order
        Reservation res5 = reservationRepository.createReservationTransaction(5, bookId);
        Reservation res6 = reservationRepository.createReservationTransaction(6, bookId);
        assertEquals(ReservationStatus.WAITING, res5.getStatus());
        assertEquals(ReservationStatus.WAITING, res6.getStatus());

        // 4. Return the copy of book 2
        BorrowTransaction returnTx = borrowingService.returnBook(tx.getTransactionId(), staffId);
        assertNotNull(returnTx);
        assertNotNull(returnTx.getAllocationResult());
        assertTrue(returnTx.getAllocationResult().isAllocated());

        // 5. Verify copy is ALLOCATED (NOT AVAILABLE)
        Book bookAfter = bookRepository.findById(bookId, true);
        assertEquals(0, bookAfter.getInventory().getAvailableCopies());
        assertTrue(bookAfter.getInventory().getAllocatedCopies() >= 1);

        // 6. Verify student 5's reservation is ALLOCATED, student 6 stays WAITING
        Reservation check5 = reservationRepository.findById(res5.getReservationId());
        Reservation check6 = reservationRepository.findById(res6.getReservationId());
        assertEquals(ReservationStatus.ALLOCATED, check5.getStatus());
        assertEquals(ReservationStatus.WAITING, check6.getStatus());

        // 7. Verify allocation record exists
        AllocationRecord alloc = allocationRepository.findActiveByCopy(copyId);
        assertNotNull(alloc);
        assertEquals(5, alloc.getStudentId());
        assertEquals(res5.getReservationId().intValue(), alloc.getReservationId());
        assertEquals(AllocationStatus.ALLOCATED, alloc.getStatus());
    }

    /**
     * Scenario 2: Book returned with empty queue -> copy becomes AVAILABLE.
     */
    @Test
    public void testReturn_EmptyQueue_TransitionsToAvailable() {
        int bookId = 3; // AI book
        int copyId = 8;
        int staffId = 2;

        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        BorrowTransaction returnTx = borrowingService.returnBook(tx.getTransactionId(), staffId);

        assertNotNull(returnTx);
        assertNotNull(returnTx.getAllocationResult());
        assertFalse(returnTx.getAllocationResult().isAllocated());
        assertEquals(AllocationResult.Outcome.MADE_AVAILABLE, returnTx.getAllocationResult().getOutcome());

        Book bookAfter = bookRepository.findById(bookId, false);
        assertTrue(bookAfter.getInventory().getAvailableCopies() >= 1);
    }

    /**
     * Scenario 3: First waiting student is inactive -> skipped; second eligible student gets allocated.
     */
    @Test
    public void testReturn_SkipsInactiveStudent() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;

        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
            stmt.executeUpdate("UPDATE users SET active = TRUE WHERE user_id = 6");
        }

        Reservation res5 = reservationRepository.createReservationTransaction(5, bookId);
        Reservation res6 = reservationRepository.createReservationTransaction(6, bookId);

        // Deactivate student 5
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE users SET active = FALSE WHERE user_id = 5");
        }

        // Return copy -> Candidate 5 is inactive, Candidate 6 is eligible
        BorrowTransaction returnTx = borrowingService.returnBook(tx.getTransactionId(), staffId);
        assertTrue(returnTx.getAllocationResult().isAllocated());

        // Candidate 5 remained WAITING (skipped untouched)
        Reservation check5 = reservationRepository.findById(res5.getReservationId());
        assertEquals(ReservationStatus.WAITING, check5.getStatus());

        // Candidate 6 was allocated
        Reservation check6 = reservationRepository.findById(res6.getReservationId());
        assertEquals(ReservationStatus.ALLOCATED, check6.getStatus());

        AllocationRecord alloc = allocationRepository.findActiveByCopy(copyId);
        assertNotNull(alloc);
        assertEquals(6, alloc.getStudentId());
    }

    /**
     * Scenario 4: First waiting student is at borrowing limit -> skipped; next student gets allocated.
     */
    @Test
    public void testReturn_SkipsStudentAtBorrowingLimit() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;

        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
            stmt.executeUpdate("UPDATE users SET active = TRUE WHERE user_id = 6");
        }

        Reservation res5 = reservationRepository.createReservationTransaction(5, bookId);
        Reservation res6 = reservationRepository.createReservationTransaction(6, bookId);

        // Give student 5 three active loans on other books (reaching max limit 3)
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by) " +
                    "VALUES (5, 7, 18, '2026-10-20', '2026-11-03', 'ACTIVE', 2), " +
                    "(5, 8, 20, '2026-10-20', '2026-11-03', 'ACTIVE', 2), " +
                    "(5, 9, 22, '2026-10-20', '2026-11-03', 'ACTIVE', 2)");
        }

        // Return copy
        BorrowTransaction returnTx = borrowingService.returnBook(tx.getTransactionId(), staffId);
        assertTrue(returnTx.getAllocationResult().isAllocated());

        // Candidate 5 remained WAITING
        Reservation check5 = reservationRepository.findById(res5.getReservationId());
        assertEquals(ReservationStatus.WAITING, check5.getStatus());

        // Candidate 6 allocated
        Reservation check6 = reservationRepository.findById(res6.getReservationId());
        assertEquals(ReservationStatus.ALLOCATED, check6.getStatus());
    }

    /**
     * Scenario 5: Desk Collection of allocated copy by allocated student ->
     * copy becomes BORROWED, allocation COLLECTED, reservation FULFILLED.
     */
    @Test
    public void testCollection_Success() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;

        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
        }
        Reservation res5 = reservationRepository.createReservationTransaction(5, bookId);
        borrowingService.returnBook(tx.getTransactionId(), staffId);

        AllocationRecord initialAlloc = allocationRepository.findActiveByCopy(copyId);
        assertNotNull(initialAlloc);
        int allocId = initialAlloc.getAllocationId();

        // Student 5 collects the book at circulation desk
        BorrowTransaction collectedTx = borrowingService.issueBook("715523105012", copyId, staffId); // Student 5 regNo
        assertNotNull(collectedTx);
        assertEquals(BorrowStatus.ACTIVE, collectedTx.getStatus());

        // Allocation marked COLLECTED
        AllocationRecord checkAlloc = allocationRepository.findById(allocId);
        assertNotNull(checkAlloc);
        assertEquals(AllocationStatus.COLLECTED, checkAlloc.getStatus());
        assertNotNull(checkAlloc.getCollectedAt());
        assertNotNull(checkAlloc.getBorrowTransactionId());

        // Reservation marked FULFILLED
        Reservation checkRes = reservationRepository.findById(res5.getReservationId());
        assertEquals(ReservationStatus.FULFILLED, checkRes.getStatus());
    }

    /**
     * Scenario 6: Another student tries to collect an ALLOCATED copy -> 409 Conflict.
     */
    @Test
    public void testCollection_DifferentStudent_Rejected409() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;

        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
        }
        reservationRepository.createReservationTransaction(5, bookId);
        borrowingService.returnBook(tx.getTransactionId(), staffId);

        // Student 4 (715522104045) attempts to checkout copy 5 allocated to Student 5
        try {
            borrowingService.issueBook("715522104045", copyId, staffId);
            fail("Expected InvalidAllocationException when issuing copy allocated to another student");
        } catch (InvalidAllocationException e) {
            assertTrue(e.getMessage().contains("held for another student"));
        }
    }

    /**
     * Scenario 7 (Condition 10): Collection re-validates eligibility at desk pickup.
     * If allocated student reached quota in the meantime, collection is rejected with 409.
     */
    @Test
    public void testCollection_RevalidatesEligibility_AtLimit_Rejected() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;

        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
        }
        reservationRepository.createReservationTransaction(5, bookId);
        borrowingService.returnBook(tx.getTransactionId(), staffId);

        // In the meantime, student 5 borrows 3 other books before collecting this one
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by) " +
                    "VALUES (5, 7, 18, '2026-10-20', '2026-11-03', 'ACTIVE', 2), " +
                    "(5, 8, 20, '2026-10-20', '2026-11-03', 'ACTIVE', 2), " +
                    "(5, 9, 22, '2026-10-20', '2026-11-03', 'ACTIVE', 2)");
        }

        // Student 5 tries to collect -> must fail re-validation with limit check
        try {
            borrowingService.issueBook("715523105012", copyId, staffId);
            fail("Expected exception due to student reaching quota at collection time");
        } catch (BorrowingLimitExceededException | InvalidAllocationException e) {
            assertTrue(e.getMessage().contains("maximum borrowing limit"));
        }

        // Allocation remains ALLOCATED (untouched)
        AllocationRecord checkAlloc = allocationRepository.findActiveByCopy(copyId);
        assertNotNull(checkAlloc);
        assertEquals(AllocationStatus.ALLOCATED, checkAlloc.getStatus());
    }

    /**
     * Scenario 8: Release allocation reallocates copy to next waiting student.
     */
    @Test
    public void testReleaseAllocation_ReallocatesToNextStudent() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;

        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
            stmt.executeUpdate("UPDATE users SET active = TRUE WHERE user_id = 6");
        }
        Reservation res5 = reservationRepository.createReservationTransaction(5, bookId);
        Reservation res6 = reservationRepository.createReservationTransaction(6, bookId);
        borrowingService.returnBook(tx.getTransactionId(), staffId);

        // Student 5 releases allocation
        Reservation cancelledRes = reservationService.cancelReservation(res5.getReservationId(), 5, Role.STUDENT);
        assertEquals(ReservationStatus.CANCELLED, cancelledRes.getStatus());
        assertNotNull(cancelledRes.getReleaseOutcome());
        assertEquals(AllocationResult.Outcome.ALLOCATED, cancelledRes.getReleaseOutcome().getOutcome());

        // Copy is still ALLOCATED, now held for Student 6
        Reservation check6 = reservationRepository.findById(res6.getReservationId());
        assertEquals(ReservationStatus.ALLOCATED, check6.getStatus());

        AllocationRecord alloc6 = allocationRepository.findActiveByCopy(copyId);
        assertNotNull(alloc6);
        assertEquals(6, alloc6.getStudentId());
    }

    /**
     * Scenario 9: Release allocation when queue is empty -> copy becomes AVAILABLE.
     */
    @Test
    public void testReleaseAllocation_QueueEmpty_TransitionsToAvailable() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;

        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
        }
        Reservation res5 = reservationRepository.createReservationTransaction(5, bookId);
        borrowingService.returnBook(tx.getTransactionId(), staffId);

        // Student 5 releases allocation (no other waiters)
        Reservation cancelledRes = reservationService.cancelReservation(res5.getReservationId(), 5, Role.STUDENT);
        assertEquals(ReservationStatus.CANCELLED, cancelledRes.getStatus());
        assertEquals(AllocationResult.Outcome.MADE_AVAILABLE, cancelledRes.getReleaseOutcome().getOutcome());

        BookCopy copy = bookRepository.findById(bookId, true).getInventory().getCopies()
                .stream().filter(c -> c.getCopyId() == copyId).findFirst().orElse(null);
        assertNotNull(copy);
        assertEquals(CopyStatus.AVAILABLE, copy.getStatus());
    }

    /**
     * Scenario 10: Archiving an ALLOCATED copy is rejected with clear message.
     */
    @Test
    public void testCopyArchival_AllocatedCopy_Blocked() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;

        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
        }
        reservationRepository.createReservationTransaction(5, bookId);
        borrowingService.returnBook(tx.getTransactionId(), staffId);

        // Attempt to archive allocated copy
        try {
            bookRepository.archiveCopy(copyId);
            fail("Expected InvalidBookOperationException when archiving ALLOCATED copy");
        } catch (InvalidBookOperationException e) {
            assertTrue(e.getMessage().contains("currently ALLOCATED"));
        }
    }

    /**
     * Scenario 11 (Condition 2): Verifies TransactionRetryHelper retry mechanism on deadlock.
     */
    @Test
    public void testDeadlockRetryHelper_ExecutesSuccessfully() {
        int[] attempts = {0};
        String result = TransactionRetryHelper.executeWithRetry(() -> {
            attempts[0]++;
            if (attempts[0] == 1) {
                // Simulate MySQL deadlock error 1213 on first attempt
                throw new SQLException("Deadlock found when trying to get lock", "40001", 1213);
            }
            return "SUCCESS_AFTER_RETRY";
        }, "testDeadlockRetry");

        assertEquals("SUCCESS_AFTER_RETRY", result);
        assertEquals(2, attempts[0]);
    }
}
