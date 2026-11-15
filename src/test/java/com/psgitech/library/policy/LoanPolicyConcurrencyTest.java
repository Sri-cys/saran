package com.psgitech.library.policy;

import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.exception.StaleVersionConflictException;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.LoanPolicySettings;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.service.LoanPolicyService;
import com.psgitech.library.service.ReservationService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Multi-threaded concurrency tests verifying optimistic concurrency control on settings,
 * pessimistic row locking serialization during book issues, and absence of deadlocks (Feature 12).
 */
public class LoanPolicyConcurrencyTest {

    private LoanPolicyService policyService;
    private BorrowingService borrowingService;
    private ReservationService reservationService;

    private int testBookId;
    private int copy1Id;
    private int copy2Id;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test suite must run on MySQL", "MySQL", prod);
            assertTrue("Test schema name must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM notification_outbox");
                stmt.executeUpdate("DELETE FROM notifications WHERE recipient_user_id >= 200");
                stmt.executeUpdate("DELETE FROM book_loan_policy_state");
                stmt.executeUpdate("DELETE FROM loan_policy_changes");
                stmt.executeUpdate("DELETE FROM loan_policy_windows");
                stmt.executeUpdate("DELETE FROM borrow_transactions WHERE transaction_id > 2");
                stmt.executeUpdate("UPDATE borrow_transactions SET status = 'ACTIVE', return_date = NULL WHERE transaction_id = 1");
                stmt.executeUpdate("UPDATE borrow_transactions SET status = 'RETURNED' WHERE transaction_id = 2");
                stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1");
                stmt.executeUpdate("UPDATE loan_policy_settings SET enabled = TRUE, enter_threshold = 4, exit_threshold = 1, high_demand_days = 5, version = 1 WHERE id = 1");
            }

            // Create test book
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO books (title, author, isbn, category, department, publisher, publication_year, is_archived) " +
                            "VALUES ('Concurrency Policy Book', 'Dr. Thread', '978-0-999999-11-1', 'Computer Science', 'CSE', 'Tech Press', 2026, FALSE)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    testBookId = rs.getInt(1);
                }
            }

            // Create 2 available copies
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO book_copies (book_id, copy_code, status) VALUES (?, ?, 'AVAILABLE')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, testBookId);
                ps.setString(2, "TEST-CONC-C1");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    copy1Id = rs.getInt(1);
                }

                ps.setInt(1, testBookId);
                ps.setString(2, "TEST-CONC-C2");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    copy2Id = rs.getInt(1);
                }
            }

            // Ensure test students 201..210 exist
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO users (user_id, name, email, password_hash, role, register_number, department, `year`, active) " +
                            "VALUES (?, ?, ?, 'hash', 'STUDENT', ?, 'CSE', 3, TRUE)")) {
                for (int s = 201; s <= 210; s++) {
                    ps.setInt(1, s);
                    ps.setString(2, "Concurrent Student " + s);
                    ps.setString(3, "cstudent" + s + "@psgitech.ac.in");
                    ps.setString(4, "715523105" + s);
                    ps.executeUpdate();
                }
            }
        }

        this.policyService = new LoanPolicyService();
        BorrowRepository borrowRepo = new BorrowRepository();
        UserRepository userRepo = new UserRepository();
        BookRepository bookRepo = new BookRepository();
        this.borrowingService = new BorrowingService(borrowRepo, userRepo, bookRepo);
        this.reservationService = new ReservationService();
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
    public void testConcurrentOptimisticSettingsUpdates_ExactlyOneWins() throws Exception {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int adminId = 10 + i;
            final int proposedDays = 5 + (i % 3);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    LoanPolicySettings settings = new LoanPolicySettings(true, 5, 2, proposedDays, 2, adminId, null);
                    policyService.updateSettings(settings, 1, adminId, "Admin " + adminId);
                    successCount.incrementAndGet();
                } catch (StaleVersionConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release all threads at once
        assertTrue("All threads must finish within timeout", doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals("Exactly one thread must succeed in optimistic update", 1, successCount.get());
        assertEquals("Remaining threads must encounter version conflict (409)", threadCount - 1, conflictCount.get());

        // Verify version in DB is exactly 2
        LoanPolicySettings finalSettings = policyService.getSettings();
        assertEquals(2, finalSettings.getVersion());

        // Verify audit log has exactly 1 record
        assertEquals(1, policyService.getChangeHistory(1, 10).getTotalCount());
    }

    @Test
    public void testConcurrentCollections_SerializeUnderRowLockWithoutDeadlock() throws Exception {
        // Setup 4 waiting reservations for other students -> Demand = 4 (high demand threshold)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO reservations (book_id, student_id, status, reservation_date) VALUES (?, ?, 'WAITING', CURDATE())")) {
            for (int i = 0; i < 4; i++) {
                ps.setInt(1, testBookId);
                ps.setInt(2, 203 + i);
                ps.executeUpdate();
            }
        }

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // Thread 1: Issue copy 1 to student 201
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                BorrowTransaction tx1 = borrowingService.issueBook("715523105201", copy1Id, 2);
                if (tx1 != null && tx1.getStatus() == BorrowStatus.ACTIVE) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Issue copy 2 to student 202
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                BorrowTransaction tx2 = borrowingService.issueBook("715523105202", copy2Id, 2);
                if (tx2 != null && tx2.getStatus() == BorrowStatus.ACTIVE) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue("Both issues must finish within timeout without deadlocks", doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals("Both concurrent checkouts must succeed under book row lock", 2, successCount.get());
    }

    @Test
    public void testConcurrentMultiCopyIssues_ConsistentPolicyState() throws Exception {
        // Setup 4 waiting reservations for other students (205..208) -> Demand = 4
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO reservations (book_id, student_id, status, reservation_date) VALUES (?, ?, 'WAITING', CURDATE())")) {
            for (int i = 0; i < 4; i++) {
                ps.setInt(1, testBookId);
                ps.setInt(2, 205 + i);
                ps.executeUpdate();
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger highDemandCount = new AtomicInteger(0);

        // Thread 1: Issue copy 1 to student 201
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                BorrowTransaction tx = borrowingService.issueBook("715523105201", copy1Id, 2);
                if (tx != null && "HIGH_DEMAND".equals(tx.getPolicyCode()) && tx.getLoanDays() == 5) {
                    highDemandCount.incrementAndGet();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Issue copy 2 to student 202
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                BorrowTransaction tx = borrowingService.issueBook("715523105202", copy2Id, 2);
                if (tx != null && "HIGH_DEMAND".equals(tx.getPolicyCode()) && tx.getLoanDays() == 5) {
                    highDemandCount.incrementAndGet();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue("Both concurrent checkouts must finish without deadlock", doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals("Both issues must consistently receive High Demand 5-day loans", 2, highDemandCount.get());
    }
}
