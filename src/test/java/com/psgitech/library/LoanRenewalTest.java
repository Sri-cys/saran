package com.psgitech.library;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.InvalidBorrowingOperationException;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.ReservationRepository;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.Assert.*;

public class LoanRenewalTest {

    private BorrowingService borrowingService;
    private BorrowRepository borrowRepository;

    @Before
    public void setUp() {
        BorrowingPolicy.resetToDefaults();
        this.borrowRepository = new BorrowRepository();
        this.borrowingService = new BorrowingService();
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
            stmt.executeUpdate("UPDATE borrow_transactions SET status = 'ACTIVE', return_date = NULL, renewal_count = 0, last_renewed_at = NULL WHERE transaction_id = 1");
            stmt.executeUpdate("UPDATE borrow_transactions SET status = 'RETURNED' WHERE transaction_id = 2");
            stmt.executeUpdate("UPDATE book_copies SET status = 'AVAILABLE' WHERE copy_id != 1");
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1");
            stmt.executeUpdate("UPDATE users SET active = TRUE WHERE user_id IN (4, 5)");
        } catch (Exception ignored) {}
    }

    @Test
    public void testSuccessfulLoanRenewalExtendsDueDateBy14Days() {
        // Transaction 1 is an active loan for student 4 on book 1, copy 1
        LocalDate today = LocalDate.now();
        LocalDate initialDue = today.plusDays(14);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE borrow_transactions SET due_date = ?, renewal_count = 0 WHERE transaction_id = 1")) {
            ps.setDate(1, Date.valueOf(initialDue));
            ps.executeUpdate();
        } catch (Exception e) {
            fail("Failed to setup initial due date: " + e.getMessage());
        }

        BorrowTransaction renewed = borrowingService.renewLoan(1, 4, 4, Role.STUDENT);
        assertNotNull(renewed);
        assertEquals(1, renewed.getRenewalCount());
        assertEquals(initialDue.plusDays(14), renewed.getDueDate());
    }

    @Test
    public void testSecondRenewalAttemptRejected() {
        // First renewal
        borrowingService.renewLoan(1, 4, 4, Role.STUDENT);

        // Second renewal attempt should fail
        try {
            borrowingService.renewLoan(1, 4, 4, Role.STUDENT);
            fail("Expected exception for second renewal attempt");
        } catch (InvalidBorrowingOperationException e) {
            assertTrue(e.getMessage().contains("maximum allowed number of times"));
        }
    }

    @Test
    public void testOverdueLoanRenewalRejected() {
        // Set due date to 2 days ago
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE borrow_transactions SET due_date = ? WHERE transaction_id = 1")) {
            ps.setDate(1, Date.valueOf(LocalDate.now().minusDays(2)));
            ps.executeUpdate();
        } catch (Exception e) {
            fail("Failed to set overdue date: " + e.getMessage());
        }

        try {
            borrowingService.renewLoan(1, 4, 4, Role.STUDENT);
            fail("Expected exception renewing overdue book");
        } catch (InvalidBorrowingOperationException e) {
            assertTrue(e.getMessage().contains("Overdue loans cannot be renewed"));
        }
    }

    @Test
    public void testRenewalRejectedWhenWaitingReservationExists() {
        // Mark all copies of book 1 as BORROWED so reservation queue opens
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = 1");
        } catch (Exception e) {
            fail("Failed to setup borrowed copies: " + e.getMessage());
        }

        // Create waiting reservation for book 1 by student 5
        ReservationRepository reservationRepository = new ReservationRepository();
        reservationRepository.createReservationTransaction(5, 1);

        try {
            borrowingService.renewLoan(1, 4, 4, Role.STUDENT);
            fail("Expected exception renewing book with waiting reservations");
        } catch (InvalidBorrowingOperationException e) {
            assertTrue(e.getMessage().contains("reservation queue"));
        }
    }

    @Test
    public void testRenewalRejectedWhenUnpaidFinesExist() {
        // Insert unpaid fine for student 4
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO fines (student_id, borrow_transaction_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, outstanding_amount, status, created_at) " +
                     "VALUES (4, 1, 1, 1, 3, 3, 2.00, 6.00, 0.00, 6.00, 'UNPAID', NOW())")) {
            ps.executeUpdate();
        } catch (Exception e) {
            fail("Failed to insert fine: " + e.getMessage());
        }

        try {
            borrowingService.renewLoan(1, 4, 4, Role.STUDENT);
            fail("Expected exception renewing with unpaid fines");
        } catch (InvalidBorrowingOperationException e) {
            assertTrue(e.getMessage().contains("unpaid fines"));
        }
    }
}
