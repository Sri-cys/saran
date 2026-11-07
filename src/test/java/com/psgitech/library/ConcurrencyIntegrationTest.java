package com.psgitech.library;

import com.psgitech.library.concurrency.ConcurrencyConfig;
import com.psgitech.library.concurrency.ConcurrencyManager;
import com.psgitech.library.concurrency.ConcurrencyMetrics;
import com.psgitech.library.concurrency.ConcurrencyOperation;
import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.PaymentStatus;
import com.psgitech.library.enums.ReservationStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.BookCopyUnavailableException;
import com.psgitech.library.exception.DuplicateReservationException;
import com.psgitech.library.exception.InvalidBorrowingOperationException;
import com.psgitech.library.exception.InvalidPaymentException;
import com.psgitech.library.exception.BorrowingLimitExceededException;
import com.psgitech.library.model.AllocationRecord;
import com.psgitech.library.model.AllocationResult;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.Payment;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.repository.AllocationRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.FineRepository;
import com.psgitech.library.repository.PaymentRepository;
import com.psgitech.library.repository.ReservationRepository;
import com.psgitech.library.service.AllocationService;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.service.PaymentService;
import com.psgitech.library.service.ReservationService;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.TransactionRetryHelper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Concurrency and multithreaded stress verification suite.
 * Strictly executes on real MySQL InnoDB against a schema ending with '_test' (psgitech_library_test).
 * Validates business rules under contention, race condition safety, deadlock recovery, and A/B baselines.
 */
public class ConcurrencyIntegrationTest {

    private static final String TEST_DB_URL = "jdbc:mysql://localhost:3306/psgitech_library_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    private static String originalDbUrl;

    private BorrowRepository borrowRepository;
    private ReservationRepository reservationRepository;
    private AllocationRepository allocationRepository;
    private FineRepository fineRepository;
    private PaymentRepository paymentRepository;
    private AllocationService allocationService;
    private BorrowingService borrowingService;
    private ReservationService reservationService;
    private PaymentService paymentService;

    @BeforeClass
    public static void beforeClass() {
        originalDbUrl = System.getProperty("db.url");
        System.setProperty("db.url", TEST_DB_URL);
        DBConnection.setForceFallback(false);
        DBConnection.resetJndiDataSource();
    }

    @AfterClass
    public static void afterClass() {
        if (originalDbUrl != null) {
            System.setProperty("db.url", originalDbUrl);
        } else {
            System.clearProperty("db.url");
        }
        DBConnection.setForceFallback(false);
        DBConnection.resetJndiDataSource();
    }

    @Before
    public void setUp() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            assertRealMySQLTestDatabase(conn);
        }

        borrowRepository = new BorrowRepository();
        reservationRepository = new ReservationRepository();
        allocationRepository = new AllocationRepository();
        fineRepository = new FineRepository();
        paymentRepository = new PaymentRepository();
        allocationService = new AllocationService();
        borrowingService = new BorrowingService();
        reservationService = new ReservationService();
        paymentService = new PaymentService();

        TransactionRetryHelper.addListener(ConcurrencyManager.getInstance().getMetrics());

        resetTestDatabaseFixtures();
    }

    @After
    public void tearDown() throws Exception {
        TransactionRetryHelper.removeListener(ConcurrencyManager.getInstance().getMetrics());
        try (Connection conn = DBConnection.getConnection()) {
            if (isRealMySQLTestDatabase(conn)) {
                cleanTestDatabaseFixtures();
            }
        }
    }

    private void assertRealMySQLTestDatabase(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String product = meta.getDatabaseProductName();
        String catalog = conn.getCatalog();
        boolean isMySQL = "MySQL".equalsIgnoreCase(product);
        boolean isTestCatalog = catalog != null && catalog.toLowerCase().endsWith("_test");

        Assume.assumeTrue("Skipping test: requires real MySQL with schema ending in _test (found: " +
                product + " / " + catalog + ")", isMySQL && isTestCatalog);
    }

    private boolean isRealMySQLTestDatabase(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String product = meta.getDatabaseProductName();
        String catalog = conn.getCatalog();
        return "MySQL".equalsIgnoreCase(product) && catalog != null && catalog.toLowerCase().endsWith("_test");
    }

    private void resetTestDatabaseFixtures() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            cleanTestDatabaseFixtures();

            // Insert 5 test students: IDs 201-205 (active loans = 0)
            stmt.executeUpdate("INSERT INTO users (user_id, name, email, password_hash, role, register_number, department, year, active) VALUES " +
                    "(201, 'Student Alpha', 'alpha@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104201', 'CSE', 3, TRUE)," +
                    "(202, 'Student Beta', 'beta@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104202', 'CSE', 3, TRUE)," +
                    "(203, 'Student Gamma', 'gamma@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104203', 'ECE', 3, TRUE)," +
                    "(204, 'Student Delta', 'delta@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104204', 'MECH', 2, TRUE)," +
                    "(205, 'Student Epsilon', 'epsilon@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104205', 'EEE', 2, TRUE)");

            // Insert Test Book Title (book_id = 99)
            stmt.executeUpdate("INSERT INTO books (book_id, title, author, isbn, publisher, publication_year, category, department, is_archived) VALUES " +
                    "(99, 'High-Performance Concurrency in Java', 'Brian Goetz', '9780321349606', 'Addison-Wesley', 2006, 'Computer Science', 'CSE', FALSE)");

            // Insert 5 Physical Copies (copy_ids 901-905)
            stmt.executeUpdate("INSERT INTO book_copies (copy_id, book_id, copy_code, status) VALUES " +
                    "(901, 99, 'PSG-BK99-C01', 'AVAILABLE')," +
                    "(902, 99, 'PSG-BK99-C02', 'AVAILABLE')," +
                    "(903, 99, 'PSG-BK99-C03', 'AVAILABLE')," +
                    "(904, 99, 'PSG-BK99-C04', 'AVAILABLE')," +
                    "(905, 99, 'PSG-BK99-C05', 'AVAILABLE')");
        }
    }

    private void cleanTestDatabaseFixtures() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM payment_audit_log WHERE fine_id IN (SELECT fine_id FROM fines WHERE book_id IN (95, 96, 97, 98, 99))");
            stmt.executeUpdate("DELETE FROM payments WHERE fine_id IN (SELECT fine_id FROM fines WHERE book_id IN (95, 96, 97, 98, 99))");
            stmt.executeUpdate("DELETE FROM fine_adjustments WHERE fine_id IN (SELECT fine_id FROM fines WHERE book_id IN (95, 96, 97, 98, 99))");
            stmt.executeUpdate("DELETE FROM fines WHERE book_id IN (95, 96, 97, 98, 99)");
            stmt.executeUpdate("DELETE FROM allocations WHERE book_id IN (95, 96, 97, 98, 99)");
            stmt.executeUpdate("DELETE FROM reservations WHERE book_id IN (95, 96, 97, 98, 99)");
            stmt.executeUpdate("DELETE FROM borrow_transactions WHERE book_id IN (95, 96, 97, 98, 99)");
            stmt.executeUpdate("DELETE FROM book_copies WHERE book_id IN (95, 96, 97, 98, 99)");
            stmt.executeUpdate("DELETE FROM books WHERE book_id IN (95, 96, 97, 98, 99)");
            stmt.executeUpdate("DELETE FROM users WHERE user_id BETWEEN 201 AND 250");
        }
    }

    // =========================================================================
    // 14.2 Simultaneous Borrowing Tests (Scenarios 18, 19, 22)
    // =========================================================================

    @Test
    public void testScenario18_SimultaneousIssueOfSameCopy() throws Exception {
        int threads = 10;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // 10 different students attempt to borrow copy 901 simultaneously
        for (int i = 0; i < threads; i++) {
            final int studentId = (i < 5) ? (201 + i) : (201 + (i % 5));
            exec.submit(() -> {
                try {
                    barrier.await();
                    borrowRepository.issueBookTransaction(studentId, 99, 901, LocalDate.now(), LocalDate.now().plusDays(14), 1);
                    successCount.incrementAndGet();
                } catch (BookCopyUnavailableException | InvalidBorrowingOperationException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        // Exactly 1 issue succeeds; the other 9 receive conflicts
        assertEquals("Exactly one issue must succeed", 1, successCount.get());
        assertEquals("Nine issues must fail with 409 conflict", 9, conflictCount.get());

        // Copy must end up in BORROWED status
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT status FROM book_copies WHERE copy_id = 901")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(CopyStatus.BORROWED.name(), rs.getString("status"));
            }
        }
    }

    @Test
    public void testScenario19_QuotaEnforcementUnderSimultaneousIssues() throws Exception {
        // Setup distinct book titles to test quota without triggering the same-book restriction
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO books (book_id, title, author, isbn, publisher, publication_year, category, department, is_archived) VALUES " +
                    "(97, 'Book 97', 'Author 97', '9780000000097', 'Pub', 2020, 'CS', 'CSE', FALSE)," +
                    "(98, 'Book 98', 'Author 98', '9780000000098', 'Pub', 2020, 'CS', 'CSE', FALSE)," +
                    "(96, 'Book 96', 'Author 96', '9780000000096', 'Pub', 2020, 'CS', 'CSE', FALSE)," +
                    "(95, 'Book 95', 'Author 95', '9780000000095', 'Pub', 2020, 'CS', 'CSE', FALSE)");
            stmt.executeUpdate("INSERT INTO book_copies (copy_id, book_id, copy_code, status) VALUES " +
                    "(917, 97, 'PSG-BK97-C01', 'AVAILABLE')," +
                    "(918, 98, 'PSG-BK98-C01', 'AVAILABLE')," +
                    "(916, 96, 'PSG-BK96-C01', 'AVAILABLE')," +
                    "(915, 95, 'PSG-BK95-C01', 'AVAILABLE')");
        }

        // Student 201 is issued 2 active loans first (limit is 3)
        borrowRepository.issueBookTransaction(201, 97, 917, LocalDate.now(), LocalDate.now().plusDays(14), 1);
        borrowRepository.issueBookTransaction(201, 98, 918, LocalDate.now(), LocalDate.now().plusDays(14), 1);

        assertEquals(2, borrowRepository.countActiveByStudentId(201));

        // 3 concurrent issue requests arrive for 3 DIFFERENT available books
        int attempts = 3;
        ExecutorService exec = Executors.newFixedThreadPool(attempts);
        CyclicBarrier barrier = new CyclicBarrier(attempts);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger quotaBlockedCount = new AtomicInteger(0);

        int[][] distinctBookCopies = {{99, 901}, {96, 916}, {95, 915}};
        for (int i = 0; i < attempts; i++) {
            final int targetBookId = distinctBookCopies[i][0];
            final int targetCopyId = distinctBookCopies[i][1];
            exec.submit(() -> {
                try {
                    barrier.await();
                    borrowRepository.issueBookTransaction(201, targetBookId, targetCopyId, LocalDate.now(), LocalDate.now().plusDays(14), 1);
                    successCount.incrementAndGet();
                } catch (BorrowingLimitExceededException | InvalidBorrowingOperationException e) {
                    quotaBlockedCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        // Exactly 1 more loan should succeed (reaching limit 3); remaining must fail
        assertEquals("Exactly one additional loan must succeed", 1, successCount.get());
        assertEquals("Remaining attempts must be blocked by quota", attempts - 1, quotaBlockedCount.get());
        assertEquals("Total active loans must equal limit 3", 3, borrowRepository.countActiveByStudentId(201));
    }

    @Test
    public void testScenario22_DoubleReturnOfSameTransactionSimultaneously() throws Exception {
        BorrowTransaction tx = borrowRepository.issueBookTransaction(201, 99, 901, LocalDate.now().minusDays(5), LocalDate.now().plusDays(9), 1);
        int txId = tx.getTransactionId();

        int threads = 2;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger returnSuccess = new AtomicInteger(0);
        AtomicInteger returnConflict = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                try {
                    barrier.await();
                    borrowRepository.returnBookTransaction(txId, 1, LocalDate.now());
                    returnSuccess.incrementAndGet();
                } catch (InvalidBorrowingOperationException e) {
                    returnConflict.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals("Exactly one return must succeed", 1, returnSuccess.get());
        assertEquals("Second concurrent return must fail with conflict", 1, returnConflict.get());

        // Verify status in DB
        BorrowTransaction finished = borrowRepository.findById(txId);
        assertNotNull(finished);
        assertEquals(BorrowStatus.RETURNED, finished.getStatus());
    }

    // =========================================================================
    // 14.3 Reservation, Allocation & Return Race Tests (Scenarios 24, 25, 27, 29)
    // =========================================================================

    @Test
    public void testScenario24_SimultaneousReservationsForSameBook() throws Exception {
        // Mark all 5 copies BORROWED so book is fully unavailable
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = 99");

            // Insert 15 additional test students (total 20 students)
            for (int id = 206; id <= 220; id++) {
                stmt.executeUpdate("INSERT INTO users (user_id, name, email, password_hash, role, register_number, department, year, active) VALUES " +
                        "(" + id + ", 'Student " + id + "', 's" + id + "@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104" + id + "', 'CSE', 3, TRUE)");
            }
        }

        int studentCount = 20;
        ExecutorService exec = Executors.newFixedThreadPool(studentCount);
        CyclicBarrier barrier = new CyclicBarrier(studentCount);
        List<Reservation> created = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < studentCount; i++) {
            final int studentId = 201 + i;
            exec.submit(() -> {
                try {
                    barrier.await();
                    Reservation r = reservationRepository.createReservationTransaction(studentId, 99);
                    created.add(r);
                } catch (Exception e) {
                    failures.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }

        exec.shutdown();
        exec.awaitTermination(15, TimeUnit.SECONDS);

        assertEquals("Zero reservation failures expected", 0, failures.get());
        assertEquals("All 20 reservations must be created", studentCount, created.size());

        // Verify that distinct FIFO queue positions 1..20 were assigned
        List<Reservation> queue = reservationRepository.findQueueForBook(99);
        assertEquals(20, queue.size());
        for (int i = 0; i < queue.size(); i++) {
            assertEquals("Queue position must be consecutive FIFO", i + 1, queue.get(i).getQueuePosition());
        }
    }

    @Test
    public void testScenario27_ReturnVsReservationRaceRepeated50Times() throws Exception {
        // Repeat 50 times: A borrowed copy is returned concurrently with a new reservation placed on the same book
        int iterations = 50;
        for (int iter = 1; iter <= iterations; iter++) {
            // Setup: book has 0 available, copy 901 is BORROWED with an active loan for student 201
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM allocations WHERE book_id = 99");
                stmt.executeUpdate("DELETE FROM reservations WHERE book_id = 99");
                stmt.executeUpdate("DELETE FROM borrow_transactions WHERE book_id = 99");
                stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = 99");
                stmt.executeUpdate("UPDATE book_copies SET status = 'AVAILABLE' WHERE copy_id = 901");
            }

            BorrowTransaction tx = borrowRepository.issueBookTransaction(201, 99, 901, LocalDate.now().minusDays(2), LocalDate.now().plusDays(12), 1);
            int txId = tx.getTransactionId();

            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService exec = Executors.newFixedThreadPool(2);

            // Thread 1: Return copy 901
            exec.submit(() -> {
                try {
                    barrier.await();
                    borrowRepository.returnBookTransaction(txId, 1, LocalDate.now());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // Thread 2: Student 202 reserves book 99
            exec.submit(() -> {
                try {
                    barrier.await();
                    reservationRepository.createReservationTransaction(202, 99);
                } catch (Exception ignored) {
                    // If return finished first, reservation might fail because copy became available for direct loan
                }
            });

            exec.shutdown();
            exec.awaitTermination(10, TimeUnit.SECONDS);

            // Invariant Verification: No run may ever leave a WAITING reservation next to an unallocated AVAILABLE copy
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) FROM reservations r " +
                         "JOIN book_copies bc ON r.book_id = bc.book_id " +
                         "WHERE r.status = 'WAITING' AND bc.status = 'AVAILABLE' AND bc.book_id = 99")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int anomalyCount = rs.getInt(1);
                    assertEquals("Iteration " + iter + ": WAITING reservation found next to unallocated AVAILABLE copy!", 0, anomalyCount);
                }
            }
        }
    }

    @Test
    public void testScenario29_ForeignKeySharedLockProvocation() throws Exception {
        // Return of a copy while the same student concurrently reserves or issues that book
        // Tests FK S-lock interaction and confirms TransactionRetryHelper retries and completes all
        int trials = 10;
        for (int i = 0; i < trials; i++) {
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM allocations WHERE book_id = 99");
                stmt.executeUpdate("DELETE FROM reservations WHERE book_id = 99");
                stmt.executeUpdate("DELETE FROM borrow_transactions WHERE book_id = 99");
                stmt.executeUpdate("UPDATE book_copies SET status = 'AVAILABLE' WHERE book_id = 99");
            }

            BorrowTransaction tx = borrowRepository.issueBookTransaction(201, 99, 901, LocalDate.now().minusDays(1), LocalDate.now().plusDays(13), 1);
            int txId = tx.getTransactionId();

            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService exec = Executors.newFixedThreadPool(2);
            AtomicInteger completedCount = new AtomicInteger(0);

            // Thread 1: Return copy 901 (triggers allocation check which takes FK locks on users)
            exec.submit(() -> {
                try {
                    barrier.await();
                    borrowRepository.returnBookTransaction(txId, 1, LocalDate.now());
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // Thread 2: Same student (201) issues copy 902 of the same book
            exec.submit(() -> {
                try {
                    barrier.await();
                    borrowRepository.issueBookTransaction(201, 99, 902, LocalDate.now(), LocalDate.now().plusDays(14), 1);
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    // May throw if same-book restriction triggers after return
                    completedCount.incrementAndGet();
                }
            });

            exec.shutdown();
            exec.awaitTermination(10, TimeUnit.SECONDS);

            assertEquals("Both requests must cleanly complete without hung threads", 2, completedCount.get());
        }
    }

    // =========================================================================
    // 14.4 Fine & Payment Concurrency Tests (Scenario 32)
    // =========================================================================

    @Test
    public void testScenario32_ConcurrentVerificationOfSamePayment() throws Exception {
        int paymentId;
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO borrow_transactions (transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by) VALUES " +
                    "(8888, 201, 99, 901, '2026-10-01', '2026-10-15', '2026-10-20', 'RETURNED', 1, 1)");
            stmt.executeUpdate("INSERT INTO fines (fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status) VALUES " +
                    "(88, 8888, 201, 99, 901, 5, 5, 2.00, 10.00, 0.00, 0.00, 10.00, 'UNPAID')");
            stmt.executeUpdate("INSERT INTO payments (payment_id, fine_id, student_id, amount, method, status, payment_code, idempotency_key, reference_id, submitted_at) VALUES " +
                    "(888, 88, 201, 10.00, 'UPI_MANUAL', 'PENDING_VERIFICATION', 'PAY-TEST-32', 'idemp_test_32', '123456789012', CURRENT_TIMESTAMP)");
            paymentId = 888;
        }

        // 10 staff members verify the same payment simultaneously
        int threads = 10;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                try {
                    barrier.await();
                    paymentService.verifyPayment(paymentId, 1, Role.ADMIN);
                    successCount.incrementAndGet();
                } catch (InvalidPaymentException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals("Exactly 1 payment verification must succeed", 1, successCount.get());
        assertEquals("9 concurrent verifications must receive conflict (409)", 9, conflictCount.get());

        // Verify status is SUCCESS
        Payment updated = paymentRepository.findById(paymentId);
        assertNotNull(updated);
        assertEquals(PaymentStatus.SUCCESS, updated.getStatus());
    }

    // =========================================================================
    // 14.5 Forced Deadlock Counter Test
    // =========================================================================

    @Test
    public void testForcedDeadlockIncrementsRetryMetrics() throws Exception {
        ConcurrencyManager manager = ConcurrencyManager.getInstance();
        ConcurrencyMetrics metrics = manager.getMetrics();
        long deadlocksBefore = metrics.getDeadlockRetries();

        // Two transactions intentionally lock book 99 and user 201 in reverse order
        CyclicBarrier step1 = new CyclicBarrier(2);
        CyclicBarrier step2 = new CyclicBarrier(2);
        ExecutorService exec = Executors.newFixedThreadPool(2);

        AtomicInteger attemptA = new AtomicInteger(0);
        AtomicInteger attemptB = new AtomicInteger(0);

        // Thread A: locks book 99, then user 201
        exec.submit(() -> {
            try {
                TransactionRetryHelper.executeWithRetry(() -> {
                    int att = attemptA.incrementAndGet();
                    try (Connection conn = DBConnection.getConnection()) {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps = conn.prepareStatement("SELECT book_id FROM books WHERE book_id = 99 FOR UPDATE")) {
                            ps.executeQuery();
                        }
                        if (att == 1) {
                            try { step1.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
                        }

                        try (PreparedStatement ps2 = conn.prepareStatement("SELECT user_id FROM users WHERE user_id = 201 FOR UPDATE")) {
                            ps2.executeQuery();
                        }
                        conn.commit();
                        return null;
                    }
                }, "ForcedDeadlockA");
            } catch (Exception ignored) {}
        });

        // Thread B: locks user 201, then book 99
        exec.submit(() -> {
            try {
                TransactionRetryHelper.executeWithRetry(() -> {
                    int att = attemptB.incrementAndGet();
                    try (Connection conn = DBConnection.getConnection()) {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE user_id = 201 FOR UPDATE")) {
                            ps.executeQuery();
                        }
                        if (att == 1) {
                            try { step1.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
                        }

                        try (PreparedStatement ps2 = conn.prepareStatement("SELECT book_id FROM books WHERE book_id = 99 FOR UPDATE")) {
                            ps2.executeQuery();
                        }
                        conn.commit();
                        return null;
                    }
                }, "ForcedDeadlockB");
            } catch (Exception ignored) {}
        });

        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        long deadlocksAfter = metrics.getDeadlockRetries();
        assertTrue("Deadlock retries counter must increment when MySQL error 1213 occurs", deadlocksAfter > deadlocksBefore);
    }

    // =========================================================================
    // 14.6 Phase D A/B Baseline Performance Test
    // =========================================================================

    @Test
    public void testPhaseD_AB_BaselinePerformance() throws Exception {
        int totalRequests = 50;

        // Baseline A: Limiter Disabled (Kill switch concurrency.limiter.enabled=false)
        ConcurrencyConfig configDisabled = new ConcurrencyConfig(false, 12, 24, 3000L, 3, true);
        ConcurrencyManager managerDisabled = new ConcurrencyManager(configDisabled);
        ConcurrencyManager.setInstance(managerDisabled);

        long startA = System.currentTimeMillis();
        ExecutorService execA = Executors.newFixedThreadPool(totalRequests);
        CyclicBarrier barrierA = new CyclicBarrier(totalRequests);
        AtomicInteger errorsA = new AtomicInteger(0);
        AtomicInteger successA = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            final int id = i;
            execA.submit(() -> {
                try {
                    barrierA.await();
                    // Acquire admission ticket (pass-through when disabled)
                    managerDisabled.acquireTicket(ConcurrencyOperation.BOOK_CATALOG_MUTATION, null);
                    // Light DB mutation query
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement("UPDATE books SET updated_at = CURRENT_TIMESTAMP WHERE book_id = 99")) {
                        ps.executeUpdate();
                    }
                    successA.incrementAndGet();
                } catch (Exception e) {
                    errorsA.incrementAndGet();
                }
            });
        }
        execA.shutdown();
        execA.awaitTermination(15, TimeUnit.SECONDS);
        long durationA = System.currentTimeMillis() - startA;

        // Baseline B: Limiter Enabled (Capacity 12, Waiting Room 24)
        ConcurrencyConfig configEnabled = new ConcurrencyConfig(true, 12, 24, 3000L, 3, true);
        ConcurrencyManager managerEnabled = new ConcurrencyManager(configEnabled);
        ConcurrencyManager.setInstance(managerEnabled);

        long startB = System.currentTimeMillis();
        ExecutorService execB = Executors.newFixedThreadPool(totalRequests);
        CyclicBarrier barrierB = new CyclicBarrier(totalRequests);
        AtomicInteger errorsB = new AtomicInteger(0);
        AtomicInteger successB = new AtomicInteger(0);
        AtomicInteger fastFailed503 = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            final int id = i;
            execB.submit(() -> {
                try {
                    barrierB.await();
                    try (var ticket = managerEnabled.acquireTicket(ConcurrencyOperation.BOOK_CATALOG_MUTATION, null)) {
                        try (Connection conn = DBConnection.getConnection();
                             PreparedStatement ps = conn.prepareStatement("UPDATE books SET updated_at = CURRENT_TIMESTAMP WHERE book_id = 99")) {
                            ps.executeUpdate();
                        }
                        successB.incrementAndGet();
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("capacity")) {
                        fastFailed503.incrementAndGet();
                    } else {
                        errorsB.incrementAndGet();
                    }
                }
            });
        }
        execB.shutdown();
        execB.awaitTermination(15, TimeUnit.SECONDS);
        long durationB = System.currentTimeMillis() - startB;

        System.out.println("================================================================================");
        System.out.println("PHASE D A/B BASELINE PERFORMANCE BENCHMARK (50 Concurrent Requests, MySQL InnoDB)");
        System.out.println("================================================================================");
        System.out.printf("Condition A (Limiter Disabled) : %d ms | Success: %d | Errors: %d%n", durationA, successA.get(), errorsA.get());
        System.out.printf("Condition B (Limiter Enabled)  : %d ms | Success: %d | Fast-Failed 503: %d | Errors: %d%n", durationB, successB.get(), fastFailed503.get(), errorsB.get());
        System.out.println("================================================================================");

        assertEquals("Zero unexpected errors with limiter enabled", 0, errorsB.get());
        assertEquals("All requests accounted for", totalRequests, successB.get() + fastFailed503.get());
    }
}
