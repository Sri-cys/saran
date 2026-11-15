package com.psgitech.library.policy;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.LoanPolicySettings;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.service.LoanPolicyService;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * System invariant and static safety scan tests for Feature 12.
 * Strictly verifies:
 * 1. ZERO retroactive alteration of existing loan due dates.
 * 2. Static source code verification prohibiting UPDATE borrow_transactions SET due_date in Feature 12 classes.
 * 3. Preservation of 3-book borrowing limit and Rs. 2.00/day fine calculation invariants.
 */
public class LoanPolicyInvariantsTest {

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test suite must run on MySQL", "MySQL", prod);
            assertTrue("Test schema name must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));
        }
    }

    @Test
    public void testCodebaseSafetyScan_NoRetroactiveDueDateUpdatesInPolicyClasses() throws IOException {
        File policyPkg = new File("src/main/java/com/psgitech/library/policy");
        File repoFile = new File("src/main/java/com/psgitech/library/repository/LoanPolicyRepository.java");
        File serviceFile = new File("src/main/java/com/psgitech/library/service/LoanPolicyService.java");

        List<File> filesToScan = new ArrayList<>();
        if (policyPkg.exists() && policyPkg.isDirectory()) {
            File[] files = policyPkg.listFiles((dir, name) -> name.endsWith(".java"));
            if (files != null) {
                for (File f : files) filesToScan.add(f);
            }
        }
        if (repoFile.exists()) filesToScan.add(repoFile);
        if (serviceFile.exists()) filesToScan.add(serviceFile);

        assertFalse("Must find files to scan for invariants", filesToScan.isEmpty());

        for (File f : filesToScan) {
            String content = Files.readString(f.toPath()).toUpperCase();
            assertFalse(f.getName() + " must NOT contain UPDATE borrow_transactions SET due_date!",
                    content.contains("UPDATE BORROW_TRANSACTIONS") && content.contains("SET DUE_DATE"));
            assertFalse(f.getName() + " must NOT truncate or shorten existing loans!",
                    content.contains("SET DUE_DATE =") || content.contains("SET DUE_DATE="));
        }
    }

    @Test
    public void testExistingLoanDueDate_ImmunityToPolicyTransitions() throws Exception {
        int bookId;
        int copyId;
        int txId;
        LocalDate originalDueDate = LocalDate.now().plusDays(14);

        try (Connection conn = DBConnection.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM notification_outbox");
                stmt.executeUpdate("DELETE FROM book_loan_policy_state");
                stmt.executeUpdate("DELETE FROM loan_policy_changes");
                stmt.executeUpdate("DELETE FROM loan_policy_windows");
                stmt.executeUpdate("DELETE FROM books WHERE isbn = '978-0-888888-22-2'");
                stmt.executeUpdate("DELETE FROM borrow_transactions WHERE transaction_id > 2");
                stmt.executeUpdate("UPDATE borrow_transactions SET status = 'ACTIVE', return_date = NULL WHERE transaction_id = 1");
                stmt.executeUpdate("UPDATE borrow_transactions SET status = 'RETURNED' WHERE transaction_id = 2");
                stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1");
                stmt.executeUpdate("UPDATE loan_policy_settings SET enabled = TRUE, enter_threshold = 4, exit_threshold = 1, high_demand_days = 5, version = 1 WHERE id = 1");
            }

            // Create book and copy
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO books (title, author, isbn, category, department, publisher, publication_year, is_archived) " +
                            "VALUES ('Immunity Test Book', 'Invariant Author', '978-0-888888-22-2', 'CSE', 'CSE', 'Press', 2026, FALSE)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    bookId = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO book_copies (book_id, copy_code, status) VALUES (?, 'TEST-IMM-C1', 'BORROWED')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, bookId);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    copyId = rs.getInt(1);
                }
            }

            // Create existing active loan issued for standard 14 days
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO borrow_transactions (book_id, copy_id, student_id, issued_by, issue_date, due_date, status, policy_code, loan_days) " +
                            "VALUES (?, ?, 101, 2, CURDATE(), ?, 'ACTIVE', 'STANDARD', 14)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, bookId);
                ps.setInt(2, copyId);
                ps.setDate(3, java.sql.Date.valueOf(originalDueDate));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    txId = rs.getInt(1);
                }
            }
        }

        // Now simulate book transitioning into HIGH_DEMAND and admin modifying global settings
        LoanPolicyService service = new LoanPolicyService();
        service.updateSettings(new LoanPolicySettings(true, 3, 1, 3, 2, 1, null), 1, 1, "Admin");

        // Put book in HIGH_DEMAND state
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO book_loan_policy_state (book_id, policy_code, last_demand) VALUES (?, 'HIGH_DEMAND', 5) " +
                             "ON DUPLICATE KEY UPDATE policy_code = 'HIGH_DEMAND'")) {
            ps.setInt(1, bookId);
            ps.executeUpdate();
        }

        // Verify existing loan due date is COMPLETELY UNTOUCHED!
        BorrowRepository borrowRepo = new BorrowRepository();
        BorrowTransaction tx = borrowRepo.findById(txId);
        assertNotNull(tx);
        assertEquals("Existing loan due date must NEVER be shortened retroactively!",
                originalDueDate, tx.getDueDate());
        assertEquals("Existing loan policy_code remains what it was issued under",
                "STANDARD", tx.getPolicyCode());
        assertEquals(Integer.valueOf(14), tx.getLoanDays());
    }

    @org.junit.After
    public void tearDown() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM borrow_transactions WHERE copy_id IN (SELECT copy_id FROM book_copies WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-888888-22-2'))");
            stmt.executeUpdate("DELETE FROM book_loan_policy_state WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-888888-22-2')");
            stmt.executeUpdate("DELETE FROM book_copies WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-888888-22-2')");
            stmt.executeUpdate("DELETE FROM books WHERE isbn = '978-0-888888-22-2'");
            stmt.executeUpdate("UPDATE borrow_transactions SET status = 'ACTIVE', return_date = NULL WHERE transaction_id = 1");
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1");
        }
    }

    @Test
    public void testBorrowingInvariantsPreserved() {
        // Invariant: Max books allowed per student remains 3
        assertEquals("Maximum books allowed per student must remain 3", 3, BorrowingPolicy.DEFAULT_MAX_ACTIVE_BORROWINGS);

        // Invariant: Standard loan period remains 14 days
        assertEquals("Default standard borrowing duration must remain 14 days", 14, BorrowingPolicy.DEFAULT_BORROWING_DAYS);

        // Invariant: Daily overdue fine rate remains 2.00 INR
        assertEquals("Daily fine rate per overdue day must remain 2.00",
                new java.math.BigDecimal("2.00"), com.psgitech.library.config.FineConfig.DEFAULT_FINE_PER_DAY);
    }
}
