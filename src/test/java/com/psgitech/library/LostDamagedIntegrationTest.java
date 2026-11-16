package com.psgitech.library;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.config.LossDamageConfig;
import com.psgitech.library.enums.*;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.LossDamageCaseRepository;
import com.psgitech.library.repository.ReservationRepository;
import com.psgitech.library.service.AllocationService;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.service.LossDamageService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Level 2 & 3 Integration Test Suite for Feature 14: Lost or Damaged Book Replacement.
 * Validates complete end-to-end lifecycle, state transitions, return hooks,
 * monetary fines, physical replacement accessioning with reservation queue offering,
 * and borrowing eligibility blocks on real MySQL test database.
 */
public class LostDamagedIntegrationTest {

    private LossDamageService lossDamageService;
    private LossDamageCaseRepository caseRepository;
    private BorrowRepository borrowRepository;
    private BookRepository bookRepository;
    private BorrowingService borrowingService;
    private ReservationRepository reservationRepository;
    private AllocationService allocationService;

    private int studentAlphaId;
    private int studentBetaId;
    private int staffUserId;
    private int adminUserId;

    private int bookAlphaId;
    private int copyAlpha1Id;
    private int copyAlpha2Id;
    private String bookAlphaIsbn;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test must run on MySQL", "MySQL", prod);
            assertTrue("Schema must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));
        }

        BorrowingPolicy.resetToDefaults();
        LossDamageConfig.resetToDefaults();

        this.caseRepository = new LossDamageCaseRepository();
        this.borrowRepository = new BorrowRepository();
        this.bookRepository = new BookRepository();
        this.allocationService = new AllocationService();
        this.reservationRepository = new ReservationRepository();
        this.lossDamageService = new LossDamageService();
        this.borrowingService = new BorrowingService(borrowRepository, new com.psgitech.library.repository.UserRepository(), bookRepository);

        cleanFixtures();
        setupFixtures();
    }

    @After
    public void tearDown() {
        cleanFixtures();
        BorrowingPolicy.resetToDefaults();
        LossDamageConfig.resetToDefaults();
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
            // Users
            staffUserId = 2; // Librarian
            adminUserId = 1; // Admin

            // Insert Student Alpha
            String insUser = "INSERT INTO users (name, email, password_hash, phone, role, register_number, department, `year`, active, must_change_password, email_verified) " +
                    "VALUES (?, ?, '$2a$10$e8w.xeqzE6fWzrq8J/5YqOSGjP/pA8u8QZcQdG0Ccm8t41X5u8gCe', '9876543210', 'STUDENT', ?, 'CSE', 3, 1, 0, 1)";
            try (PreparedStatement ps = conn.prepareStatement(insUser, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "F14 Alpha Student");
                ps.setString(2, "f14_alpha@psgitech.ac.in");
                ps.setString(3, "715523F1401");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    studentAlphaId = rs.getInt(1);
                }
            }

            // Insert Student Beta
            try (PreparedStatement ps = conn.prepareStatement(insUser, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "F14 Beta Student");
                ps.setString(2, "f14_beta@psgitech.ac.in");
                ps.setString(3, "715523F1402");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    studentBetaId = rs.getInt(1);
                }
            }

            // Book Alpha
            bookAlphaIsbn = "978-0-1414-9999-1";
            String insBook = "INSERT INTO books (isbn, title, author, publisher, publication_year, category, department, replacement_price) " +
                    "VALUES (?, 'F14 Clean Code Master', 'Robert Martin', 'Prentice Hall', 2022, 'Computer Science', 'CSE', 800.00)";
            try (PreparedStatement ps = conn.prepareStatement(insBook, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, bookAlphaIsbn);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    bookAlphaId = rs.getInt(1);
                }
            }

            // Book Copies: Copy 1 (acquired 1 year ago)
            String insCopy = "INSERT INTO book_copies (book_id, copy_code, status, created_at) VALUES (?, ?, ?, DATE_SUB(NOW(), INTERVAL 365 DAY))";
            try (PreparedStatement ps = conn.prepareStatement(insCopy, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, bookAlphaId);
                ps.setString(2, "PSG-F14-BK1-C01");
                ps.setString(3, "AVAILABLE");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    copyAlpha1Id = rs.getInt(1);
                }
            }

            // Copy 2 (AVAILABLE)
            try (PreparedStatement ps = conn.prepareStatement(insCopy, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, bookAlphaId);
                ps.setString(2, "PSG-F14-BK1-C02");
                ps.setString(3, "AVAILABLE");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    copyAlpha2Id = rs.getInt(1);
                }
            }
        }
    }

    private int issueBookToAlpha(int copyId) {
        BorrowTransaction tx = borrowingService.issueBook("715523F1401", copyId, staffUserId);
        assertNotNull(tx);
        return tx.getTransactionId();
    }

    @Test
    public void testReportLostBook_Success() {
        int txId = issueBookToAlpha(copyAlpha1Id);

        LossDamageCase c = lossDamageService.reportLost(txId, studentAlphaId, "Misplaced book on college bus", studentAlphaId, "STUDENT");

        assertNotNull(c);
        assertTrue(c.getCaseId() > 0);
        assertEquals(CaseType.LOST, c.getCaseType());
        assertEquals(CaseStatus.REPORTED, c.getStatus());
        assertEquals(studentAlphaId, c.getStudentId());
        assertEquals(bookAlphaId, c.getBookId());
        assertEquals(copyAlpha1Id, c.getCopyId());
        assertEquals("Misplaced book on college bus", c.getReportNote());

        // Verify audit event
        List<CaseEvent> events = lossDamageService.getCaseEvents(c.getCaseId());
        assertEquals(1, events.size());
        assertEquals("CASE_REPORTED", events.get(0).getEventType());
    }

    @Test
    public void testReportLostBook_DuplicateActiveCase_IdempotentAndUniqueConstraint() throws Exception {
        int txId = issueBookToAlpha(copyAlpha1Id);

        LossDamageCase c1 = lossDamageService.reportLost(txId, studentAlphaId, "First report", studentAlphaId, "STUDENT");
        LossDamageCase c2 = lossDamageService.reportLost(txId, studentAlphaId, "Second report", studentAlphaId, "STUDENT");

        // Service level idempotency
        assertNotNull(c1);
        assertNotNull(c2);
        assertEquals(c1.getCaseId(), c2.getCaseId());

        // Database level constraint uq_active_loan_case rejection on raw duplicate active insert
        try (Connection conn = DBConnection.getConnection()) {
            LossDamageCase duplicate = LossDamageCase.createReported(txId, studentAlphaId, bookAlphaId, copyAlpha1Id, staffUserId, "Raw duplicate");
            try {
                caseRepository.insertCase(conn, duplicate);
                fail("Expected SQLException (1062 duplicate key) on uq_active_loan_case");
            } catch (java.sql.SQLException e) {
                assertEquals(1062, e.getErrorCode());
            }
        }
    }

    @Test
    public void testAssessLostBook_Withdrawn_Archived_LoanLost() {
        int txId = issueBookToAlpha(copyAlpha1Id);
        LossDamageCase reported = lossDamageService.reportLost(txId, studentAlphaId, "Lost at train station", studentAlphaId, "STUDENT");

        LossDamageCase assessed = lossDamageService.assessLost(reported.getCaseId(), null, "Confirmed lost by librarian", staffUserId, "LIBRARIAN");

        assertNotNull(assessed);
        assertEquals(CaseStatus.ASSESSED, assessed.getStatus());
        assertEquals(staffUserId, assessed.getAssessedBy().intValue());
        assertNotNull(assessed.getComputedAmount());
        assertTrue(assessed.getComputedAmount().compareTo(BigDecimal.ZERO) > 0);

        // Verify loan closed as LOST
        BorrowTransaction tx = borrowRepository.findById(txId);
        assertEquals(BorrowStatus.LOST, tx.getStatus());

        // Verify physical copy retired to ARCHIVED
        BookCopy copy = bookRepository.findCopyById(copyAlpha1Id);
        assertEquals(CopyStatus.ARCHIVED, copy.getStatus());

        // Verify event logged
        List<CaseEvent> events = lossDamageService.getCaseEvents(assessed.getCaseId());
        assertEquals(2, events.size());
        assertEquals("CASE_ASSESSED", events.get(1).getEventType());
    }

    @Test
    public void testFoundBookAutoCancelsReportedCase() {
        int txId = issueBookToAlpha(copyAlpha1Id);
        LossDamageCase reported = lossDamageService.reportLost(txId, studentAlphaId, "Lost somewhere", studentAlphaId, "STUDENT");

        // Book is found and returned at circulation desk
        borrowRepository.returnBookTransaction(txId, staffUserId, LocalDate.now(), "GOOD", null, null, null, "Found by student");

        // Verify loan is RETURNED
        BorrowTransaction tx = borrowRepository.findById(txId);
        assertEquals(BorrowStatus.RETURNED, tx.getStatus());

        // Verify copy restored to AVAILABLE
        BookCopy copy = bookRepository.findCopyById(copyAlpha1Id);
        assertEquals(CopyStatus.AVAILABLE, copy.getStatus());

        // Verify case auto-cancelled
        LossDamageCase updatedCase = lossDamageService.getCaseById(reported.getCaseId());
        assertEquals(CaseStatus.CANCELLED, updatedCase.getStatus());

        // Verify event
        List<CaseEvent> events = lossDamageService.getCaseEvents(reported.getCaseId());
        assertTrue(events.stream().anyMatch(e -> "CANCELLED".equals(e.getNewStatus()) && e.getDetails().contains("Auto-cancelled")));
    }

    @Test
    public void testReturnDamagedBook_Withdrawn_Archived_DirectAssessed() {
        int txId = issueBookToAlpha(copyAlpha1Id);

        // Student returns severely damaged book (water damage)
        borrowRepository.returnBookTransaction(
                txId,
                staffUserId,
                LocalDate.now(),
                "DAMAGED",
                DamageSeverity.UNUSABLE,
                CopyDisposition.WITHDRAWN,
                new BigDecimal("800.00"),
                "Pages soaked and unreadable"
        );

        // Verify loan RETURNED
        BorrowTransaction tx = borrowRepository.findById(txId);
        assertEquals(BorrowStatus.RETURNED, tx.getStatus());

        // Verify copy ARCHIVED
        BookCopy copy = bookRepository.findCopyById(copyAlpha1Id);
        assertEquals(CopyStatus.ARCHIVED, copy.getStatus());

        // Verify direct ASSESSED case created
        List<LossDamageCase> cases = lossDamageService.getMyCases(studentAlphaId);
        assertFalse(cases.isEmpty());
        LossDamageCase dc = cases.get(0);
        assertEquals(CaseType.DAMAGED, dc.getCaseType());
        assertEquals(CaseStatus.ASSESSED, dc.getStatus());
        assertEquals(DamageSeverity.UNUSABLE, dc.getSeverity());
        assertEquals(CopyDisposition.WITHDRAWN, dc.getCopyDisposition());
    }

    @Test
    public void testReturnDamagedBook_Minor_ReturnedToCirculation() {
        int txId = issueBookToAlpha(copyAlpha1Id);

        // Student returns book with minor pencil markings (circulatable)
        borrowRepository.returnBookTransaction(
                txId,
                staffUserId,
                LocalDate.now(),
                "DAMAGED",
                DamageSeverity.MINOR,
                CopyDisposition.RETURNED_TO_CIRCULATION,
                new BigDecimal("800.00"),
                "Minor pencil notes on chapter 2"
        );

        // Verify copy remains in circulation (AVAILABLE)
        BookCopy copy = bookRepository.findCopyById(copyAlpha1Id);
        assertEquals(CopyStatus.AVAILABLE, copy.getStatus());

        // Verify case ASSESSED with RETURNED_TO_CIRCULATION
        List<LossDamageCase> cases = lossDamageService.getMyCases(studentAlphaId);
        assertFalse(cases.isEmpty());
        LossDamageCase dc = cases.get(0);
        assertEquals(CaseType.DAMAGED, dc.getCaseType());
        assertEquals(CaseStatus.ASSESSED, dc.getStatus());
        assertEquals(DamageSeverity.MINOR, dc.getSeverity());
        assertEquals(CopyDisposition.RETURNED_TO_CIRCULATION, dc.getCopyDisposition());
    }

    @Test
    public void testMonetaryCompensationChoiceAndFineSettlement() throws Exception {
        int txId = issueBookToAlpha(copyAlpha1Id);
        LossDamageCase reported = lossDamageService.reportLost(txId, studentAlphaId, "Lost", studentAlphaId, "STUDENT");
        LossDamageCase assessed = lossDamageService.assessLost(reported.getCaseId(), null, "Lost", staffUserId, "LIBRARIAN");

        // Student chooses monetary compensation
        LossDamageCase chosen = lossDamageService.choosePayment(assessed.getCaseId(), studentAlphaId, "STUDENT");
        assertEquals(CaseStatus.PAYMENT_PENDING, chosen.getStatus());
        assertNotNull(chosen.getFineId());

        // Verify fine created
        int fineId = chosen.getFineId();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM fines WHERE fine_id = ?")) {
            ps.setInt(1, fineId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("COMPENSATION", rs.getString("fine_type"));
                assertEquals("UNPAID", rs.getString("status"));
                assertEquals(0, chosen.getFinalAmount().compareTo(rs.getBigDecimal("original_amount")));
            }
        }

        // Simulate fine settlement (payment verified)
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            lossDamageService.handleFineSettled(conn, fineId, chosen.getFinalAmount(), BigDecimal.ZERO);
            conn.commit();
        }

        // Verify case resolved as RESOLVED_PAID
        LossDamageCase resolved = lossDamageService.getCaseById(chosen.getCaseId());
        assertEquals(CaseStatus.RESOLVED_PAID, resolved.getStatus());
        assertNotNull(resolved.getResolvedAt());
    }

    @Test
    public void testPhysicalReplacement_Receive_Accept_NewCopy_QueueAllocation() throws Exception {
        int txId = issueBookToAlpha(copyAlpha1Id);
        LossDamageCase reported = lossDamageService.reportLost(txId, studentAlphaId, "Lost", studentAlphaId, "STUDENT");
        LossDamageCase assessed = lossDamageService.assessLost(reported.getCaseId(), null, "Lost", staffUserId, "LIBRARIAN");

        // Student chooses physical replacement
        LossDamageCase chosen = lossDamageService.chooseReplacement(assessed.getCaseId(), studentAlphaId, "STUDENT");
        assertEquals(CaseStatus.REPLACEMENT_PENDING, chosen.getStatus());
        assertNotNull(chosen.getReplacementDeadline());
        assertEquals(LocalDate.now().plusDays(30), chosen.getReplacementDeadline().toLocalDate());

        // Student Beta creates reservation on this book
        // Make sure copyAlpha2Id is BORROWED so queue is non-empty / book unavailable
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = " + copyAlpha2Id);
        }
        Reservation res = reservationRepository.createReservationTransaction(studentBetaId, bookAlphaId);
        assertNotNull(res);

        // Staff receives physical replacement
        LossDamageCase received = lossDamageService.receiveReplacement(chosen.getCaseId(), "3rd Edition", "Brand new sealed", staffUserId, "LIBRARIAN");
        assertEquals(CaseStatus.REPLACEMENT_SUBMITTED, received.getStatus());

        // Staff accepts physical replacement
        LossDamageCase accepted = lossDamageService.acceptReplacement(
                received.getCaseId(),
                bookAlphaIsbn,
                "NEW",
                "Verified authentic by librarian",
                staffUserId,
                "LIBRARIAN"
        );

        assertEquals(CaseStatus.RESOLVED_REPLACED, accepted.getStatus());
        assertNotNull(accepted.getReplacementCopyId());

        // Verify new copy was created
        BookCopy newCopy = bookRepository.findCopyById(accepted.getReplacementCopyId());
        assertNotNull(newCopy);
        assertEquals(bookAlphaId, newCopy.getBookId());

        // Verify new copy was allocated to Student Beta in queue!
        assertEquals(CopyStatus.ALLOCATED, newCopy.getStatus());
    }

    @Test(expected = IsbnMismatchException.class)
    public void testPhysicalReplacement_IsbnMismatchThrowsException() {
        int txId = issueBookToAlpha(copyAlpha1Id);
        LossDamageCase reported = lossDamageService.reportLost(txId, studentAlphaId, "Lost", studentAlphaId, "STUDENT");
        LossDamageCase assessed = lossDamageService.assessLost(reported.getCaseId(), null, "Lost", staffUserId, "LIBRARIAN");
        LossDamageCase chosen = lossDamageService.chooseReplacement(assessed.getCaseId(), studentAlphaId, "STUDENT");
        lossDamageService.receiveReplacement(chosen.getCaseId(), staffUserId, "LIBRARIAN");

        // Attempt accept with wrong ISBN
        lossDamageService.acceptReplacement(
                chosen.getCaseId(),
                "978-9-9999-9999-9",
                "NEW",
                "Wrong book",
                staffUserId,
                "LIBRARIAN"
        );
    }

    @Test
    public void testPhysicalReplacementRejectionAndConversionToPayment() {
        int txId = issueBookToAlpha(copyAlpha1Id);
        LossDamageCase reported = lossDamageService.reportLost(txId, studentAlphaId, "Lost", studentAlphaId, "STUDENT");
        LossDamageCase assessed = lossDamageService.assessLost(reported.getCaseId(), null, "Lost", staffUserId, "LIBRARIAN");
        LossDamageCase chosen = lossDamageService.chooseReplacement(assessed.getCaseId(), studentAlphaId, "STUDENT");
        lossDamageService.receiveReplacement(chosen.getCaseId(), staffUserId, "LIBRARIAN");

        // Staff rejects replacement
        LossDamageCase rejected = lossDamageService.rejectReplacement(chosen.getCaseId(), "Water stained pages", staffUserId, "LIBRARIAN");
        assertEquals(CaseStatus.ASSESSED, rejected.getStatus());
        assertEquals("Water stained pages", rejected.getRejectionReason());

        // Convert to payment
        LossDamageCase converted = lossDamageService.convertToPayment(chosen.getCaseId(), "Switched to monetary compensation", staffUserId, "LIBRARIAN");
        assertEquals(CaseStatus.PAYMENT_PENDING, converted.getStatus());
        assertNotNull(converted.getFineId());
    }

    @Test
    public void testUnresolvedCaseBlocksBorrowing() throws Exception {
        int txId = issueBookToAlpha(copyAlpha1Id);
        LossDamageCase reported = lossDamageService.reportLost(txId, studentAlphaId, "Lost", studentAlphaId, "STUDENT");
        lossDamageService.assessLost(reported.getCaseId(), null, "Lost", staffUserId, "LIBRARIAN");

        // Open case exists
        try (Connection conn = DBConnection.getConnection()) {
            assertTrue(BorrowingPolicy.hasUnresolvedCase(studentAlphaId, conn));
            assertTrue(BorrowingPolicy.hasBlockingObligation(studentAlphaId, conn));
        }

        // Student Alpha attempts to borrow another copy
        try {
            borrowingService.issueBook("715523F1401", copyAlpha2Id, staffUserId);
            fail("Expected StudentNotEligibleException due to open case");
        } catch (StudentNotEligibleException e) {
            assertTrue(e.getMessage().contains("unresolved lost or damaged book case") || e.getMessage().contains("Clear obligations"));
        }
    }

    @Test
    public void testAdminOverrideCompensationAmount() {
        int txId = issueBookToAlpha(copyAlpha1Id);
        LossDamageCase reported = lossDamageService.reportLost(txId, studentAlphaId, "Lost", studentAlphaId, "STUDENT");
        LossDamageCase assessed = lossDamageService.assessLost(reported.getCaseId(), null, "Lost", staffUserId, "LIBRARIAN");

        BigDecimal originalAmount = assessed.getFinalAmount();
        BigDecimal overriddenAmount = new BigDecimal("450.00");

        LossDamageCase overridden = lossDamageService.overrideAmount(
                assessed.getCaseId(),
                overriddenAmount,
                "Dean approved financial reduction",
                adminUserId,
                "ADMIN"
        );

        assertNotNull(overridden);
        assertEquals(0, overriddenAmount.compareTo(overridden.getFinalAmount()));
        assertEquals(adminUserId, overridden.getOverrideBy().intValue());
        assertEquals("Dean approved financial reduction", overridden.getOverrideReason());

        // Verify event
        List<CaseEvent> events = lossDamageService.getCaseEvents(assessed.getCaseId());
        assertTrue(events.stream().anyMatch(e -> "AMOUNT_OVERRIDDEN".equals(e.getEventType()) && e.getDetails().contains("Dean approved")));
    }
}
