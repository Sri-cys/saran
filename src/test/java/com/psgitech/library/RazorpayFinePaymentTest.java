package com.psgitech.library;

import com.psgitech.library.config.FineConfig;
import com.psgitech.library.config.RazorpayConfig;
import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.enums.PaymentStatus;
import com.psgitech.library.exception.FineNotFoundException;
import com.psgitech.library.exception.InvalidPaymentException;
import com.psgitech.library.exception.PaymentInProgressException;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.model.Payment;
import com.psgitech.library.repository.FineRepository;
import com.psgitech.library.repository.PaymentRepository;
import com.psgitech.library.service.RazorpayPaymentService;
import com.psgitech.library.service.RazorpayService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Comprehensive Automated Test Suite covering all 11 required scenarios
 * for Razorpay Fine Management (Individual Payment + Pay All).
 */
public class RazorpayFinePaymentTest {

    private RazorpayPaymentService paymentService;
    private RazorpayService razorpayService;
    private FineRepository fineRepository;
    private PaymentRepository paymentRepository;

    private static final int STUDENT_1_ID = 4; // Student 1
    private static final int STUDENT_2_ID = 5; // Student 2

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        this.razorpayService = new RazorpayService();
        this.fineRepository = new FineRepository();
        this.paymentRepository = new PaymentRepository();
        this.paymentService = new RazorpayPaymentService();
        resetDatabaseState();
    }

    @After
    public void tearDown() {
        resetDatabaseState();
    }

    private void resetDatabaseState() {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM payment_fines");
            stmt.executeUpdate("DELETE FROM payment_audit_log");
            stmt.executeUpdate("DELETE FROM fine_adjustments");
            stmt.executeUpdate("DELETE FROM payments");
            stmt.executeUpdate("DELETE FROM fines");
            stmt.executeUpdate("DELETE FROM borrow_transactions WHERE transaction_id > 2");
            stmt.executeUpdate("UPDATE borrow_transactions SET status = 'ACTIVE', return_date = NULL WHERE transaction_id = 1");
            stmt.executeUpdate("UPDATE borrow_transactions SET status = 'RETURNED' WHERE transaction_id = 2");
            stmt.executeUpdate("UPDATE users SET active = TRUE WHERE user_id IN (4, 5)");
        } catch (SQLException ignored) {}
    }

    private int createTestBorrowTx(int studentId, int bookId, int copyId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by) " +
                             "VALUES (?, ?, ?, DATE_SUB(CURRENT_DATE, INTERVAL 20 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY), CURRENT_DATE, 'RETURNED', 2)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, studentId);
            ps.setInt(2, bookId);
            ps.setInt(3, copyId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to create test borrow tx");
    }

    private int insertTestFine(int studentId, int bookId, int copyId, BigDecimal amount, FineStatus status) throws SQLException {
        int txId = createTestBorrowTx(studentId, bookId, copyId);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO fines (borrow_transaction_id, student_id, book_id, copy_id, overdue_days, " +
                             "chargeable_days, fine_per_day, grace_period_days, original_amount, paid_amount, waived_amount, " +
                             "outstanding_amount, status, fine_type) " +
                             "VALUES (?, ?, ?, ?, 5, 5, 2.00, 0, ?, ?, 0.00, ?, ?, 'OVERDUE')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, txId);
            ps.setInt(2, studentId);
            ps.setInt(3, bookId);
            ps.setInt(4, copyId);
            ps.setBigDecimal(5, amount);

            BigDecimal paid = (status == FineStatus.PAID) ? amount : BigDecimal.ZERO;
            BigDecimal outstanding = (status == FineStatus.PAID) ? BigDecimal.ZERO : amount;

            ps.setBigDecimal(6, paid);
            ps.setBigDecimal(7, outstanding);
            ps.setString(8, status.name());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to create test fine");
    }

    /**
     * TEST 1: Single unpaid fine of ₹50 -> Checkout order created for exact amount of ₹50.
     */
    @Test
    public void test1_SingleUnpaidFine_CreatesOrderForExactAmount() throws SQLException {
        int fineId = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("50.00"), FineStatus.UNPAID);

        Map<String, Object> order = paymentService.createIndividualOrder(STUDENT_1_ID, fineId);

        assertNotNull("Order response should not be null", order);
        assertEquals("INDIVIDUAL", order.get("type"));
        assertEquals(new BigDecimal("50.00"), order.get("amount"));
        assertEquals(5000L, order.get("amountPaise"));
        assertEquals(RazorpayConfig.KEY_ID, order.get("keyId"));
        assertEquals(RazorpayConfig.CURRENCY, order.get("currency"));
        assertNotNull(order.get("orderId"));

        int paymentId = (Integer) order.get("paymentId");
        Payment payment = paymentRepository.findById(paymentId);
        assertNotNull(payment);
        assertEquals(PaymentStatus.INITIATED, payment.getStatus());
        assertEquals(new BigDecimal("50.00"), payment.getAmount());
        assertEquals(fineId, payment.getFineId());
    }

    /**
     * TEST 2: Multiple unpaid fines (₹50, ₹100, ₹75) -> Pay All displays ₹225, creates single order of ₹225.
     */
    @Test
    public void test2_MultipleUnpaidFines_PayAllCreatesSingleCombinedOrder() throws SQLException {
        int f1 = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("50.00"), FineStatus.UNPAID);
        int f2 = insertTestFine(STUDENT_1_ID, 2, 2, new BigDecimal("100.00"), FineStatus.UNPAID);
        int f3 = insertTestFine(STUDENT_1_ID, 3, 3, new BigDecimal("75.00"), FineStatus.UNPAID);

        Map<String, Object> order = paymentService.createPayAllOrder(STUDENT_1_ID, Arrays.asList(f1, f2, f3));

        assertNotNull("Bulk order response should not be null", order);
        assertEquals("BULK", order.get("type"));
        assertEquals(new BigDecimal("225.00"), order.get("amount"));
        assertEquals(22500L, order.get("amountPaise"));
        assertEquals(3, order.get("finesCount"));

        int paymentId = (Integer) order.get("paymentId");
        Payment payment = paymentRepository.findById(paymentId);
        assertNotNull(payment);
        assertEquals(PaymentStatus.INITIATED, payment.getStatus());
        assertEquals(new BigDecimal("225.00"), payment.getAmount());

        List<Integer> linkedFines = paymentRepository.findFineIdsByPaymentId(paymentId);
        assertEquals(3, linkedFines.size());
        assertTrue(linkedFines.contains(f1));
        assertTrue(linkedFines.contains(f2));
        assertTrue(linkedFines.contains(f3));
    }

    /**
     * TEST 3: 1 paid and 2 unpaid fines -> Pay All includes ONLY unpaid fines.
     */
    @Test
    public void test3_OnePaidAndTwoUnpaidFines_PayAllOnlyIncludesUnpaid() throws SQLException {
        int paidFine = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("50.00"), FineStatus.PAID);
        int unpaid1 = insertTestFine(STUDENT_1_ID, 2, 2, new BigDecimal("100.00"), FineStatus.UNPAID);
        int unpaid2 = insertTestFine(STUDENT_1_ID, 3, 3, new BigDecimal("75.00"), FineStatus.UNPAID);

        // When passing null list or all fines, only the unpaid should be included
        Map<String, Object> order = paymentService.createPayAllOrder(STUDENT_1_ID, Arrays.asList(paidFine, unpaid1, unpaid2));

        assertEquals("BULK", order.get("type"));
        assertEquals(new BigDecimal("175.00"), order.get("amount"));
        assertEquals(2, order.get("finesCount"));

        int paymentId = (Integer) order.get("paymentId");
        List<Integer> linkedFines = paymentRepository.findFineIdsByPaymentId(paymentId);
        assertEquals(2, linkedFines.size());
        assertFalse("Paid fine must NOT be included in Pay All order", linkedFines.contains(paidFine));
        assertTrue(linkedFines.contains(unpaid1));
        assertTrue(linkedFines.contains(unpaid2));
    }

    /**
     * TEST 4: All fines paid -> Pay Now and Pay All hidden / rejected with clear message.
     */
    @Test
    public void test4_AllFinesPaid_PayNowAndPayAllRejected() throws SQLException {
        int paidFine = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("50.00"), FineStatus.PAID);

        // Individual order creation on paid fine must fail
        try {
            paymentService.createIndividualOrder(STUDENT_1_ID, paidFine);
            fail("Expected InvalidPaymentException when creating order for already paid fine");
        } catch (InvalidPaymentException e) {
            assertTrue(e.getMessage().contains("already settled"));
        }

        // Pay All when no unpaid fines exist must fail
        try {
            paymentService.createPayAllOrder(STUDENT_1_ID, null);
            fail("Expected InvalidPaymentException when no outstanding fines exist for student");
        } catch (InvalidPaymentException e) {
            assertTrue(e.getMessage().contains("No outstanding fines found"));
        }
    }

    /**
     * TEST 5: Payment cancelled -> Fines remain UNPAID, balance preserved.
     */
    @Test
    public void test5_PaymentCancelled_FinesRemainUnpaidBalancePreserved() throws SQLException {
        int fineId = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("50.00"), FineStatus.UNPAID);

        Map<String, Object> order = paymentService.createIndividualOrder(STUDENT_1_ID, fineId);
        String orderId = (String) order.get("orderId");

        // Student cancels / closes Razorpay checkout modal
        paymentService.recordCancelledPayment(STUDENT_1_ID, orderId, "User closed checkout");

        // Fine remains UNPAID and outstanding balance untouched
        FineRecord fine = fineRepository.findById(fineId);
        assertEquals(FineStatus.UNPAID, fine.getStatus());
        assertEquals(new BigDecimal("50.00"), fine.getOutstandingAmount());

        // Payment status transitioned to CANCELLED
        Payment payment = paymentRepository.findByPaymentCode(orderId);
        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());

        // Student can immediately create a new payment attempt
        Map<String, Object> newOrder = paymentService.createIndividualOrder(STUDENT_1_ID, fineId);
        assertNotNull(newOrder.get("orderId"));
    }

    /**
     * TEST 6: Payment fails -> Fine remains outstanding, retry possible.
     */
    @Test
    public void test6_PaymentFails_FineRemainsOutstandingAndRetryPossible() throws SQLException {
        int fineId = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("50.00"), FineStatus.UNPAID);

        Map<String, Object> order = paymentService.createIndividualOrder(STUDENT_1_ID, fineId);
        String orderId = (String) order.get("orderId");

        // Gateway reports error
        paymentService.recordFailedPayment(STUDENT_1_ID, orderId, "Card declined by bank");

        FineRecord fine = fineRepository.findById(fineId);
        assertEquals(FineStatus.UNPAID, fine.getStatus());
        assertEquals(new BigDecimal("50.00"), fine.getOutstandingAmount());

        Payment payment = paymentRepository.findByPaymentCode(orderId);
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("Card declined by bank", payment.getRejectionReason());

        // Retry is immediately permitted
        Map<String, Object> retryOrder = paymentService.createIndividualOrder(STUDENT_1_ID, fineId);
        assertNotNull(retryOrder.get("orderId"));
    }

    /**
     * TEST 7: Payment succeeds -> Selected fines marked PAID, payment status SUCCESS.
     */
    @Test
    public void test7_PaymentSucceeds_FinesMarkedPaidAndPaymentStatusSuccess() throws SQLException {
        int f1 = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("50.00"), FineStatus.UNPAID);
        int f2 = insertTestFine(STUDENT_1_ID, 2, 2, new BigDecimal("100.00"), FineStatus.UNPAID);

        Map<String, Object> order = paymentService.createPayAllOrder(STUDENT_1_ID, Arrays.asList(f1, f2));
        String orderId = (String) order.get("orderId");
        String paymentId = "pay_test_" + System.currentTimeMillis();
        String validSignature = razorpayService.generateSignature(orderId, paymentId);

        // Backend verification callback
        Payment result = paymentService.verifyAndCapturePayment(STUDENT_1_ID, orderId, paymentId, validSignature);

        assertNotNull(result);
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        assertEquals(paymentId, result.getReferenceId());
        assertNotNull(result.getReceiptNumber());
        assertTrue(result.getReceiptNumber().startsWith("RCPT-"));

        // Both fines marked PAID atomically
        FineRecord fine1 = fineRepository.findById(f1);
        FineRecord fine2 = fineRepository.findById(f2);
        assertEquals(FineStatus.PAID, fine1.getStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), fine1.getOutstandingAmount());
        assertEquals(new BigDecimal("50.00"), fine1.getPaidAmount());

        assertEquals(FineStatus.PAID, fine2.getStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), fine2.getOutstandingAmount());
        assertEquals(new BigDecimal("100.00"), fine2.getPaidAmount());

        // Student fine dues are now 0
        assertFalse(fineRepository.hasOutstandingFines(STUDENT_1_ID));
    }

    /**
     * TEST 8: Student attempts to pay another student's fine -> Backend rejects with Access Denied (403).
     */
    @Test
    public void test8_StudentAttemptsToPayAnotherStudentsFine_RejectedWithAccessDenied() throws SQLException {
        int fineStudent2 = insertTestFine(STUDENT_2_ID, 1, 1, new BigDecimal("50.00"), FineStatus.UNPAID);

        try {
            // Student 1 attempts to create order for Student 2's fine
            paymentService.createIndividualOrder(STUDENT_1_ID, fineStudent2);
            fail("Expected InvalidPaymentException when paying another student's fine");
        } catch (InvalidPaymentException e) {
            assertTrue(e.getMessage().contains("Access Denied"));
        }

        try {
            // Student 1 attempts to include Student 2's fine in Pay All
            paymentService.createPayAllOrder(STUDENT_1_ID, Collections.singletonList(fineStudent2));
            fail("Expected InvalidPaymentException when including another student's fine in bulk order");
        } catch (InvalidPaymentException e) {
            assertTrue(e.getMessage().contains("Access Denied"));
        }
    }

    /**
     * TEST 9: Rapid clicks / duplicate submissions -> Prevented by in-flight active payment check.
     */
    @Test
    public void test9_RapidClicksAndDuplicateSubmissions_Prevented() throws SQLException {
        int fineId = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("50.00"), FineStatus.UNPAID);

        // First click
        Map<String, Object> order1 = paymentService.createIndividualOrder(STUDENT_1_ID, fineId);
        assertNotNull(order1.get("orderId"));

        // Immediate rapid second click while first is still in progress
        try {
            paymentService.createIndividualOrder(STUDENT_1_ID, fineId);
            fail("Expected PaymentInProgressException on rapid duplicate submission");
        } catch (PaymentInProgressException e) {
            assertTrue(e.getMessage().contains("active payment is currently in progress"));
        }
    }

    /**
     * TEST 10: Simultaneous payment attempts on same fine -> Prevented, cannot double settle.
     */
    @Test
    public void test10_SimultaneousPaymentAttemptsOnSameFine_CannotDoubleSettle() throws SQLException {
        int fineId = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("50.00"), FineStatus.UNPAID);

        Map<String, Object> order = paymentService.createIndividualOrder(STUDENT_1_ID, fineId);
        String orderId = (String) order.get("orderId");
        String paymentId = "pay_test_concurrency_" + System.currentTimeMillis();
        String sig = razorpayService.generateSignature(orderId, paymentId);

        // First capture settles the fine
        Payment p1 = paymentService.verifyAndCapturePayment(STUDENT_1_ID, orderId, paymentId, sig);
        assertEquals(PaymentStatus.SUCCESS, p1.getStatus());

        // Second simultaneous / replay capture for same order
        Payment p2 = paymentService.verifyAndCapturePayment(STUDENT_1_ID, orderId, paymentId, sig);
        assertEquals(PaymentStatus.SUCCESS, p2.getStatus());

        // Verify fine was settled only once, paid amount is exactly 50.00 (not 100.00)
        FineRecord fine = fineRepository.findById(fineId);
        assertEquals(FineStatus.PAID, fine.getStatus());
        assertEquals(new BigDecimal("50.00"), fine.getPaidAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), fine.getOutstandingAmount());
    }

    /**
     * TEST 11: Fine changes after order created -> Discrepancy detected and safely handled.
     */
    @Test
    public void test11_FineChangesAfterOrderCreated_DiscrepancyDetectedAndSafelyHandled() throws SQLException {
        int fineId = insertTestFine(STUDENT_1_ID, 1, 1, new BigDecimal("100.00"), FineStatus.UNPAID);

        // Order created for 100.00
        Map<String, Object> order = paymentService.createIndividualOrder(STUDENT_1_ID, fineId);
        String orderId = (String) order.get("orderId");

        // Before payment is captured, administration waives 60.00, leaving 40.00 outstanding
        try (Connection conn = DBConnection.getConnection()) {
            fineRepository.updateFineAmountsAndStatus(conn, fineId, BigDecimal.ZERO,
                    new BigDecimal("60.00"), new BigDecimal("40.00"), FineStatus.PARTIALLY_PAID);
        }

        String paymentId = "pay_disc_" + System.currentTimeMillis();
        String sig = razorpayService.generateSignature(orderId, paymentId);

        // Capture payment: service detects discrepancy, only settles actual outstanding (40.00)
        Payment captured = paymentService.verifyAndCapturePayment(STUDENT_1_ID, orderId, paymentId, sig);
        assertNotNull(captured);
        assertEquals(PaymentStatus.SUCCESS, captured.getStatus());

        FineRecord fine = fineRepository.findById(fineId);
        assertEquals(FineStatus.PAID, fine.getStatus());
        assertEquals(new BigDecimal("40.00"), fine.getPaidAmount());
        assertEquals(new BigDecimal("60.00"), fine.getWaivedAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), fine.getOutstandingAmount());
        // Invariant holds: paid (40) + waived (60) == original (100)
        assertEquals(fine.getOriginalAmount(), fine.getPaidAmount().add(fine.getWaivedAmount()));
    }
}
