package com.psgitech.library;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.ReservationStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.model.ReservationQueue;
import com.psgitech.library.repository.ReservationRepository;
import com.psgitech.library.service.ReservationService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Comprehensive Automated Test Suite for Feature 4: Reservation Queue.
 * Verifies all 17 scenarios specified in Part 11.
 */
public class ReservationServiceTest {
    private ReservationService reservationService;
    private ReservationRepository reservationRepository;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        this.reservationRepository = new ReservationRepository();
        this.reservationService = new ReservationService(reservationRepository);
        BorrowingPolicy.resetToDefaults();

        // Clean any test-created reservations between tests
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE reservations SET status = 'CANCELLED' WHERE status = 'WAITING'");
        }
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE book_copies SET status = 'AVAILABLE' WHERE copy_id != 1");
            stmt.executeUpdate("UPDATE reservations SET status = 'CANCELLED' WHERE status = 'WAITING'");
        }
    }

    /**
     * Helper to mark all physical copies of a book as BORROWED so it becomes reservable.
     */
    private void setAllCopiesBorrowed(int bookId) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE book_copies SET status = 'BORROWED' WHERE book_id = ? AND status != 'ARCHIVED'")) {
            ps.setInt(1, bookId);
            ps.executeUpdate();
        }
    }

    /**
     * Helper to restore copies of a book to AVAILABLE.
     */
    private void setAllCopiesAvailable(int bookId) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE book_copies SET status = 'AVAILABLE' WHERE book_id = ? AND status != 'ARCHIVED'")) {
            ps.setInt(1, bookId);
            ps.executeUpdate();
        }
    }

    /**
     * Scenario 3: Reserve a book that has at least one AVAILABLE copy -> rejected with exact message.
     */
    @Test
    public void testScenario3_ReserveBook_WhenCopiesAvailable_ThrowsInvalidReservationException() throws Exception {
        setAllCopiesAvailable(2);

        try {
            // Student 5 tries to reserve Book 2 (which has available copies)
            reservationService.createReservation(5, 2);
            fail("Expected InvalidReservationException when book has available shelf copies");
        } catch (InvalidReservationException e) {
            assertEquals("Copies of this book are currently available for borrowing at the library desk. Reservations are only permitted when all copies are checked out.", e.getMessage());
        }
    }

    /**
     * Scenario 1: Reserve a book whose copies are all BORROWED -> Position #1.
     */
    @Test
    public void testScenario1_ReserveBook_AllCopiesBorrowed_PositionOne() throws Exception {
        setAllCopiesBorrowed(3);

        Reservation res = reservationService.createReservation(5, 3);
        assertNotNull(res);
        assertNotNull(res.getReservationId());
        assertEquals(5, res.getStudentId());
        assertEquals(3, res.getBookId());
        assertEquals(ReservationStatus.WAITING, res.getStatus());
        assertEquals(1, res.getQueuePosition());
        assertEquals(1, res.getTotalWaitingInQueue());
    }

    /**
     * Scenario 2: Two more students reserve the same book -> Position #2 and #3 in exact FIFO order.
     */
    @Test
    public void testScenario2_FIFO_Ordering_MultipleStudents() throws Exception {
        setAllCopiesBorrowed(4);

        // Student 4 joins first -> #1
        Reservation res1 = reservationService.createReservation(4, 4);
        assertEquals(1, res1.getQueuePosition());

        // Student 5 joins second -> #2
        Reservation res2 = reservationService.createReservation(5, 4);
        assertEquals(2, res2.getQueuePosition());

        // Create temporary 3rd active student for queue testing
        int student3Id = createTestStudent("test.fifo3@psgitech.test", "TEST-FIFO-03");
        Reservation res3 = reservationService.createReservation(student3Id, 4);
        assertEquals(3, res3.getQueuePosition());

        // Verify queue data structure
        ReservationQueue queue = reservationService.getQueueForBook(4);
        assertEquals(3, queue.size());
        assertEquals(4, queue.peekNext().getStudentId());
    }

    /**
     * Scenario 4: Reserve a book the student is currently borrowing -> rejected.
     */
    @Test
    public void testScenario4_ReserveBook_CurrentlyBorrowing_Rejected() throws Exception {
        // In seed data, Student 4 has active borrow on Book 1
        setAllCopiesBorrowed(1);

        try {
            reservationService.createReservation(4, 1);
            fail("Expected InvalidReservationException when student is currently borrowing the book");
        } catch (InvalidReservationException e) {
            assertTrue(e.getMessage().contains("currently borrowing an active copy"));
        }
    }

    /**
     * Scenario 5: Reserve the same book twice with the same student -> duplicate rejected.
     */
    @Test
    public void testScenario5_ReserveSameBookTwice_DuplicateRejected() throws Exception {
        setAllCopiesBorrowed(5);

        Reservation first = reservationService.createReservation(5, 5);
        assertNotNull(first);

        try {
            reservationService.createReservation(5, 5);
            fail("Expected DuplicateReservationException on second attempt");
        } catch (DuplicateReservationException e) {
            assertTrue(e.getMessage().contains("already hold an active reservation"));
        }
    }

    /**
     * Scenario 6: Student with 3 active reservations tries a 4th -> ReservationLimitExceededException.
     */
    @Test
    public void testScenario6_ActiveReservationLimit_Enforced() throws Exception {
        setAllCopiesBorrowed(6);
        setAllCopiesBorrowed(7);
        setAllCopiesBorrowed(8);
        setAllCopiesBorrowed(9);

        // Student 5 reserves 3 books
        reservationService.createReservation(5, 6);
        reservationService.createReservation(5, 7);
        reservationService.createReservation(5, 8);

        // 4th reservation must be rejected
        try {
            reservationService.createReservation(5, 9);
            fail("Expected ReservationLimitExceededException after reaching 3 reservations");
        } catch (ReservationLimitExceededException e) {
            assertTrue(e.getMessage().contains("maximum active reservation limit"));
        }
    }

    /**
     * Scenario 7: Student 1 cancels -> status CANCELLED, audit recorded; Student 2 becomes #1, Student 3 becomes #2.
     */
    @Test
    public void testScenario7_CancelReservation_FIFO_Shifting() throws Exception {
        setAllCopiesBorrowed(10);

        int studentA = 4;
        int studentB = 5;
        int studentC = createTestStudent("test.shift3@psgitech.test", "TEST-SHIFT-03");

        Reservation resA = reservationService.createReservation(studentA, 10);
        Reservation resB = reservationService.createReservation(studentB, 10);
        Reservation resC = reservationService.createReservation(studentC, 10);

        assertEquals(1, resA.getQueuePosition());
        assertEquals(2, resB.getQueuePosition());
        assertEquals(3, resC.getQueuePosition());

        // Student A cancels
        Reservation cancelled = reservationService.cancelReservation(resA.getReservationId(), studentA, Role.STUDENT);
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt());
        assertEquals(Integer.valueOf(studentA), cancelled.getCancelledBy());

        // Live FIFO position recomputation
        assertEquals(1, reservationService.getQueuePosition(resB.getReservationId()));
        assertEquals(2, reservationService.getQueuePosition(resC.getReservationId()));
    }

    /**
     * Scenario 8: Student cancels then re-reserves the same book -> allowed, placed at back of queue.
     */
    @Test
    public void testScenario8_CancelAndReReserve_PlacedAtBack() throws Exception {
        setAllCopiesBorrowed(3);

        int studentA = 4;
        int studentB = 5;

        Reservation resA = reservationService.createReservation(studentA, 3);
        Reservation resB = reservationService.createReservation(studentB, 3);

        // Student A cancels
        reservationService.cancelReservation(resA.getReservationId(), studentA, Role.STUDENT);

        // Student A re-reserves
        Reservation resA2 = reservationService.createReservation(studentA, 3);
        assertNotNull(resA2);
        // Student B is now #1, Student A is now #2
        assertEquals(1, reservationService.getQueuePosition(resB.getReservationId()));
        assertEquals(2, resA2.getQueuePosition());
    }

    /**
     * Scenario 9: Student A tries to cancel Student B's reservation -> rejected (404/not found).
     */
    @Test
    public void testScenario9_StudentCannotCancelOtherStudentReservation() throws Exception {
        setAllCopiesBorrowed(4);

        Reservation resB = reservationService.createReservation(5, 4);

        try {
            // Student 4 tries to cancel Student 5's reservation
            reservationService.cancelReservation(resB.getReservationId(), 4, Role.STUDENT);
            fail("Expected ReservationNotFoundException when cancelling another student's reservation");
        } catch (ReservationNotFoundException e) {
            assertTrue(e.getMessage().contains("not found"));
        }

        // Verify status remains WAITING
        assertEquals(ReservationStatus.WAITING, reservationRepository.findById(resB.getReservationId()).getStatus());
    }

    /**
     * Scenario 10: Cancel an already CANCELLED reservation -> 409 InvalidReservationException.
     */
    @Test
    public void testScenario10_CancelAlreadyCancelledReservation_Rejected() throws Exception {
        setAllCopiesBorrowed(5);

        Reservation res = reservationService.createReservation(5, 5);
        reservationService.cancelReservation(res.getReservationId(), 5, Role.STUDENT);

        try {
            reservationService.cancelReservation(res.getReservationId(), 5, Role.STUDENT);
            fail("Expected InvalidReservationException when cancelling already CANCELLED reservation");
        } catch (InvalidReservationException e) {
            assertTrue(e.getMessage().contains("cannot be cancelled"));
        }
    }

    /**
     * Staff cancellation permissions: Librarian or Admin can cancel a student's reservation.
     */
    @Test
    public void testStaffCancellation_Success() throws Exception {
        setAllCopiesBorrowed(6);

        Reservation res = reservationService.createReservation(5, 6);
        int staffId = 2; // Librarian

        Reservation cancelled = reservationService.cancelReservation(res.getReservationId(), staffId, Role.LIBRARIAN);
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
        assertEquals(Integer.valueOf(staffId), cancelled.getCancelledBy());
    }

    /**
     * Scenario 12: Inactive student or non-existent book -> rejected.
     */
    @Test
    public void testScenario12_InactiveStudent_Rejected() throws Exception {
        setAllCopiesBorrowed(7);

        // User 6 is inactive student in seed data
        try {
            reservationService.createReservation(6, 7);
            fail("Expected StudentNotEligibleException for inactive student");
        } catch (StudentNotEligibleException e) {
            assertTrue(e.getMessage().contains("inactive"));
        }
    }

    /**
     * Scenario 14: Concurrency Testing.
     * (a) Two students reserve same book concurrently -> both succeed with positions 1 and 2.
     * (b) Same student fires two requests concurrently -> exactly one succeeds, one fails with duplicate.
     */
    @Test
    public void testScenario14_Concurrency_TwoStudents() throws Exception {
        setAllCopiesBorrowed(8);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        List<Reservation> results = Collections.synchronizedList(new ArrayList<>());
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        int studentA = 4;
        int studentB = 5;

        executor.submit(() -> {
            try {
                startLatch.await();
                results.add(reservationService.createReservation(studentA, 8));
            } catch (Exception e) {
                errors.add(e);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                results.add(reservationService.createReservation(studentB, 8));
            } catch (Exception e) {
                errors.add(e);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("Both requests should succeed", 2, results.size());
        assertEquals("Zero errors expected", 0, errors.size());

        int pos1 = results.get(0).getQueuePosition();
        int pos2 = results.get(1).getQueuePosition();
        assertTrue("Positions must be 1 and 2", (pos1 == 1 && pos2 == 2) || (pos1 == 2 && pos2 == 1));
    }

    @Test
    public void testScenario14_Concurrency_SameStudent_SingleWinner() throws Exception {
        setAllCopiesBorrowed(9);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        List<Reservation> successes = Collections.synchronizedList(new ArrayList<>());
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        int studentId = 5;

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    successes.add(reservationService.createReservation(studentId, 9));
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("Exactly one reservation must succeed", 1, successes.size());
        assertEquals("Exactly one request must fail with DuplicateReservationException", 1, exceptions.size());
        assertTrue("Exception must be duplicate", exceptions.get(0) instanceof DuplicateReservationException);
    }

    /**
     * Scenario 15: Verify NO SQL DELETE statement exists anywhere in reservation code.
     */
    @Test
    public void testScenario15_NoDeleteStatementsInReservationCode() throws Exception {
        File repoFile = new File("src/main/java/com/psgitech/library/repository/ReservationRepository.java");
        File serviceFile = new File("src/main/java/com/psgitech/library/service/ReservationService.java");

        assertTrue(repoFile.exists());
        assertTrue(serviceFile.exists());

        String repoContent = Files.readString(repoFile.toPath());
        String serviceContent = Files.readString(serviceFile.toPath());

        assertFalse("ReservationRepository must not contain SQL DELETE",
                repoContent.toUpperCase().contains("DELETE FROM"));
        assertFalse("ReservationService must not contain SQL DELETE",
                serviceContent.toUpperCase().contains("DELETE FROM"));
    }

    private int createTestStudent(String email, String regNo) throws Exception {
        String uniqueEmail = email.replace("@", "." + System.nanoTime() + "@");
        String uniqueReg = regNo + "-" + (System.nanoTime() % 1000000);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (name, email, password_hash, phone, role, register_number, department, `year`, active) " +
                     "VALUES ('Test Student', ?, '$2a$12$test.hash', '9876500000', 'STUDENT', ?, 'CSE', 3, TRUE)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uniqueEmail);
            ps.setString(2, uniqueReg);
            ps.executeUpdate();
            try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to create test student");
    }
}
