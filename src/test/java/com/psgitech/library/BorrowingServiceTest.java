package com.psgitech.library;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit & Integration Test Suite for Feature 3: Book Borrowing & Return Management.
 * Tests:
 * 1. Successful issue of available copy.
 * 2. Automatic 14-day due date calculation.
 * 3. Copy status transition to BORROWED.
 * 4. Prevention of issuing already BORROWED copy.
 * 5. Prevention of issuing to inactive student.
 * 6. Prevention of issuing to non-existent student.
 * 7. Enforcement of maximum borrowing limit (3 books).
 * 8. Enforcement of same-book restriction (preventing duplicate copy borrow).
 * 9. Successful return and copy restoration to AVAILABLE.
 * 10. Prevention of double-return on already RETURNED transaction.
 * 11. Overdue days dynamic calculation.
 * 12. Student eligibility and lookup API data consistency.
 */
public class BorrowingServiceTest {
    private BorrowingService borrowingService;
    private BorrowRepository borrowRepository;
    private UserRepository userRepository;
    private BookRepository bookRepository;

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        this.borrowRepository = new BorrowRepository();
        this.userRepository = new UserRepository();
        this.bookRepository = new BookRepository();
        this.borrowingService = new BorrowingService(borrowRepository, userRepository, bookRepository);
        BorrowingPolicy.resetToDefaults();
        resetDatabaseState();
    }

    @org.junit.After
    public void tearDown() {
        resetDatabaseState();
    }

    private void resetDatabaseState() {
        try (java.sql.Connection conn = DBConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
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
        } catch (java.sql.SQLException ignored) {}
    }

    @Test
    public void testIssueBook_Success() {
        // Ananya Ramesh (reg: 715523105012) borrows Book 4 Copy 1 (PSG-BK4-C01, copy_id 11)
        String regNo = "715523105012";
        int copyId = 11;
        int staffId = 2;

        BorrowTransaction existingActive = borrowRepository.findActiveByCopyId(copyId);
        if (existingActive != null) {
            borrowingService.returnBook(existingActive.getTransactionId(), staffId);
        }

        BorrowTransaction tx = borrowingService.issueBook(regNo, copyId, staffId);

        assertNotNull("Borrow transaction should not be null", tx);
        assertNotNull("Transaction ID should be generated", tx.getTransactionId());
        assertEquals("Status should be ACTIVE", BorrowStatus.ACTIVE, tx.getStatus());
        assertEquals("Issue date should be today", LocalDate.now(), tx.getIssueDate());
        assertEquals("Due date should be 14 days from issue", LocalDate.now().plusDays(14), tx.getDueDate());

        // Verify copy status in DB updated to BORROWED
        BookCopy copy = null;
        for (BookCopy c : bookRepository.findCopiesByBookId(tx.getBookId())) {
            if (c.getCopyId() == copyId) {
                copy = c;
                break;
            }
        }
        assertNotNull("Copy should exist", copy);
        assertEquals("Copy status should be BORROWED", CopyStatus.BORROWED, copy.getStatus());
    }

    @Test(expected = BookCopyUnavailableException.class)
    public void testIssueBook_CopyAlreadyBorrowed_ThrowsException() {
        // Copy 1 (PSG-BK1-C01) is already borrowed by Sivakathir
        String regNo = "715523105012"; // Ananya
        int copyId = 1; // Already BORROWED
        borrowingService.issueBook(regNo, copyId, 2);
    }

    @Test(expected = StudentNotEligibleException.class)
    public void testIssueBook_InactiveStudent_ThrowsException() {
        // Rahul Kumar (reg: 715521102030) is inactive (active = false)
        String inactiveRegNo = "715521102030";
        int copyId = 2; // Available copy
        borrowingService.issueBook(inactiveRegNo, copyId, 2);
    }

    @Test(expected = StudentNotFoundException.class)
    public void testIssueBook_NonExistentStudent_ThrowsException() {
        String fakeRegNo = "999999999999";
        int copyId = 2;
        borrowingService.issueBook(fakeRegNo, copyId, 2);
    }

    @Test
    public void testIssueBook_SameBookRestriction_ThrowsException() {
        // Sivakathir (715522104045) already has Book 1 Copy 1 borrowed.
        // Trying to borrow Book 1 Copy 2 should fail with InvalidBorrowingOperationException.
        String regNo = "715522104045";
        int secondCopyIdOfBook1 = 2; // Belong to Book 1

        try {
            borrowingService.issueBook(regNo, secondCopyIdOfBook1, 2);
            fail("Should have thrown InvalidBorrowingOperationException for same-book borrowing");
        } catch (InvalidBorrowingOperationException e) {
            assertTrue("Message should mention same textbook or already has an active copy",
                    e.getMessage().toLowerCase().contains("already has an active copy") ||
                    e.getMessage().toLowerCase().contains("multiple copies"));
        }
    }

    @Test
    public void testReturnBook_Success() {
        String regNo = "715523105012";
        int copyId = 14; // Book 5 Copy 1 (Operating System Concepts)
        int staffId = 2;

        Student s = userRepository.findByRegisterNumber(regNo);
        for (BorrowTransaction activeTx : borrowRepository.findActiveByStudentId(s.getUserId())) {
            borrowingService.returnBook(activeTx.getTransactionId(), staffId);
        }

        BorrowTransaction issued = borrowingService.issueBook(regNo, copyId, staffId);
        assertNotNull(issued);

        // Process Return
        BorrowTransaction returned = borrowingService.returnBook(issued.getTransactionId(), staffId);

        assertNotNull(returned);
        assertEquals(BorrowStatus.RETURNED, returned.getStatus());
        assertEquals(LocalDate.now(), returned.getReturnDate());

        // Verify copy status in DB restored to AVAILABLE
        BookCopy copy = null;
        for (BookCopy c : bookRepository.findCopiesByBookId(issued.getBookId())) {
            if (c.getCopyId() == copyId) {
                copy = c;
                break;
            }
        }
        assertNotNull(copy);
        assertEquals(CopyStatus.AVAILABLE, copy.getStatus());
    }

    @Test(expected = InvalidBorrowingOperationException.class)
    public void testReturnBook_AlreadyReturned_ThrowsException() {
        // Transaction 2 was seeded as RETURNED
        borrowingService.returnBook(2, 2);
    }

    @Test(expected = BorrowTransactionNotFoundException.class)
    public void testReturnBook_NonExistentTransaction_ThrowsException() {
        borrowingService.returnBook(999999, 2);
    }

    @Test
    public void testBorrowingLimit_Enforcement() {
        String regNo = "715523105012";
        int staffId = 2;

        // Ensure student has 0 active borrowings before setting quota limit to 1
        Student s = userRepository.findByRegisterNumber(regNo);
        for (BorrowTransaction activeTx : borrowRepository.findActiveByStudentId(s.getUserId())) {
            borrowingService.returnBook(activeTx.getTransactionId(), staffId);
        }

        BorrowingPolicy.setMaxActiveBorrowingLimit(1);

        try {
            // First book issue should succeed (reaches limit of 1)
            int copy14 = 14; // Book 5 Copy 1
            BorrowTransaction existing14 = borrowRepository.findActiveByCopyId(copy14);
            if (existing14 != null) borrowingService.returnBook(existing14.getTransactionId(), staffId);

            borrowingService.issueBook(regNo, copy14, staffId);

            // Second book issue must fail because student already has 1 active book and quota is 1
            int copy17 = 17; // Book 6 Copy 1
            BorrowTransaction existing17 = borrowRepository.findActiveByCopyId(copy17);
            if (existing17 != null) borrowingService.returnBook(existing17.getTransactionId(), staffId);

            borrowingService.issueBook(regNo, copy17, staffId);
            fail("Should have thrown BorrowingLimitExceededException");
        } catch (BorrowingLimitExceededException e) {
            assertTrue(e.getMessage().contains("maximum borrowing limit"));
        } finally {
            BorrowingPolicy.resetToDefaults();
        }
    }

    @Test
    public void testStudentLookup_DetailsAndCapacity() {
        String regNo = "715522104045"; // Sivakathir
        Map<String, Object> data = borrowingService.lookupStudentForBorrowing(regNo, 1);

        assertNotNull(data);
        assertEquals("Sivakathir S", data.get("studentName"));
        assertEquals("715522104045", data.get("registerNumber"));
        assertEquals("ACTIVE", data.get("accountStatus"));
        assertEquals(3, data.get("maxLimit"));
        assertTrue((int) data.get("activeBorrowings") >= 1);
        assertEquals(Boolean.TRUE, data.get("alreadyHasSameBook"));
    }

    @Test
    public void testOverdueDaysCalculation() {
        LocalDate today = LocalDate.now();
        LocalDate pastDue = today.minusDays(5);
        LocalDate futureDue = today.plusDays(5);

        assertEquals("5 days overdue", 5, BorrowingService.calculateOverdueDays(pastDue, today));
        assertEquals("Not overdue for future date", 0, BorrowingService.calculateOverdueDays(futureDue, today));
        assertEquals("Not overdue when due today", 0, BorrowingService.calculateOverdueDays(today, today));
    }

    @Test
    public void testBorrowBookForStudent_Success() {
        // Student ID 5 (Ananya Ramesh, reg: 715523105012) self-borrows Book 4 directly
        int studentUserId = 5;
        int bookId = 4;

        // Clean any active borrowings for student 5 on book 4
        for (BorrowTransaction activeTx : borrowRepository.findActiveByStudentId(studentUserId)) {
            if (activeTx.getBookId() == bookId) {
                borrowingService.returnBook(activeTx.getTransactionId(), 2);
            }
        }

        BorrowTransaction tx = borrowingService.borrowBookForStudent(studentUserId, bookId);

        assertNotNull("Borrow transaction must not be null", tx);
        assertEquals("Student ID should match", studentUserId, tx.getStudentId());
        assertEquals("Book ID should match", bookId, tx.getBookId());
        assertEquals("Transaction status should be ACTIVE", BorrowStatus.ACTIVE, tx.getStatus());
        assertEquals("Issue date should be today", LocalDate.now(), tx.getIssueDate());
        assertNotNull("Due date must be set", tx.getDueDate());
        assertTrue("Due date must be in the future", tx.getDueDate().isAfter(LocalDate.now()));
    }

    @Test
    public void testBorrowBookForStudent_SameBookHolding_ThrowsException() {
        // Sivakathir (userId 4) already holds an active loan for Book 1 Copy 1
        int studentUserId = 4;
        int bookId = 1;

        try {
            borrowingService.borrowBookForStudent(studentUserId, bookId);
            fail("Should have thrown InvalidBorrowingOperationException for borrowing the same book twice");
        } catch (InvalidBorrowingOperationException e) {
            assertTrue("Exception message should indicate active checkout of same title",
                    e.getMessage().toLowerCase().contains("already have an active checkout") ||
                    e.getMessage().toLowerCase().contains("multiple copies"));
        }
    }

    @Test
    public void testBorrowBookForStudent_FinesBlocking() {
        int studentUserId = 5;
        int bookId = 3;

        // Insert an unpaid fine for student 5
        try (java.sql.Connection conn = DBConnection.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO fines (borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, grace_period_days, original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type) " +
                     "VALUES (1, ?, 1, 1, 5, 5, 2.00, 0, 10.00, 0.00, 0.00, 10.00, 'UNPAID', 'OVERDUE')")) {
            stmt.setInt(1, studentUserId);
            stmt.executeUpdate();

            try {
                borrowingService.borrowBookForStudent(studentUserId, bookId);
                fail("Should have thrown StudentNotEligibleException due to unpaid fines");
            } catch (StudentNotEligibleException e) {
                assertTrue("Message should mention unpaid fines", e.getMessage().toLowerCase().contains("fines") || e.getMessage().toLowerCase().contains("dues"));
            }
        } catch (java.sql.SQLException e) {
            fail("Database setup failed: " + e.getMessage());
        }
    }
}
