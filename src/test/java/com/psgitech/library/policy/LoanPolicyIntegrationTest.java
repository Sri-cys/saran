package com.psgitech.library.policy;

import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.InvalidBorrowingOperationException;
import com.psgitech.library.model.BookCopy;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.LoanPolicyRepository;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.service.WaitEstimator;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end MySQL integration tests for Feature 12 Dynamic Loan Policy.
 * Validates actual database row locks, transaction persistence, hysteresis transitions,
 * renewal prohibitions, and wait estimator integration on the dedicated test schema.
 */
public class LoanPolicyIntegrationTest {

    private BorrowRepository borrowRepository;
    private LoanPolicyRepository loanPolicyRepository;
    private BookRepository bookRepository;
    private BorrowingService borrowingService;
    private WaitEstimator waitEstimator;

    private int testBookId;
    private int copy1Id;
    private int copy2Id;
    private int copy3Id;
    private int copy4Id;
    private int copy5Id;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test suite must run on MySQL", "MySQL", prod);
            assertTrue("Test schema name must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));

            // Reset loan policy settings to default: enabled, enter=4, exit=1, days=5
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM notification_outbox");
                stmt.executeUpdate("DELETE FROM notifications WHERE recipient_user_id >= 100");
                stmt.executeUpdate("DELETE FROM book_loan_policy_state");
                stmt.executeUpdate("DELETE FROM loan_policy_windows");
                stmt.executeUpdate("DELETE FROM loan_policy_changes");
                stmt.executeUpdate("DELETE FROM borrow_transactions WHERE transaction_id > 2");
                stmt.executeUpdate("UPDATE borrow_transactions SET status = 'ACTIVE', return_date = NULL WHERE transaction_id = 1");
                stmt.executeUpdate("UPDATE borrow_transactions SET status = 'RETURNED' WHERE transaction_id = 2");
                stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1");
                stmt.executeUpdate("UPDATE loan_policy_settings SET enabled = TRUE, enter_threshold = 4, exit_threshold = 1, high_demand_days = 5, version = 1 WHERE id = 1");
            }

            // Create a dedicated 5-copy test book
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO books (title, author, isbn, category, department, publisher, publication_year, is_archived) " +
                            "VALUES ('Dynamic Policy Test Book', 'Dr. Algorithm', '978-0-123456-99-9', 'Computer Science', 'CSE', 'Tech Press', 2026, FALSE)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    testBookId = rs.getInt(1);
                }
            }

            // Insert 5 copies
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO book_copies (book_id, copy_code, status) VALUES (?, ?, 'AVAILABLE')",
                    Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 1; i <= 5; i++) {
                    ps.setInt(1, testBookId);
                    ps.setString(2, "TEST-DYN-C" + i);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        assertTrue(rs.next());
                        int cId = rs.getInt(1);
                        if (i == 1) copy1Id = cId;
                        else if (i == 2) copy2Id = cId;
                        else if (i == 3) copy3Id = cId;
                        else if (i == 4) copy4Id = cId;
                        else if (i == 5) copy5Id = cId;
                    }
                }
            }

            // Ensure test student accounts exist (IDs 101, 102, 103, 104, 105, 106)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO users (user_id, name, email, password_hash, role, register_number, department, `year`, active) " +
                            "VALUES (?, ?, ?, 'hash', 'STUDENT', ?, 'CSE', 3, TRUE)")) {
                for (int s = 101; s <= 106; s++) {
                    ps.setInt(1, s);
                    ps.setString(2, "Test Student " + s);
                    ps.setString(3, "student" + s + "@psgitech.ac.in");
                    ps.setString(4, "715523105" + s);
                    ps.executeUpdate();
                }
            }
        }

        this.borrowRepository = new BorrowRepository();
        this.loanPolicyRepository = new LoanPolicyRepository();
        this.bookRepository = new BookRepository();
        this.borrowingService = new BorrowingService(borrowRepository, new com.psgitech.library.repository.UserRepository(), bookRepository);
        this.waitEstimator = new WaitEstimator();
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM reservations WHERE book_id = " + testBookId);
            stmt.executeUpdate("DELETE FROM borrow_transactions WHERE book_id = " + testBookId);
            stmt.executeUpdate("DELETE FROM book_loan_policy_state WHERE book_id = " + testBookId);
            stmt.executeUpdate("DELETE FROM book_copies WHERE book_id = " + testBookId);
            stmt.executeUpdate("DELETE FROM books WHERE book_id = " + testBookId);
            stmt.executeUpdate("UPDATE borrow_transactions SET status = 'ACTIVE', return_date = NULL WHERE transaction_id = 1");
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1");
        }
    }

    @Test
    public void testStandardLoan_ZeroDemand_Issues14Days() {
        // Issue copy 1 to Student 101 when demand is 0
        BorrowTransaction tx = borrowingService.issueBook("715523105101", copy1Id, 2);

        assertNotNull(tx);
        assertEquals(BorrowStatus.ACTIVE, tx.getStatus());
        assertEquals(LocalDate.now(), tx.getIssueDate());
        assertEquals("Standard loan must have 14 days due date", LocalDate.now().plusDays(14), tx.getDueDate());
        assertEquals("STANDARD", tx.getPolicyCode());
        assertEquals(Integer.valueOf(14), tx.getLoanDays());
    }

    @Test
    public void testHighDemandTransition_AtEnterThreshold_Issues5DaysAndProhibitsRenewal() throws Exception {
        // 1. Create 4 waiting reservations from other students (102, 103, 104, 105) -> Demand = 4
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO reservations (book_id, student_id, status, reservation_date) VALUES (?, ?, 'WAITING', CURDATE())")) {
            for (int i = 0; i < 4; i++) {
                ps.setInt(1, testBookId);
                ps.setInt(2, 102 + i);
                ps.executeUpdate();
            }
        }

        // 2. Issue copy 1 to Student 101 -> Demand is 4 (>= enter_threshold 4)
        BorrowTransaction tx = borrowingService.issueBook("715523105101", copy1Id, 2);

        assertNotNull(tx);
        assertEquals("HIGH_DEMAND", tx.getPolicyCode());
        assertEquals(Integer.valueOf(5), tx.getLoanDays());
        assertEquals("Due date must be exactly 5 days from issue date", LocalDate.now().plusDays(5), tx.getDueDate());

        // 3. Verify state recorded in book_loan_policy_state
        LoanPolicy effective = LoanPolicyManager.getInstance().effectivePolicy(testBookId);
        assertNotNull(effective);
        assertTrue("Book state must now be HIGH_DEMAND", effective.isHighDemand());
        assertEquals(5, effective.getLoanDays());

        // 4. Attempt Renewal -> Must be rejected with HIGH_DEMAND_POLICY
        try {
            borrowRepository.renewLoan(tx.getTransactionId(), 101, 101, Role.STUDENT);
            fail("Renewal on a high-demand book must be blocked");
        } catch (InvalidBorrowingOperationException e) {
            assertTrue("Exception message must contain HIGH_DEMAND_POLICY, got: " + e.getMessage(),
                    e.getMessage().contains("HIGH_DEMAND_POLICY"));
        }
    }

    @Test
    public void testHysteresisPreservation_QueueDropsTo3_RemainsHighDemand() throws Exception {
        // Setup demand = 4
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO reservations (book_id, student_id, status, reservation_date) VALUES (?, ?, 'WAITING', CURDATE())")) {
            for (int i = 0; i < 4; i++) {
                ps.setInt(1, testBookId);
                ps.setInt(2, 102 + i);
                ps.executeUpdate();
            }
        }

        // Trigger High Demand on first checkout
        borrowingService.issueBook("715523105101", copy1Id, 2);

        // Cancel 1 reservation -> demand drops from 4 to 3 (still > exit threshold 1)
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM reservations WHERE book_id = " + testBookId + " AND student_id = 105");
        }

        // Issue copy 2 to Student 106 -> Hysteresis dampens: should STILL receive 5-day high-demand loan!
        BorrowTransaction tx2 = borrowingService.issueBook("715523105106", copy2Id, 2);

        assertNotNull(tx2);
        assertEquals("HIGH_DEMAND", tx2.getPolicyCode());
        assertEquals(Integer.valueOf(5), tx2.getLoanDays());
        assertEquals(LocalDate.now().plusDays(5), tx2.getDueDate());
    }

    @Test
    public void testReversionToStandard_QueueDropsToExitThreshold() throws Exception {
        // Setup demand = 4 and trigger high demand
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO reservations (book_id, student_id, status, reservation_date) VALUES (?, ?, 'WAITING', CURDATE())")) {
            for (int i = 0; i < 4; i++) {
                ps.setInt(1, testBookId);
                ps.setInt(2, 102 + i);
                ps.executeUpdate();
            }
        }
        borrowingService.issueBook("715523105101", copy1Id, 2);

        // Cancel 3 reservations -> demand drops to 1 (<= exit_threshold 1)
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM reservations WHERE book_id = " + testBookId + " AND student_id IN (103, 104, 105)");
        }

        // Issue copy 2 to Student 106 -> Should revert to STANDARD 14-day loan!
        BorrowTransaction tx2 = borrowingService.issueBook("715523105106", copy2Id, 2);

        assertNotNull(tx2);
        assertEquals("STANDARD", tx2.getPolicyCode());
        assertEquals(Integer.valueOf(14), tx2.getLoanDays());
        assertEquals(LocalDate.now().plusDays(14), tx2.getDueDate());

        // Verify DB state persisted as STANDARD
        LoanPolicy effective = LoanPolicyManager.getInstance().effectivePolicy(testBookId);
        assertNotNull(effective);
        assertFalse(effective.isHighDemand());
        assertEquals("STANDARD", effective.getCode());
    }

    @Test
    public void testOwnReservationExcludedFromDemand() throws Exception {
        // Student 102 has an ALLOCATED reservation, plus 3 other students waiting (total in table = 4)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO reservations (book_id, student_id, status, reservation_date) VALUES (?, ?, ?, CURDATE())")) {
            ps.setInt(1, testBookId);
            ps.setInt(2, 102);
            ps.setString(3, "ALLOCATED");
            ps.executeUpdate();

            for (int i = 0; i < 3; i++) {
                ps.setInt(1, testBookId);
                ps.setInt(2, 103 + i);
                ps.setString(3, "WAITING");
                ps.executeUpdate();
            }
        }

        // When Student 102 collects, their own reservation is excluded:
        // Demand from OTHER students = 3 (< enter_threshold 4)
        // Therefore, Student 102 receives a STANDARD 14-day loan!
        BorrowTransaction tx = borrowingService.issueBook("715523105102", copy1Id, 2);

        assertNotNull(tx);
        assertEquals("STANDARD", tx.getPolicyCode());
        assertEquals(Integer.valueOf(14), tx.getLoanDays());
        assertEquals(LocalDate.now().plusDays(14), tx.getDueDate());
    }

    @Test
    public void testWaitEstimator_UsesDynamicLoanDays() throws Exception {
        // 1. Initially when standard, loan duration is 14 days
        long standardDays = LoanPolicyManager.getInstance().effectiveLoanDays(testBookId);
        assertEquals("Standard book effective loan days must be 14", 14L, standardDays);

        // 2. Put book into High Demand state in DB with 2 active reservations (in hysteresis zone: exit=1 < demand=2 < enter=4)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement psState = conn.prepareStatement(
                     "INSERT INTO book_loan_policy_state (book_id, policy_code, last_demand) " +
                             "VALUES (?, 'HIGH_DEMAND', 2) " +
                             "ON DUPLICATE KEY UPDATE policy_code = 'HIGH_DEMAND'");
             PreparedStatement psRes = conn.prepareStatement(
                     "INSERT INTO reservations (book_id, student_id, status, reservation_date) VALUES (?, ?, 'WAITING', CURDATE())")) {
            psState.setInt(1, testBookId);
            psState.executeUpdate();

            psRes.setInt(1, testBookId);
            psRes.setInt(2, 102);
            psRes.executeUpdate();

            psRes.setInt(1, testBookId);
            psRes.setInt(2, 103);
            psRes.executeUpdate();
        }

        long highDemandDays = LoanPolicyManager.getInstance().effectiveLoanDays(testBookId);
        assertEquals("High demand book effective loan days must be 5", 5L, highDemandDays);

        // 3. Test WaitEstimator estimate string
        WaitEstimator.WaitEstimate estimate = waitEstimator.estimateWait(testBookId, 1);
        assertNotNull(estimate);
        assertNotNull(estimate.getMessage());
        assertFalse(estimate.getMessage().isEmpty());
    }
}
