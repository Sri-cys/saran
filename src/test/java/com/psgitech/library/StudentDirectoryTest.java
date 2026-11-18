package com.psgitech.library;

import com.psgitech.library.exception.UserAlreadyExistsException;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.Student;
import com.psgitech.library.model.StudentMaster;
import com.psgitech.library.repository.StudentRepository;
import com.psgitech.library.service.StudentDirectoryService;
import com.psgitech.library.service.UserService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Automated test suite for Feature: Student Master Directory & Institutional Identity Integration.
 * Validates the 63 verified institutional student records, duplicate name preservation,
 * directory search and pagination, controlled registration lockdown, and peer discovery.
 */
public class StudentDirectoryTest {

    private StudentRepository studentRepository;
    private StudentDirectoryService directoryService;
    private UserService userService;

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        this.studentRepository = new StudentRepository();
        this.directoryService = new StudentDirectoryService(studentRepository);
        this.userService = new UserService();
    }

    @After
    public void tearDown() {
        // Clean up any test registrations if needed
    }

    @Test
    public void testExact63MasterStudentsSeeded() {
        Map<String, Integer> metrics = studentRepository.getMetrics();
        assertNotNull("Metrics map must not be null", metrics);
        int total = metrics.getOrDefault("total", 0);
        assertEquals("Master student directory must contain exactly 63 students", 63, total);
        assertEquals("All 63 students must have active account status by default", 63, (int) metrics.get("active"));
    }

    @Test
    public void testDuplicateStudentNamesPreserved() {
        // Institutional class list has three separate students named 'VISHAL M':
        // 715525104238, 715525104239, 715525104240
        StudentMaster v1 = studentRepository.findByRollNumber("715525104238");
        StudentMaster v2 = studentRepository.findByRollNumber("715525104239");
        StudentMaster v3 = studentRepository.findByRollNumber("715525104240");

        assertNotNull("Student 715525104238 must exist", v1);
        assertNotNull("Student 715525104239 must exist", v2);
        assertNotNull("Student 715525104240 must exist", v3);

        assertEquals("VISHAL M", v1.getFullName());
        assertEquals("VISHAL M", v2.getFullName());
        assertEquals("VISHAL M", v3.getFullName());

        assertNotEquals("Student IDs must be unique primary keys", v1.getStudentId(), v2.getStudentId());
        assertNotEquals("Student IDs must be unique primary keys", v2.getStudentId(), v3.getStudentId());
        assertNotEquals("Student IDs must be unique primary keys", v1.getStudentId(), v3.getStudentId());
    }

    @Test
    public void testDirectoryPaginationAndSearching() {
        // Page 1 with page size 20
        List<StudentMaster> page1 = studentRepository.search(null, null, null, 1, 20);
        assertEquals(20, page1.size());
        assertEquals("715525104189", page1.get(0).getPermanentRollNumber());

        // Page 4 with page size 20 (records 61 to 63)
        List<StudentMaster> page4 = studentRepository.search(null, null, null, 4, 20);
        assertEquals(3, page4.size());
        assertEquals("715525104251", page4.get(2).getPermanentRollNumber());

        // Search by Roll Number
        List<StudentMaster> rollSearch = studentRepository.search("715525104210", null, null, 1, 20);
        assertEquals(1, rollSearch.size());
        assertEquals("SRI SARAN KARTHI M S", rollSearch.get(0).getFullName());

        // Search by Name case-insensitive
        List<StudentMaster> nameSearch = studentRepository.search("sarvesh", null, null, 1, 20);
        assertTrue("Must find at least one student matching 'sarvesh'", nameSearch.size() >= 1);
        assertTrue(nameSearch.get(0).getFullName().contains("SARVESH"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegistrationRejectionForUnregisteredRollNumber() throws Exception {
        Student student = new Student();
        student.setName("Unknown Student");
        student.setEmail("unknown." + System.currentTimeMillis() + "@psgitech.ac.in");
        student.setRegisterNumber("715525104999"); // Not in 63 master list
        student.setDepartment("CSE");
        student.setYear(1);

        userService.registerStudent(student, "Psgitech@2026", "Psgitech@2026");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegistrationRejectionForNameMismatch() throws Exception {
        // 715525104191 is 'SARVESH P'
        Student student = new Student();
        student.setName("Completely Wrong Name");
        student.setEmail("sarvesh." + System.currentTimeMillis() + "@psgitech.ac.in");
        student.setRegisterNumber("715525104191");
        student.setDepartment("CSE");
        student.setYear(2);

        userService.registerStudent(student, "Psgitech@2026", "Psgitech@2026");
    }

    @Test
    public void testControlledRegistrationSuccessAndAtomicBinding() throws Exception {
        // Pick student 715525104192: SARVIN S
        String rollNo = "715525104192";
        StudentMaster before = studentRepository.findByRollNumber(rollNo);
        assertNotNull(before);

        if (!before.isRegistered()) {
            Student s = new Student();
            s.setName("SARVIN S"); // Matches master record exactly
            s.setEmail("sarvin." + System.currentTimeMillis() + "@psgitech.ac.in");
            s.setRegisterNumber(rollNo);
            s.setDepartment("CSE");
            s.setYear(2);
            s.setPhone("9876543299");

            Student registered = userService.registerStudent(s, "Sarvin@2026Pass", "Sarvin@2026Pass");
            assertNotNull(registered);
            assertTrue(registered.getUserId() > 0);

            // Verify master record was atomically updated and bound
            StudentMaster after = studentRepository.findByRollNumber(rollNo);
            assertNotNull(after);
            assertEquals("REGISTERED", after.getRegistrationStatus());
            assertEquals(Integer.valueOf(registered.getUserId()), after.getUserId());
        }
    }

    @Test(expected = UserAlreadyExistsException.class)
    public void testDuplicateRegistrationRejection() throws Exception {
        // Ensure student 715525104193 (SATHVIKA D V) is registered
        String rollNo = "715525104193";
        StudentMaster master = studentRepository.findByRollNumber(rollNo);
        assertNotNull(master);

        if (!master.isRegistered()) {
            Student s = new Student();
            s.setName("SATHVIKA D V");
            s.setEmail("sathvika." + System.currentTimeMillis() + "@psgitech.ac.in");
            s.setRegisterNumber(rollNo);
            s.setDepartment("CSE");
            s.setYear(2);
            userService.registerStudent(s, "Sathvika@2026P", "Sathvika@2026P");
        }

        // Attempting to register again must throw UserAlreadyExistsException
        Student duplicate = new Student();
        duplicate.setName("SATHVIKA D V");
        duplicate.setEmail("another." + System.currentTimeMillis() + "@psgitech.ac.in");
        duplicate.setRegisterNumber(rollNo);
        duplicate.setDepartment("CSE");
        duplicate.setYear(2);

        userService.registerStudent(duplicate, "Sathvika@2026P", "Sathvika@2026P");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInactiveStudentRegistrationRejection() throws Exception {
        String rollNo = "715525104194"; // SATHYAPRIYA S
        // Temporarily mark student as INACTIVE
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE students SET account_status = 'INACTIVE' WHERE permanent_roll_number = ?")) {
            ps.setString(1, rollNo);
            ps.executeUpdate();
        }

        try {
            Student s = new Student();
            s.setName("SATHYAPRIYA S");
            s.setEmail("sathyapriya." + System.currentTimeMillis() + "@psgitech.ac.in");
            s.setRegisterNumber(rollNo);
            s.setDepartment("CSE");
            s.setYear(2);

            userService.registerStudent(s, "Sathya@2026Pass", "Sathya@2026Pass");
        } finally {
            // Restore ACTIVE status
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE students SET account_status = 'ACTIVE' WHERE permanent_roll_number = ?")) {
                ps.setString(1, rollNo);
                ps.executeUpdate();
            }
        }
    }

    @Test
    public void testVerifyRollNumberService() {
        Map<String, Object> validRes = directoryService.verifyRollNumber("715525104210");
        assertTrue((Boolean) validRes.get("valid"));
        assertTrue((Boolean) validRes.get("existsInMaster"));
        assertEquals("SRI SARAN KARTHI M S", validRes.get("fullName"));

        Map<String, Object> invalidRes = directoryService.verifyRollNumber("000000000000");
        assertFalse((Boolean) invalidRes.get("valid"));
        assertFalse((Boolean) invalidRes.get("existsInMaster"));
    }

    @Test
    public void testPeerHoldingsLookup() {
        // User 28 (Sri Saran) has permanent roll number 715525104210
        StudentMaster profile = directoryService.getStudentProfile("715525104210");
        assertNotNull(profile);
        assertEquals("SRI SARAN KARTHI M S", profile.getFullName());
        assertNotNull(profile.getActiveBorrowings());
    }
}
