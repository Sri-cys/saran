package com.psgitech.library;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.enums.PaymentStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.FineRepository;
import com.psgitech.library.repository.PaymentRepository;
import com.psgitech.library.service.*;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Comprehensive JUnit 4 Test Suite for Feature 6: Fine Calculation and UPI Payment.
 * Tests:
 * 1. On-time return incurs no fine (no fine row created).
 * 2. 1 day late incurs exactly INR 2.00 (rate D1, grace D2).
 * 3. 5 days and 30 days late fine calculations.
 * 4. Boundary calendar-day calculation using injected Clock (Asia/Kolkata).
 * 5. Idempotent return prevents duplicate fine creation.
 * 6. Single active in-flight payment constraint (no duplicate concurrent attempts).
 * 7. Stale INITIATED payment superseded after 30 minutes.
 * 8. Duplicate UPI reference / UTR rejection (409 Conflict).
 * 9. Staff verification marks payment SUCCESS, updates fine, and generates receipt.
 * 10. Staff rejection requires mandatory reason.
 * 11. Admin-only fine waiver enforcing invariant: Original = Paid + Waived + Outstanding.
 * 12. Admin refund of SUCCESS payment restores fine balance and voids receipt.
 * 13. Outstanding fine (> INR 0) blocks book issue and collection (Decision D9).
 */
public class FineServiceTest {

    private FineService fineService;
    private PaymentService paymentService;
    private BorrowingService borrowingService;
    private AllocationService allocationService;
    private FineCalculator fineCalculator;
    private FineRepository fineRepository;
    private PaymentRepository paymentRepository;
    private BorrowRepository borrowRepository;

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        BorrowingPolicy.resetToDefaults();
        this.fineRepository = new FineRepository();
        this.paymentRepository = new PaymentRepository();
        this.borrowRepository = new BorrowRepository();
        this.fineCalculator = new FineCalculator();
        this.fineService = new FineService();
        this.paymentService = new PaymentService();
        this.borrowingService = new BorrowingService();
        this.allocationService = new AllocationService();
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

    private int createTestBorrowTx(int studentId, int bookId, int copyId, LocalDate dueDate) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by) " +
                             "VALUES (?, ?, ?, ?, ?, 'RETURNED', 2)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, studentId);
            ps.setInt(2, bookId);
            ps.setInt(3, copyId);
            ps.setDate(4, java.sql.Date.valueOf(dueDate.minusDays(14)));
            ps.setDate(5, java.sql.Date.valueOf(dueDate));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to create test borrow tx");
    }

    /**
     * Test 1: On-time return produces 0 overdue days and no fine record.
     */
    @Test
    public void testOnTimeReturnNoFine() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 20);
        LocalDate returnDate = LocalDate.of(2026, 10, 20);

        try (Connection conn = DBConnection.getConnection()) {
            FineRecord record = fineService.createFineForReturn(conn, 1, 4, 1, 1, dueDate, returnDate);
            assertNull("On-time return should produce no fine record", record);
        }

        List<FineRecord> studentFines = fineService.getFinesByStudent(4);
        assertTrue("No fine records should exist for on-time return", studentFines.isEmpty());
    }

    /**
     * Test 2: 1 day late incurs exactly INR 2.00 fine (rate D1 = INR 2.00, grace D2 = 0).
     */
    @Test
    public void testOneDayLateFineCalculation() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 20);
        LocalDate returnDate = LocalDate.of(2026, 10, 21); // 1 day late
        int txId = createTestBorrowTx(4, 1, 1, dueDate);

        FineRecord record;
        try (Connection conn = DBConnection.getConnection()) {
            record = fineService.createFineForReturn(conn, txId, 4, 1, 1, dueDate, returnDate);
        }

        assertNotNull(record);
        assertNotNull(record.getFineId());
        assertEquals(1, record.getOverdueDays());
        assertEquals(1, record.getChargeableDays());
        assertEquals(new BigDecimal("2.00"), record.getOriginalAmount());
        assertEquals(new BigDecimal("2.00"), record.getOutstandingAmount());
        assertEquals(new BigDecimal("0.00"), record.getPaidAmount());
        assertEquals(new BigDecimal("0.00"), record.getWaivedAmount());
        assertEquals(FineStatus.UNPAID, record.getStatus());
    }

    /**
     * Test 3: Multi-day late returns (5 days and 30 days) scale correctly.
     */
    @Test
    public void testMultiDayLateFineCalculation() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 1);
        int txId5 = createTestBorrowTx(4, 1, 1, dueDate);
        int txId30 = createTestBorrowTx(5, 2, 5, dueDate);

        // 5 days late -> 5 * 2 = INR 10.00
        FineRecord fine5;
        try (Connection conn = DBConnection.getConnection()) {
            fine5 = fineService.createFineForReturn(conn, txId5, 4, 1, 1, dueDate, LocalDate.of(2026, 10, 6));
        }
        assertNotNull(fine5);
        assertEquals(5, fine5.getOverdueDays());
        assertEquals(new BigDecimal("10.00"), fine5.getOriginalAmount());
        assertEquals(new BigDecimal("10.00"), fine5.getOutstandingAmount());

        // 30 days late -> 30 * 2 = INR 60.00
        FineRecord fine30;
        try (Connection conn = DBConnection.getConnection()) {
            fine30 = fineService.createFineForReturn(conn, txId30, 5, 2, 5, dueDate, LocalDate.of(2026, 10, 31));
        }
        assertNotNull(fine30);
        assertEquals(30, fine30.getOverdueDays());
        assertEquals(new BigDecimal("60.00"), fine30.getOriginalAmount());
        assertEquals(new BigDecimal("60.00"), fine30.getOutstandingAmount());
    }

    /**
     * Test 4: Boundary calendar-day calculation using injected Clock.
     */
    @Test
    public void testBoundaryCalendarDayCalculation() {
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        // Fixed instant: 2026-10-25 23:59:59 IST
        Instant instantSameDay = Instant.parse("2026-10-25T18:29:59Z"); // 23:59:59 IST
        Clock clockSameDay = Clock.fixed(instantSameDay, istZone);
        FineCalculator calcSameDay = new FineCalculator(new StandardFinePolicy(), clockSameDay);

        LocalDate dueDate = LocalDate.of(2026, 10, 25);
        FineCalculation c1 = calcSameDay.calculateProjectedFine(dueDate);
        assertFalse("Same calendar day (23:59:59) should not be overdue", c1.isFineIncurred());
        assertEquals(0, c1.getOverdueDays());

        // Fixed instant: 2026-10-26 00:00:01 IST (1 second into next day)
        Instant instantNextDay = Instant.parse("2026-10-25T18:30:01Z"); // 00:00:01 IST
        Clock clockNextDay = Clock.fixed(instantNextDay, istZone);
        FineCalculator calcNextDay = new FineCalculator(new StandardFinePolicy(), clockNextDay);

        FineCalculation c2 = calcNextDay.calculateProjectedFine(dueDate);
        assertTrue("Next calendar day (00:00:01) is overdue", c2.isFineIncurred());
        assertEquals(1, c2.getOverdueDays());
        assertEquals(new BigDecimal("2.00"), c2.getFineAmount());
    }

    /**
     * Test 5: Idempotent return prevents duplicate fine creation.
     */
    @Test
    public void testIdempotentReturnNoDuplicateFine() throws SQLException {
        // Setup transaction 1 as overdue
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE borrow_transactions SET due_date = '2026-10-01' WHERE transaction_id = 1");
        }

        // Return transaction 1
        BorrowTransaction returnedTx = borrowingService.returnBook(1, 2);
        assertEquals(BorrowStatus.RETURNED, returnedTx.getStatus());
        assertNotNull("Fine record should be created for overdue return", returnedTx.getFineRecord());

        // Attempting to return again must fail and not create another fine
        try {
            borrowingService.returnBook(1, 2);
            fail("Expected InvalidBorrowingOperationException for double return");
        } catch (InvalidBorrowingOperationException e) {
            assertTrue(e.getMessage().contains("already been returned"));
        }

        List<FineRecord> studentFines = fineService.getFinesByStudent(4);
        assertEquals("Exactly one fine record must exist for the transaction", 1, studentFines.size());
    }

    /**
     * Test 6: Single active payment attempt constraint (no duplicate in-flight payments).
     */
    @Test
    public void testSingleActivePaymentConstraint() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 1);
        int txId = createTestBorrowTx(4, 1, 1, dueDate);

        FineRecord fine;
        try (Connection conn = DBConnection.getConnection()) {
            fine = fineService.createFineForReturn(conn, txId, 4, 1, 1,
                    dueDate, LocalDate.of(2026, 10, 6)); // INR 10.00
        }

        // Student initiates payment attempt 1
        Map<String, Object> res1 = paymentService.initiatePayment(4, fine.getFineId(), new BigDecimal("10.00"), "idem-key-1");
        assertNotNull(res1.get("payment"));

        // Student attempts to initiate payment attempt 2 on the same fine
        try {
            paymentService.initiatePayment(4, fine.getFineId(), new BigDecimal("10.00"), "idem-key-2");
            fail("Expected PaymentInProgressException when active attempt already exists");
        } catch (PaymentInProgressException e) {
            assertTrue(e.getMessage().contains("active payment attempt is already in progress"));
        }
    }

    /**
     * Test 7: Stale INITIATED payment is superseded after 30 minutes.
     */
    @Test
    public void testStaleInitiationSuperseded() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 1);
        int txId = createTestBorrowTx(4, 1, 1, dueDate);

        FineRecord fine;
        try (Connection conn = DBConnection.getConnection()) {
            fine = fineService.createFineForReturn(conn, txId, 4, 1, 1,
                    dueDate, LocalDate.of(2026, 10, 6));
        }

        // Initiate payment 1
        paymentService.initiatePayment(4, fine.getFineId(), new BigDecimal("10.00"), "idem-stale");

        // Manually age the payment to 35 minutes ago using consistent Java Timestamp
        java.sql.Timestamp agedTime = new java.sql.Timestamp(System.currentTimeMillis() - (35L * 60L * 1000L));
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE payments SET created_at = ? WHERE fine_id = ?")) {
            ps.setTimestamp(1, agedTime);
            ps.setInt(2, fine.getFineId());
            ps.executeUpdate();
        }

        // Next initiation attempt should supersede the stale one
        Map<String, Object> res2 = paymentService.initiatePayment(4, fine.getFineId(), new BigDecimal("10.00"), "idem-fresh");
        Payment newPayment = (Payment) res2.get("payment");
        assertNotNull(newPayment);
        assertEquals(PaymentStatus.INITIATED, newPayment.getStatus());
    }

    /**
     * Test 8: Duplicate UPI reference ID / UTR is rejected (409 Conflict).
     */
    @Test
    public void testDuplicateReferenceRejected() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 1);
        int txId1 = createTestBorrowTx(4, 1, 1, dueDate);
        int txId2 = createTestBorrowTx(5, 2, 5, dueDate);

        FineRecord fine1;
        FineRecord fine2;
        try (Connection conn = DBConnection.getConnection()) {
            fine1 = fineService.createFineForReturn(conn, txId1, 4, 1, 1,
                    dueDate, LocalDate.of(2026, 10, 6));
            fine2 = fineService.createFineForReturn(conn, txId2, 5, 2, 5,
                    dueDate, LocalDate.of(2026, 10, 6));
        }

        Map<String, Object> res1 = paymentService.initiatePayment(4, fine1.getFineId(), new BigDecimal("10.00"), "idem-ref-1");
        Payment p1 = (Payment) res1.get("payment");

        Map<String, Object> res2 = paymentService.initiatePayment(5, fine2.getFineId(), new BigDecimal("10.00"), "idem-ref-2");
        Payment p2 = (Payment) res2.get("payment");

        // Student 1 submits reference
        paymentService.submitReference(p1.getPaymentId(), 4, "430123456789");

        // Student 2 attempts to submit the identical reference
        try {
            paymentService.submitReference(p2.getPaymentId(), 5, "430123456789");
            fail("Expected DuplicatePaymentReferenceException for reused UPI reference");
        } catch (DuplicatePaymentReferenceException e) {
            assertTrue(e.getMessage().contains("already been recorded"));
        }
    }

    /**
     * Test 9: Staff verification transitions payment to SUCCESS, settles fine, and generates receipt.
     */
    @Test
    public void testStaffVerificationSuccess() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 1);
        int txId = createTestBorrowTx(4, 1, 1, dueDate);

        FineRecord fine;
        try (Connection conn = DBConnection.getConnection()) {
            fine = fineService.createFineForReturn(conn, txId, 4, 1, 1,
                    dueDate, LocalDate.of(2026, 10, 6)); // INR 10.00
        }

        Map<String, Object> res = paymentService.initiatePayment(4, fine.getFineId(), new BigDecimal("10.00"), "idem-verif");
        Payment p = (Payment) res.get("payment");
        paymentService.submitReference(p.getPaymentId(), 4, "987654321098");

        // Staff verifies
        Payment verified = paymentService.verifyPayment(p.getPaymentId(), 2, Role.LIBRARIAN);
        assertEquals(PaymentStatus.SUCCESS, verified.getStatus());
        assertNotNull(verified.getReceiptNumber());
        assertTrue(verified.getReceiptNumber().startsWith("RCPT-"));

        // Fine should be fully paid
        FineRecord updatedFine = fineService.getFineById(fine.getFineId());
        assertEquals(FineStatus.PAID, updatedFine.getStatus());
        assertEquals(new BigDecimal("10.00"), updatedFine.getPaidAmount());
        assertEquals(new BigDecimal("0.00"), updatedFine.getOutstandingAmount());

        // Receipt data verification
        Map<String, Object> rcpt = paymentService.getReceiptData(p.getPaymentId(), 4, Role.STUDENT);
        assertEquals(verified.getReceiptNumber(), rcpt.get("receiptNumber"));
        assertEquals("10.00", rcpt.get("amountPaidThisPayment"));
    }

    /**
     * Test 10: Staff rejection with mandatory reason.
     */
    @Test
    public void testStaffRejectionMandatoryReason() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 1);
        int txId = createTestBorrowTx(4, 1, 1, dueDate);

        FineRecord fine;
        try (Connection conn = DBConnection.getConnection()) {
            fine = fineService.createFineForReturn(conn, txId, 4, 1, 1,
                    dueDate, LocalDate.of(2026, 10, 6));
        }

        Map<String, Object> res = paymentService.initiatePayment(4, fine.getFineId(), new BigDecimal("10.00"), "idem-rej");
        Payment p = (Payment) res.get("payment");
        paymentService.submitReference(p.getPaymentId(), 4, "111222333444");

        // Rejection without reason fails
        try {
            paymentService.rejectPayment(p.getPaymentId(), 2, Role.LIBRARIAN, "");
            fail("Expected InvalidPaymentException when rejection reason is missing");
        } catch (InvalidPaymentException e) {
            assertTrue(e.getMessage().contains("mandatory"));
        }

        // Rejection with reason succeeds
        Payment rejected = paymentService.rejectPayment(p.getPaymentId(), 2, Role.LIBRARIAN, "UTR not found in bank statement");
        assertEquals(PaymentStatus.REJECTED, rejected.getStatus());
        assertEquals("UTR not found in bank statement", rejected.getRejectionReason());
    }

    /**
     * Test 11: Admin fine waiver enforces invariant: Original = Paid + Waived + Outstanding.
     */
    @Test
    public void testAdminWaiverEnforcesInvariant() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 1);
        int txId = createTestBorrowTx(4, 1, 1, dueDate);

        FineRecord fine;
        try (Connection conn = DBConnection.getConnection()) {
            fine = fineService.createFineForReturn(conn, txId, 4, 1, 1,
                    dueDate, LocalDate.of(2026, 10, 11)); // 10 days = INR 20.00
        }

        // Librarian cannot waive (Decision D7: Admin only)
        try {
            fineService.applyWaiver(fine.getFineId(), new BigDecimal("5.00"), "Medical excuse", 2, Role.LIBRARIAN);
            fail("Expected InvalidFineOperationException when Librarian attempts waiver");
        } catch (InvalidFineOperationException e) {
            assertTrue(e.getMessage().contains("Only Administrators"));
        }

        // Admin applies partial waiver of INR 8.00
        FineRecord waivedFine = fineService.applyWaiver(fine.getFineId(), new BigDecimal("8.00"), "Approved medical certificate", 1, Role.ADMIN);
        assertEquals(FineStatus.PARTIALLY_PAID, waivedFine.getStatus());
        assertEquals(new BigDecimal("8.00"), waivedFine.getWaivedAmount());
        assertEquals(new BigDecimal("12.00"), waivedFine.getOutstandingAmount());

        // Invariant check
        BigDecimal expectedOriginal = waivedFine.getPaidAmount().add(waivedFine.getWaivedAmount()).add(waivedFine.getOutstandingAmount());
        assertEquals(waivedFine.getOriginalAmount(), expectedOriginal);

        // Admin waives remaining INR 12.00
        FineRecord fullWaiver = fineService.applyWaiver(fine.getFineId(), new BigDecimal("12.00"), "Principal clearance", 1, Role.ADMIN);
        assertEquals(FineStatus.WAIVED, fullWaiver.getStatus());
        assertEquals(new BigDecimal("20.00"), fullWaiver.getWaivedAmount());
        assertEquals(new BigDecimal("0.00"), fullWaiver.getOutstandingAmount());
    }

    /**
     * Test 12: Admin refund of SUCCESS payment restores fine balance and marks payment REFUNDED.
     */
    @Test
    public void testAdminRefundReversal() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 1);
        int txId = createTestBorrowTx(4, 1, 1, dueDate);

        FineRecord fine;
        try (Connection conn = DBConnection.getConnection()) {
            fine = fineService.createFineForReturn(conn, txId, 4, 1, 1,
                    dueDate, LocalDate.of(2026, 10, 6)); // INR 10.00
        }

        Map<String, Object> res = paymentService.initiatePayment(4, fine.getFineId(), new BigDecimal("10.00"), "idem-refund");
        Payment p = (Payment) res.get("payment");
        paymentService.submitReference(p.getPaymentId(), 4, "555666777888");
        paymentService.verifyPayment(p.getPaymentId(), 2, Role.LIBRARIAN);

        // Admin records refund
        Payment refunded = paymentService.recordRefund(p.getPaymentId(), 1, Role.ADMIN, "Student charged twice by bank");
        assertEquals(PaymentStatus.REFUNDED, refunded.getStatus());

        // Fine balance restored
        FineRecord updatedFine = fineService.getFineById(fine.getFineId());
        assertEquals(FineStatus.UNPAID, updatedFine.getStatus());
        assertEquals(new BigDecimal("0.00"), updatedFine.getPaidAmount());
        assertEquals(new BigDecimal("10.00"), updatedFine.getOutstandingAmount());

        // Receipt reflects refund / void status
        Map<String, Object> rcpt = paymentService.getReceiptData(p.getPaymentId(), 1, Role.ADMIN);
        assertTrue((Boolean) rcpt.get("isRefunded"));
        assertEquals("Student charged twice by bank", rcpt.get("refundReason"));
    }

    /**
     * Test 13: Outstanding fine (> INR 0) blocks book issue and collection (Decision D9).
     */
    @Test
    public void testOutstandingFineBlocksBorrowingAndCollection() throws SQLException {
        LocalDate dueDate = LocalDate.of(2026, 10, 1);
        int txId = createTestBorrowTx(4, 1, 1, dueDate);

        // Create an overdue fine for Student #4 (Sivakathir, reg: 715522104045)
        try (Connection conn = DBConnection.getConnection()) {
            fineService.createFineForReturn(conn, txId, 4, 1, 1,
                    dueDate, LocalDate.of(2026, 10, 6)); // INR 10.00 outstanding
        }

        assertTrue("Student #4 should have outstanding fines", fineService.hasOutstandingFines(4));

        // 1. Issuing a book to Student #4 must be blocked
        try {
            borrowingService.issueBook("715522104045", 8, 2);
            fail("Expected StudentNotEligibleException when student has outstanding fines");
        } catch (StudentNotEligibleException e) {
            assertTrue("Exception message should cite outstanding fines",
                    e.getMessage().toLowerCase().contains("fine") || e.getMessage().toLowerCase().contains("outstanding"));
        }

        // 2. Desk collection of an allocated book must also be blocked
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO reservations (reservation_id, book_id, student_id, reservation_date, status, created_at, updated_at) " +
                    "VALUES (9901, 2, 4, CURRENT_DATE, 'ALLOCATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            stmt.executeUpdate("INSERT INTO allocations (allocation_id, reservation_id, student_id, book_id, copy_id, return_transaction_id, status, allocated_at, expires_at) " +
                    "VALUES (9901, 9901, 4, 2, 5, 2, 'ALLOCATED', CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY))");
            stmt.executeUpdate("UPDATE book_copies SET status = 'ALLOCATED' WHERE copy_id = 5");
        }

        try (Connection conn = DBConnection.getConnection()) {
            allocationService.validateCollection(conn, 5, 4);
            fail("Expected InvalidAllocationException during collection when student has outstanding fines");
        } catch (InvalidAllocationException e) {
            assertTrue("Exception message should cite outstanding fines",
                    e.getMessage().toLowerCase().contains("fine") || e.getMessage().toLowerCase().contains("dues"));
        }
    }
}
