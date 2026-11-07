package com.psgitech.library;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.AllocationStatus;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.ReservationStatus;
import com.psgitech.library.exception.InvalidAllocationException;
import com.psgitech.library.model.AllocationRecord;
import com.psgitech.library.model.AllocationResult;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.repository.AllocationRepository;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.ReservationRepository;
import com.psgitech.library.service.AllocationService;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.Assert.*;

public class AllocationExpiryTest {

    private AllocationService allocationService;
    private BorrowingService borrowingService;
    private BorrowRepository borrowRepository;
    private AllocationRepository allocationRepository;
    private ReservationRepository reservationRepository;
    private BookRepository bookRepository;

    @Before
    public void setUp() {
        BorrowingPolicy.resetToDefaults();
        this.allocationRepository = new AllocationRepository();
        this.reservationRepository = new ReservationRepository();
        this.allocationService = new AllocationService();
        this.borrowRepository = new BorrowRepository();
        this.borrowingService = new BorrowingService();
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
            stmt.executeUpdate("DELETE FROM loan_renewals");
            stmt.executeUpdate("DELETE FROM book_interchanges");
            stmt.executeUpdate("DELETE FROM payment_audit_log");
            stmt.executeUpdate("DELETE FROM fine_adjustments");
            stmt.executeUpdate("DELETE FROM payments");
            stmt.executeUpdate("DELETE FROM fines");
            stmt.executeUpdate("DELETE FROM allocations");
            stmt.executeUpdate("DELETE FROM reservations");
            stmt.executeUpdate("DELETE FROM borrow_transactions WHERE transaction_id > 2");
            stmt.executeUpdate("UPDATE book_copies SET status = 'AVAILABLE' WHERE copy_id != 1");
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1");
            stmt.executeUpdate("UPDATE users SET active = TRUE WHERE user_id IN (4, 5, 6)");
        } catch (Exception ignored) {}
    }

    @Test
    public void testValidateCollectionRejectsExpiredAllocation() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;
        int studentId = 5;

        // 1. Issue and mark all copies borrowed so reservation triggers allocation on return
        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
        }

        // 2. Student 5 places reservation
        reservationRepository.createReservationTransaction(studentId, bookId);

        // 3. Return book -> copy 5 automatically allocated to student 5
        borrowingService.returnBook(tx.getTransactionId(), staffId);
        AllocationRecord alloc = allocationRepository.findActiveByCopy(copyId);
        assertNotNull(alloc);
        assertEquals(studentId, alloc.getStudentId());

        // 4. Manually age the allocation to expired (1 hour ago)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE allocations SET expires_at = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE allocation_id = ?")) {
            ps.setInt(1, alloc.getAllocationId());
            ps.executeUpdate();
        }

        // 5. Attempt collection -> must throw InvalidAllocationException (mapped to HTTP 409 Conflict)
        try (Connection conn = DBConnection.getConnection()) {
            allocationService.validateCollection(conn, copyId, studentId);
            fail("Expected InvalidAllocationException for expired collection");
        } catch (InvalidAllocationException e) {
            assertTrue(e.getMessage().contains("expired"));
        }
    }

    @Test
    public void testExpireAndReallocateOffersToNextStudentInQueue() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;
        int firstStudentId = 5;
        int nextStudentId = 6;

        // 1. Setup active loan and mark book copies borrowed
        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
        }

        // 2. Student 5 and Student 6 reserve in FIFO order
        Reservation res1 = reservationRepository.createReservationTransaction(firstStudentId, bookId);
        Reservation res2 = reservationRepository.createReservationTransaction(nextStudentId, bookId);

        // 3. Return book -> copy 5 allocated to Student 5; Student 6 remains WAITING
        borrowingService.returnBook(tx.getTransactionId(), staffId);
        AllocationRecord alloc1 = allocationRepository.findActiveByCopy(copyId);
        assertNotNull(alloc1);
        assertEquals(firstStudentId, alloc1.getStudentId());

        // 4. Age allocation 1 to expired
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE allocations SET expires_at = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE allocation_id = ?")) {
            ps.setInt(1, alloc1.getAllocationId());
            ps.executeUpdate();
        }

        // 5. Trigger automatic expiry and reallocation
        AllocationResult outcome1 = allocationService.expireAndReallocate(alloc1.getAllocationId());
        assertNotNull(outcome1);
        assertTrue(outcome1.isAllocated());

        // Verify allocation 1 is EXPIRED and points to successor
        AllocationRecord oldAlloc = allocationRepository.findById(alloc1.getAllocationId());
        assertEquals(AllocationStatus.EXPIRED, oldAlloc.getStatus());
        assertNotNull(oldAlloc.getExpiredAt());
        assertTrue(oldAlloc.getSuccessorAllocationId() != null && oldAlloc.getSuccessorAllocationId() > 0);

        // Verify reservation 1 is EXPIRED
        Reservation oldRes = reservationRepository.findById(res1.getReservationId());
        assertEquals(ReservationStatus.EXPIRED, oldRes.getStatus());

        // Verify next student's reservation is now ALLOCATED
        Reservation nextRes = reservationRepository.findById(res2.getReservationId());
        assertEquals(ReservationStatus.ALLOCATED, nextRes.getStatus());

        // Verify physical copy remains ALLOCATED
        assertEquals(CopyStatus.ALLOCATED, bookRepository.findCopyById(copyId).getStatus());
    }

    @Test
    public void testExpireAndReallocateRestoresCopyToAvailableWhenNoStudentsWaiting() throws Exception {
        int bookId = 2;
        int copyId = 5;
        int staffId = 2;
        int studentId = 5;

        // 1. Setup active loan and mark book copies borrowed
        BorrowTransaction tx = borrowRepository.issueBookTransaction(4, bookId, copyId, LocalDate.now(), LocalDate.now().plusDays(14), staffId);
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = " + bookId);
        }

        // 2. Student 5 reserves
        Reservation res = reservationRepository.createReservationTransaction(studentId, bookId);

        // 3. Return book -> copy 5 allocated to Student 5
        borrowingService.returnBook(tx.getTransactionId(), staffId);
        AllocationRecord alloc = allocationRepository.findActiveByCopy(copyId);
        assertNotNull(alloc);

        // 4. Age allocation to expired
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE allocations SET expires_at = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE allocation_id = ?")) {
            ps.setInt(1, alloc.getAllocationId());
            ps.executeUpdate();
        }

        // 5. Trigger expiry (queue is empty) -> copy restored to AVAILABLE
        AllocationResult outcome2 = allocationService.expireAndReallocate(alloc.getAllocationId());
        assertNotNull(outcome2);
        assertEquals(AllocationResult.Outcome.MADE_AVAILABLE, outcome2.getOutcome());

        // Verify old allocation is EXPIRED
        AllocationRecord oldAlloc = allocationRepository.findById(alloc.getAllocationId());
        assertEquals(AllocationStatus.EXPIRED, oldAlloc.getStatus());

        // Verify old reservation is EXPIRED
        Reservation oldRes = reservationRepository.findById(res.getReservationId());
        assertEquals(ReservationStatus.EXPIRED, oldRes.getStatus());

        // Verify physical copy returned to AVAILABLE
        assertEquals(CopyStatus.AVAILABLE, bookRepository.findCopyById(copyId).getStatus());
    }
}
