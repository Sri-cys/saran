package com.psgitech.library;

import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.CaseStatus;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.service.LossDamageService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Level 5 Concurrency and Race Condition Test Suite for Feature 14.
 * Validates thread safety, atomic row locks, and absence of race-induced anomalies:
 * 1. Dual concurrent assessment of the same reported loss: exactly 1 succeeds.
 * 2. Concurrent student choosePayment vs staff convertToPayment: exactly 1 fine created.
 * 3. Concurrent loss assessment vs return desk standard return: mutual exclusion.
 */
public class LostDamagedConcurrencyTest {

    private LossDamageService lossDamageService;
    private BorrowRepository borrowRepository;
    private BookRepository bookRepository;
    private BorrowingService borrowingService;

    private int studentId;
    private int bookId;
    private int copyId;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test must run on MySQL", "MySQL", prod);
            assertTrue("Schema must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));
        }

        this.lossDamageService = new LossDamageService();
        this.borrowRepository = new BorrowRepository();
        this.bookRepository = new BookRepository();
        this.borrowingService = new BorrowingService(borrowRepository, new com.psgitech.library.repository.UserRepository(), bookRepository);

        cleanFixtures();
        setupFixtures();
    }

    @After
    public void tearDown() {
        cleanFixtures();
    }

    private void cleanFixtures() {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM notifications WHERE recipient_user_id IN (SELECT user_id FROM users WHERE register_number LIKE '715523F14%')");
            stmt.executeUpdate("DELETE FROM payment_audit_log WHERE payment_id IN " +
                    "(SELECT payment_id FROM payments WHERE student_id IN (SELECT user_id FROM users WHERE register_number LIKE '715523F14%'))");
            stmt.executeUpdate("DELETE FROM payments WHERE student_id IN " +
                    "(SELECT user_id FROM users WHERE register_number LIKE '715523F14%')");
            stmt.executeUpdate("DELETE FROM loss_damage_case_events WHERE case_id IN " +
                    "(SELECT case_id FROM loss_damage_cases WHERE student_id IN (SELECT user_id FROM users WHERE register_number LIKE '715523F14%') OR book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-1414-%'))");
            stmt.executeUpdate("DELETE FROM loss_damage_cases WHERE student_id IN " +
                    "(SELECT user_id FROM users WHERE register_number LIKE '715523F14%') OR book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-1414-%')");
            stmt.executeUpdate("DELETE FROM fine_adjustments WHERE fine_id IN " +
                    "(SELECT fine_id FROM fines WHERE student_id IN (SELECT user_id FROM users WHERE register_number LIKE '715523F14%'))");
            stmt.executeUpdate("DELETE FROM fines WHERE student_id IN " +
                    "(SELECT user_id FROM users WHERE register_number LIKE '715523F14%')");
            stmt.executeUpdate("DELETE FROM book_interchanges WHERE from_student_id IN (SELECT user_id FROM users WHERE register_number LIKE '715523F14%') OR to_student_id IN (SELECT user_id FROM users WHERE register_number LIKE '715523F14%')");
            stmt.executeUpdate("DELETE FROM allocations WHERE student_id IN (SELECT user_id FROM users WHERE register_number LIKE '715523F14%') OR book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-1414-%')");
            stmt.executeUpdate("DELETE FROM reservations WHERE student_id IN (SELECT user_id FROM users WHERE register_number LIKE '715523F14%') OR book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-1414-%')");
            stmt.executeUpdate("DELETE FROM borrow_transactions WHERE student_id IN " +
                    "(SELECT user_id FROM users WHERE register_number LIKE '715523F14%') OR book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-1414-%')");
            stmt.executeUpdate("DELETE FROM book_copies WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-1414-%')");
            stmt.executeUpdate("DELETE FROM books WHERE isbn LIKE '978-0-1414-%'");
            stmt.executeUpdate("DELETE FROM users WHERE register_number LIKE '715523F14%'");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupFixtures() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            String insUser = "INSERT INTO users (name, email, password_hash, phone, role, register_number, department, `year`, active, must_change_password, email_verified) " +
                    "VALUES ('F14 Concurrency Student', 'f14_conc@psgitech.ac.in', '$2a$10$e8w.xeqzE6fWzrq8J/5YqOSGjP/pA8u8QZcQdG0Ccm8t41X5u8gCe', '9876543210', 'STUDENT', '715523F1499', 'CSE', 3, 1, 0, 1)";
            try (PreparedStatement ps = conn.prepareStatement(insUser, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    studentId = rs.getInt(1);
                }
            }

            String insBook = "INSERT INTO books (isbn, title, author, publisher, publication_year, category, department, replacement_price) " +
                    "VALUES ('978-0-1414-7777-3', 'F14 Concurrency in Practice', 'Brian Goetz', 'Addison-Wesley', 2020, 'CS', 'CSE', 900.00)";
            try (PreparedStatement ps = conn.prepareStatement(insBook, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    bookId = rs.getInt(1);
                }
            }

            String insCopy = "INSERT INTO book_copies (book_id, copy_code, status) VALUES (?, 'PSG-F14-BK3-C01', 'AVAILABLE')";
            try (PreparedStatement ps = conn.prepareStatement(insCopy, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, bookId);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    copyId = rs.getInt(1);
                }
            }
        }
    }

    private int issueBook() {
        BorrowTransaction tx = borrowingService.issueBook("715523F1499", copyId, 2);
        assertNotNull(tx);
        return tx.getTransactionId();
    }

    @Test
    public void testConcurrentDualAssessment_ExactlyOneSucceeds() throws Exception {
        int txId = issueBook();
        LossDamageCase reported = lossDamageService.reportLost(txId, studentId, "Lost on bus", studentId, "STUDENT");
        final int caseId = reported.getCaseId();

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int assessorId = (i == 0) ? 2 : 1; // Staff 2 vs Admin 1
            final String role = (i == 0) ? "LIBRARIAN" : "ADMIN";
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lossDamageService.assessLost(caseId, null, "Concurrent assessment", assessorId, role);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue("All threads should complete within timeout", completed);

        assertEquals("Exactly 1 assessment must succeed", 1, successCount.get());
        assertEquals("Exactly 1 assessment must fail", 1, failureCount.get());

        // Verify final state
        LossDamageCase finalCase = lossDamageService.getCaseById(caseId);
        assertEquals(CaseStatus.ASSESSED, finalCase.getStatus());

        BorrowTransaction tx = borrowRepository.findById(txId);
        assertEquals(BorrowStatus.LOST, tx.getStatus());

        BookCopy copy = bookRepository.findCopyById(copyId);
        assertEquals(CopyStatus.ARCHIVED, copy.getStatus());
    }

    @Test
    public void testConcurrentStudentChoosePaymentVsStaffConvert_ExactlyOneFineCreated() throws Exception {
        int txId = issueBook();
        LossDamageCase reported = lossDamageService.reportLost(txId, studentId, "Lost", studentId, "STUDENT");
        LossDamageCase assessed = lossDamageService.assessLost(reported.getCaseId(), null, "Assessed", 2, "LIBRARIAN");
        lossDamageService.chooseReplacement(assessed.getCaseId(), studentId, "STUDENT");
        final int caseId = assessed.getCaseId();

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);

        // Thread 1: Student chooses payment
        executor.submit(() -> {
            try {
                startLatch.await();
                lossDamageService.choosePayment(caseId, studentId, "STUDENT");
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Staff converts to payment
        executor.submit(() -> {
            try {
                startLatch.await();
                lossDamageService.convertToPayment(caseId, "Staff convert", 2, "LIBRARIAN");
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue("Threads should finish within timeout", completed);

        assertTrue("At least one transition succeeded", successCount.get() >= 1);

        // Verify case is PAYMENT_PENDING
        LossDamageCase finalCase = lossDamageService.getCaseById(caseId);
        assertEquals(CaseStatus.PAYMENT_PENDING, finalCase.getStatus());
        assertNotNull(finalCase.getFineId());

        // Invariant: Exactly ONE fine created in fines table for this transaction
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM fines WHERE borrow_transaction_id = ? AND fine_type = 'COMPENSATION'")) {
            ps.setInt(1, txId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Exactly ONE compensation fine must exist in database", 1, rs.getInt(1));
            }
        }
    }

    @Test
    public void testConcurrentAssessmentAndReturnDesk_MutualExclusion() throws Exception {
        int txId = issueBook();
        LossDamageCase reported = lossDamageService.reportLost(txId, studentId, "Lost", studentId, "STUDENT");
        final int caseId = reported.getCaseId();

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger assessSuccess = new AtomicInteger(0);
        AtomicInteger returnSuccess = new AtomicInteger(0);

        // Thread 1: Assess loss
        executor.submit(() -> {
            try {
                startLatch.await();
                lossDamageService.assessLost(caseId, null, "Lost confirmed", 2, "LIBRARIAN");
                assessSuccess.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Desk return
        executor.submit(() -> {
            try {
                startLatch.await();
                borrowRepository.returnBookTransaction(txId, 2, java.time.LocalDate.now());
                returnSuccess.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue("Both operations should finish within timeout", completed);

        // Mutual exclusion: exactly ONE succeeded, never both
        assertEquals("Either assess or return succeeded, but never both", 1, assessSuccess.get() + returnSuccess.get());

        // Verify borrow transaction status
        BorrowTransaction tx = borrowRepository.findById(txId);
        assertTrue("Status must be either LOST or RETURNED",
                tx.getStatus() == BorrowStatus.LOST || tx.getStatus() == BorrowStatus.RETURNED);
    }
}
