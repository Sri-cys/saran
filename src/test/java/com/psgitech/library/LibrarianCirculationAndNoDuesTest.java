package com.psgitech.library;

import com.psgitech.library.dto.librarian.NoDuesSlipDTO;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.service.ActivityLogService;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.service.LibrarianStudentService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class LibrarianCirculationAndNoDuesTest {

    private BorrowingService borrowingService;
    private BorrowRepository borrowRepository;
    private LibrarianStudentService studentService;
    private ActivityLogService activityLogService;

    @Before
    public void setUp() {
        this.borrowingService = new BorrowingService();
        this.borrowRepository = new BorrowRepository();
        this.studentService = new LibrarianStudentService();
        this.activityLogService = new ActivityLogService();
    }

    @Test
    public void testCirculationListQueries() {
        List<BorrowTransaction> active = borrowingService.getAllActiveBorrowings(null);
        assertNotNull("Active borrowings must not be null", active);

        List<BorrowTransaction> dueSoon = borrowRepository.findDueSoonTransactions();
        assertNotNull("Due soon transactions must not be null", dueSoon);

        List<BorrowTransaction> overdue = borrowingService.getOverdueBorrowings();
        assertNotNull("Overdue list must not be null", overdue);

        List<BorrowTransaction> recentIssued = borrowRepository.findRecentlyIssuedTransactions(10);
        assertNotNull("Recent issued list must not be null", recentIssued);

        List<BorrowTransaction> recentReturned = borrowRepository.findRecentlyReturnedTransactions(10);
        assertNotNull("Recent returned list must not be null", recentReturned);
    }

    @Test
    public void testStudentDirectoryAndFilters() {
        List<Map<String, Object>> students = studentService.getStudents(1, 10, "", "ACTIVE");
        assertNotNull(students);
        int count = studentService.countStudents("", "ACTIVE");
        assertTrue(count >= students.size());

        if (!students.isEmpty()) {
            int studentId = (Integer) students.get(0).get("userId");
            Map<String, Object> profile = studentService.getStudentProfile(studentId);
            assertNotNull(profile);
            assertTrue(profile.containsKey("activeLoans"));
            assertTrue(profile.containsKey("borrowingHistory"));
            assertTrue(profile.containsKey("fines"));
            assertTrue(profile.containsKey("activeReservations"));
        }
    }

    @Test
    public void testNoDuesClearanceSlipGeneration() {
        List<Map<String, Object>> students = studentService.getStudents(1, 5, "", "");
        if (!students.isEmpty()) {
            int studentId = (Integer) students.get(0).get("userId");
            NoDuesSlipDTO slip = studentService.generateNoDuesSlip(studentId, 2, "Dr. K. Ramanathan", "127.0.0.1");
            assertNotNull("No dues slip DTO must not be null", slip);
            assertNotNull("Certificate reference must be generated", slip.getCertificateReference());
            assertTrue(slip.getCertificateReference().startsWith("PSG-ND-"));
            assertNotNull("Issue timestamp must be present", slip.getIssuedTimestamp());
            assertEquals(2, slip.getIssuedByStaffId());
            assertEquals("Dr. K. Ramanathan", slip.getIssuedByStaffName());
        }
    }

    @Test
    public void testStudentStatusToggleRequiresMandatoryReason() {
        List<Map<String, Object>> students = studentService.getStudents(1, 1, "", "");
        if (!students.isEmpty()) {
            int studentId = (Integer) students.get(0).get("userId");
            try {
                studentService.toggleStudentStatus(studentId, false, "", 2, "127.0.0.1");
                fail("Empty reason must throw ValidationException");
            } catch (ValidationException e) {
                assertTrue(e.getMessage().contains("Mandatory reason is required"));
            }
        }
    }

    @Test
    public void testActivityLoggingAndSecurityRedaction() {
        activityLogService.logEvent(
                "TEST_CIRCULATION_AUDIT", 2, "LIBRARIAN", "COPY", "1",
                "Routine inventory check completed", "127.0.0.1"
        );

        List<Map<String, Object>> logs = activityLogService.getLogs(1, 10, "TEST_CIRCULATION_AUDIT", null, null, null, null);
        assertNotNull(logs);
        assertFalse(logs.isEmpty());
        assertEquals("TEST_CIRCULATION_AUDIT", logs.get(0).get("eventType"));

        // Redaction verification: passwords and OTPs must never be persisted in activity log details
        activityLogService.logEvent(
                "TEST_SECURITY_REDACTION", 2, "LIBRARIAN", "USER", "10",
                "Attempted password update with new secret_password_123", "127.0.0.1"
        );
        List<Map<String, Object>> redactedLogs = activityLogService.getLogs(1, 5, "TEST_SECURITY_REDACTION", null, null, null, null);
        assertFalse(redactedLogs.isEmpty());
        assertEquals("[REDACTED_SECURITY_DATA]", redactedLogs.get(0).get("actionDetails"));
    }
}
