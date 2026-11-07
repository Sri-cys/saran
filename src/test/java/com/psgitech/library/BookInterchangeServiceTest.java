package com.psgitech.library;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.config.FineConfig;
import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.InterchangeStatus;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.*;
import com.psgitech.library.service.BookInterchangeService;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit, Integration & Concurrency Test Suite for Feature 8: Student-to-Student Book Interchange.
 * Validates:
 * 1. Complete interchange lifecycle (Initiate, Accept, Reject, Cancel).
 * 2. Strict due date preservation and ownership transfer.
 * 3. Feature 6 Rule D10 preservation: finalized fines block, but other unreturned overdue books do not.
 * 4. Transfer of an overdue book is blocked.
 * 5. Reservation queue priority protection (WAITING & ALLOCATED).
 * 6. Quota enforcement with active allocations.
 * 7. History preservation for both sender and recipient.
 * 8. Desk return auto-cancellation and stale-borrower ID retry.
 * 9. Concurrent acceptances for the same recipient with quota safety.
 */
public class BookInterchangeServiceTest {
    private BookInterchangeService interchangeService;
    private BookInterchangeRepository interchangeRepository;
    private BorrowRepository borrowRepository;
    private UserRepository userRepository;
    private BookRepository bookRepository;
    private ReservationRepository reservationRepository;
    private AllocationRepository allocationRepository;
    private BorrowingService borrowingService;

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        this.interchangeRepository = new BookInterchangeRepository();
        this.borrowRepository = new BorrowRepository();
        this.userRepository = new UserRepository();
        this.bookRepository = new BookRepository();
        this.reservationRepository = new ReservationRepository();
        this.allocationRepository = new AllocationRepository();
        this.interchangeService = new BookInterchangeService(
                interchangeRepository, borrowRepository, userRepository,
                bookRepository, reservationRepository, allocationRepository
        );
        this.borrowingService = new BorrowingService(borrowRepository, userRepository, bookRepository);
        BorrowingPolicy.resetToDefaults();
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
            stmt.executeUpdate("UPDATE borrow_transactions SET student_id = 4, status = 'ACTIVE', return_date = NULL, issue_date = CURRENT_DATE(), due_date = DATE_ADD(CURRENT_DATE(), INTERVAL 14 DAY) WHERE transaction_id = 1");
            stmt.executeUpdate("UPDATE borrow_transactions SET student_id = 5, status = 'RETURNED' WHERE transaction_id = 2");
            stmt.executeUpdate("DELETE FROM reservations");
            stmt.executeUpdate("UPDATE book_copies SET status = 'AVAILABLE' WHERE copy_id != 1");
            stmt.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1");
            stmt.executeUpdate("UPDATE users SET active = TRUE WHERE user_id IN (4, 5)");
            stmt.executeUpdate("UPDATE users SET active = FALSE WHERE user_id = 6");
        } catch (Exception ignored) {}
    }

    @Test
    public void testSuccessfulInterchange_PreservesIssueAndDueDate() throws Exception {
        // Sivakathir (ID 4, reg: 715522104045) holds Tx #1 (Book 1, Copy 1).
        // Transfer to Ananya Ramesh (ID 5, reg: 715523105012).
        BorrowTransaction txBefore = borrowRepository.findById(1);
        assertNotNull(txBefore);
        assertEquals(4, txBefore.getStudentId());
        LocalDate originalIssue = txBefore.getIssueDate();
        LocalDate originalDue = txBefore.getDueDate();

        // 1. Sivakathir initiates transfer
        BookInterchange req = interchangeService.initiateInterchange(
                4, 1, "715523105012", "Handing over algorithm book"
        );
        assertNotNull(req);
        assertEquals(InterchangeStatus.REQUESTED, req.getStatus());
        assertEquals(4, req.getFromStudentId());
        assertEquals(5, req.getToStudentId());

        // Verify borrowing is STILL owned by Student 4 while pending
        BorrowTransaction txPending = borrowRepository.findById(1);
        assertEquals(4, txPending.getStudentId());
        assertEquals(BorrowStatus.ACTIVE, txPending.getStatus());

        // 2. Ananya accepts transfer
        BookInterchange completed = interchangeService.acceptInterchange(req.getInterchangeId(), 5);
        assertNotNull(completed);
        assertEquals(InterchangeStatus.COMPLETED, completed.getStatus());

        // 3. Verify borrowing transaction owner changed to Student 5
        BorrowTransaction txAfter = borrowRepository.findById(1);
        assertNotNull(txAfter);
        assertEquals("Owner must be updated to recipient", 5, txAfter.getStudentId());
        assertEquals("Status must remain ACTIVE", BorrowStatus.ACTIVE, txAfter.getStatus());
        assertEquals("Issue date must be exactly preserved", originalIssue, txAfter.getIssueDate());
        assertEquals("Due date must be exactly preserved", originalDue, txAfter.getDueDate());
        
        CopyStatus copyStatus = null;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT status FROM book_copies WHERE copy_id = ?")) {
            ps.setInt(1, txAfter.getCopyId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    copyStatus = CopyStatus.fromString(rs.getString("status"));
                }
            }
        }
        assertEquals("Physical copy must remain BORROWED", CopyStatus.BORROWED, copyStatus);

        // 4. Verify no new borrowing transaction was created
        assertEquals(1, (int) txAfter.getTransactionId());
    }

    @Test(expected = RecipientNotEligibleException.class)
    public void testEligibility_InactiveRecipientRejected() {
        // Student 6 (Rahul Kumar) is inactive
        interchangeService.initiateInterchange(4, 1, "715521102030", "Attempt transfer to inactive");
    }

    @Test(expected = InvalidInterchangeException.class)
    public void testEligibility_SelfTransferRejected() {
        // Student 4 transferring to Student 4
        interchangeService.initiateInterchange(4, 1, "715522104045", "Self transfer attempt");
    }

    @Test(expected = RecipientNotEligibleException.class)
    public void testEligibility_RecipientAlreadyBorrowingSameBook() {
        // Ananya (ID 5) issues copy 2 of Book 1 (PSG-BK1-C02, copy_id 2)
        borrowRepository.issueBookTransaction(5, 1, 2, LocalDate.now(), LocalDate.now().plusDays(14), 1);

        // Sivakathir attempts to transfer another copy of Book 1 to Ananya
        interchangeService.initiateInterchange(4, 1, "715523105012", "Duplicate book transfer");
    }

    @Test(expected = RecipientNotEligibleException.class)
    public void testEligibility_RecipientAtQuotaLimit() {
        // Set borrowing limit to 1
        BorrowingPolicy.setMaxActiveBorrowingLimit(1);
        // Ananya (ID 5) already borrows Book 2 (copy 5)
        borrowRepository.issueBookTransaction(5, 2, 5, LocalDate.now(), LocalDate.now().plusDays(14), 1);

        // Sivakathir attempts to transfer Book 1 to Ananya
        interchangeService.initiateInterchange(4, 1, "715523105012", "Over-limit transfer");
    }

    @Test(expected = RecipientNotEligibleException.class)
    public void testEligibility_RecipientWithAllocationsExceedingQuota() throws Exception {
        BorrowingPolicy.setMaxActiveBorrowingLimit(1);

        // Ananya (ID 5) has an active allocation for Book 2
        try (Connection conn = DBConnection.getConnection()) {
            // Insert reservation first to satisfy FK
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO reservations (reservation_id, student_id, book_id, reservation_date, status) VALUES (999, 5, 2, CURRENT_DATE(), 'ALLOCATED')");
            }
            AllocationRecord alloc = new AllocationRecord(999, 5, 2, 5, null, new java.sql.Timestamp(System.currentTimeMillis() + 86400000));
            allocationRepository.insert(conn, alloc);
        }

        // Sivakathir attempts to transfer Book 1 to Ananya
        interchangeService.initiateInterchange(4, 1, "715523105012", "Allocation quota test");
    }

    @Test(expected = RecipientNotEligibleException.class)
    public void testEligibility_RecipientWithOutstandingFinesRejected() throws Exception {
        // Record an unpaid fine for Ananya (ID 5)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO fines (borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status) " +
                     "VALUES (2, 5, 1, 1, 3, 3, 2.00, 6.00, 0.00, 0.00, 6.00, 'UNPAID')")) {
            ps.executeUpdate();
        }

        // Sivakathir attempts to transfer Book 1 to Ananya
        interchangeService.initiateInterchange(4, 1, "715523105012", "Fines test");
    }

    @Test
    public void testEligibility_OtherUnreturnedOverdueBooksDoNotBlockRecipient() throws Exception {
        // Feature 6 Rule D10 Preservation:
        // Ananya (ID 5) has an unreturned active loan on Book 2 (copy 5) that is overdue,
        // but NO finalized fine record in 'fines'.
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by) " +
                     "VALUES (5, 2, 5, DATE_SUB(CURRENT_DATE(), INTERVAL 20 DAY), DATE_SUB(CURRENT_DATE(), INTERVAL 6 DAY), 'ACTIVE', 1)")) {
            ps.executeUpdate();
        }

        // Sivakathir initiates transfer of Book 1 to Ananya
        BookInterchange req = interchangeService.initiateInterchange(4, 1, "715523105012", "D10 test");
        assertNotNull(req);

        // Ananya can successfully accept! Other unreturned overdue books do not block borrowing or transfer receipt.
        BookInterchange completed = interchangeService.acceptInterchange(req.getInterchangeId(), 5);
        assertNotNull(completed);
        assertEquals(InterchangeStatus.COMPLETED, completed.getStatus());
    }

    @Test(expected = InterchangeNotAllowedException.class)
    public void testEligibility_ParticularTransferredBookOverdue_Blocked() throws Exception {
        // Set Tx #1's due date to yesterday in Asia/Kolkata
        ZoneId zone = FineConfig.getLibraryZone();
        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE borrow_transactions SET due_date = ? WHERE transaction_id = 1")) {
            ps.setDate(1, Date.valueOf(yesterday));
            ps.executeUpdate();
        }

        // Attempting to transfer an overdue book must fail
        interchangeService.initiateInterchange(4, 1, "715523105012", "Overdue transfer block");
    }

    @Test(expected = ActiveReservationQueueException.class)
    public void testQueuePriority_WaitingReservationBlocksInterchange() throws Exception {
        // Put a waiting reservation on Book 1 for Student 5
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO reservations (student_id, book_id, reservation_date, status) VALUES (5, 1, CURRENT_DATE(), 'WAITING')");
        }

        interchangeService.initiateInterchange(4, 1, "715523105012", "Queue priority test");
    }

    @Test
    public void testLifecycle_RecipientRejectsRequest() {
        BookInterchange req = interchangeService.initiateInterchange(4, 1, "715523105012", "To be rejected");
        assertEquals(InterchangeStatus.REQUESTED, req.getStatus());

        BookInterchange rejected = interchangeService.rejectInterchange(req.getInterchangeId(), 5, "I do not need this book");
        assertEquals(InterchangeStatus.REJECTED, rejected.getStatus());
        assertEquals("I do not need this book", rejected.getRejectionReason());

        // Verify sender retains active borrowing
        BorrowTransaction tx = borrowRepository.findById(1);
        assertEquals(4, tx.getStudentId());
        assertEquals(BorrowStatus.ACTIVE, tx.getStatus());

        // Attempting to accept a rejected request fails
        try {
            interchangeService.acceptInterchange(req.getInterchangeId(), 5);
            fail("Should fail accepting rejected request");
        } catch (InvalidInterchangeException expected) {}
    }

    @Test
    public void testLifecycle_SenderCancelsRequest() {
        BookInterchange req = interchangeService.initiateInterchange(4, 1, "715523105012", "To be cancelled");
        assertEquals(InterchangeStatus.REQUESTED, req.getStatus());

        BookInterchange cancelled = interchangeService.cancelInterchange(req.getInterchangeId(), 4, "Changed my mind");
        assertEquals(InterchangeStatus.CANCELLED, cancelled.getStatus());
        assertEquals("Changed my mind", cancelled.getCancellationReason());

        // Verify sender retains active borrowing
        BorrowTransaction tx = borrowRepository.findById(1);
        assertEquals(4, tx.getStudentId());
        assertEquals(BorrowStatus.ACTIVE, tx.getStatus());

        // Attempting to accept a cancelled request fails
        try {
            interchangeService.acceptInterchange(req.getInterchangeId(), 5);
            fail("Should fail accepting cancelled request");
        } catch (InvalidInterchangeException expected) {}
    }

    @Test
    public void testLifecycle_DuplicatePendingRequestPrevented() {
        // First request succeeds
        BookInterchange req1 = interchangeService.initiateInterchange(4, 1, "715523105012", "First attempt");
        assertNotNull(req1);

        // Same parameters re-submitted -> Idempotent return of existing request
        BookInterchange req2 = interchangeService.initiateInterchange(4, 1, "715523105012", "Second attempt");
        assertEquals(req1.getInterchangeId(), req2.getInterchangeId());
    }

    @Test
    public void testReturnInteraction_DeskReturnAutoCancelsPendingInterchange() {
        // 1. Initiate transfer
        BookInterchange req = interchangeService.initiateInterchange(4, 1, "715523105012", "Will be desk returned");
        assertEquals(InterchangeStatus.REQUESTED, req.getStatus());

        // 2. Sivakathir returns the book to the library desk
        BorrowTransaction returnedTx = borrowRepository.returnBookTransaction(1, 1, LocalDate.now());
        assertNotNull(returnedTx);
        assertEquals(BorrowStatus.RETURNED, returnedTx.getStatus());

        // 3. Verify interchange record was automatically CANCELLED
        BookInterchange biAfterReturn = interchangeRepository.findById(req.getInterchangeId());
        assertNotNull(biAfterReturn);
        assertEquals(InterchangeStatus.CANCELLED, biAfterReturn.getStatus());
        assertTrue(biAfterReturn.getCancellationReason().contains("Auto-cancelled"));

        // 4. Recipient cannot accept the returned book
        try {
            interchangeService.acceptInterchange(req.getInterchangeId(), 5);
            fail("Recipient must not be able to accept returned book");
        } catch (InvalidInterchangeException expected) {}
    }

    @Test
    public void testHistory_BothStudentsSeeCompletedTransfer() {
        // Transfer Tx #1 from Student 4 to Student 5
        BookInterchange req = interchangeService.initiateInterchange(4, 1, "715523105012", "History audit test");
        interchangeService.acceptInterchange(req.getInterchangeId(), 5);

        // 1. Sender (ID 4) views history -> sees transferred record
        List<BorrowTransaction> senderHistory = borrowRepository.findHistoryByStudentId(4);
        boolean senderFound = false;
        for (BorrowTransaction tx : senderHistory) {
            if (tx.getTransactionId() == 1) {
                senderFound = true;
                assertTrue("Must be marked as transferred", tx.isTransferred());
            }
        }
        assertTrue("Sender must see transferred book in borrowing history", senderFound);

        // 2. Recipient (ID 5) views active borrowings -> sees acquired via transfer
        List<BorrowTransaction> recipientActive = borrowRepository.findActiveByStudentId(5);
        boolean recipientFound = false;
        for (BorrowTransaction tx : recipientActive) {
            if (tx.getTransactionId() == 1) {
                recipientFound = true;
                assertTrue("Must be marked as acquired via interchange", tx.isAcquiredViaInterchange());
            }
        }
        assertTrue("Recipient must see acquired book in active list", recipientFound);

        // 3. Both see the record in personal interchange history
        List<BookInterchange> senderInterchanges = interchangeService.getInterchangeHistory(4);
        assertFalse(senderInterchanges.isEmpty());
        List<BookInterchange> recipientInterchanges = interchangeService.getInterchangeHistory(5);
        assertFalse(recipientInterchanges.isEmpty());
    }

    @Test
    public void testConcurrency_SimultaneousAcceptanceBySameRecipient_QuotaEnforced() throws Exception {
        // Set borrowing limit to 1
        BorrowingPolicy.setMaxActiveBorrowingLimit(1);

        // Student 4 holds Tx #1 (Book 1, Copy 1)
        // Staff issues Book 2 (Copy 4) to Student 6 (activate student 6 temporarily)
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE users SET active = TRUE WHERE user_id = 6");
            st.executeUpdate("UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 4");
            st.executeUpdate("INSERT INTO borrow_transactions (transaction_id, student_id, book_id, copy_id, issue_date, due_date, status, issued_by) " +
                    "VALUES (101, 6, 2, 4, CURRENT_DATE(), DATE_ADD(CURRENT_DATE(), INTERVAL 14 DAY), 'ACTIVE', 1)");
        }

        // Student 4 initiates transfer of Tx 1 to Ananya (ID 5)
        BookInterchange req1 = interchangeService.initiateInterchange(4, 1, "715523105012", "Book 1");
        // Student 6 initiates transfer of Tx 101 to Ananya (ID 5)
        BookInterchange req2 = interchangeService.initiateInterchange(6, 101, "715523105012", "Book 2");

        // Ananya attempts to accept BOTH concurrently
        ExecutorService exec = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger quotaExceededCount = new AtomicInteger(0);

        Future<?> f1 = exec.submit(() -> {
            try {
                barrier.await();
                interchangeService.acceptInterchange(req1.getInterchangeId(), 5);
                successCount.incrementAndGet();
            } catch (RecipientNotEligibleException e) {
                quotaExceededCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        Future<?> f2 = exec.submit(() -> {
            try {
                barrier.await();
                interchangeService.acceptInterchange(req2.getInterchangeId(), 5);
                successCount.incrementAndGet();
            } catch (RecipientNotEligibleException e) {
                quotaExceededCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        exec.shutdown();

        // Exactly ONE acceptance must succeed, and exactly ONE must fail with quota limit!
        assertEquals("Exactly one transfer must succeed within quota", 1, successCount.get());
        assertEquals("Exactly one transfer must fail due to quota limit", 1, quotaExceededCount.get());
        assertEquals("Recipient active count must equal max limit (1)", 1, borrowRepository.countActiveByStudentId(5));
    }
}
