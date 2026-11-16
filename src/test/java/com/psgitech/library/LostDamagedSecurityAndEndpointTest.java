package com.psgitech.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.CaseStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.LossDamageCaseRepository;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.service.LossDamageService;
import com.psgitech.library.servlet.*;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Level 4 Security, RBAC, Cross-Tenant Isolation, and HTTP Response Code Tests for Feature 14.
 * Asserts 401 unauthenticated, 403 unauthorized, 404 not found, 422 unprocessable entity,
 * and cross-tenant resource isolation.
 */
public class LostDamagedSecurityAndEndpointTest {

    private LossDamageService lossDamageService;
    private BorrowRepository borrowRepository;
    private BookRepository bookRepository;
    private BorrowingService borrowingService;

    private Student studentAlpha;
    private Student studentBeta;
    private Librarian librarianUser;
    private Admin adminUser;

    private int bookId;
    private int copy1Id;
    private int copy2Id;
    private int txAlphaId;
    private int caseAlphaId;

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
            // Librarian
            librarianUser = new Librarian(2, "Test Librarian", "librarian@psgitech.ac.in", "hash", "9876543210", true, "LIB001", false);
            // Admin
            adminUser = new Admin(1, "Test Admin", "admin@psgitech.ac.in", "hash", "9876543210", true);

            // Student Alpha
            String insUser = "INSERT INTO users (name, email, password_hash, phone, role, register_number, department, `year`, active, must_change_password, email_verified) " +
                    "VALUES (?, ?, '$2a$10$e8w.xeqzE6fWzrq8J/5YqOSGjP/pA8u8QZcQdG0Ccm8t41X5u8gCe', '9876543210', 'STUDENT', ?, 'CSE', 3, 1, 0, 1)";
            int alphaId;
            try (PreparedStatement ps = conn.prepareStatement(insUser, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "F14 Alpha Student");
                ps.setString(2, "f14_alpha@psgitech.ac.in");
                ps.setString(3, "715523F1401");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    alphaId = rs.getInt(1);
                }
            }
            studentAlpha = new Student(alphaId, "F14 Alpha Student", "f14_alpha@psgitech.ac.in", "hash", "9876543210", true, "715523F1401", "CSE", 3);

            // Student Beta
            int betaId;
            try (PreparedStatement ps = conn.prepareStatement(insUser, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "F14 Beta Student");
                ps.setString(2, "f14_beta@psgitech.ac.in");
                ps.setString(3, "715523F1402");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    betaId = rs.getInt(1);
                }
            }
            studentBeta = new Student(betaId, "F14 Beta Student", "f14_beta@psgitech.ac.in", "hash", "9876543210", true, "715523F1402", "ECE", 3);

            // Book
            String insBook = "INSERT INTO books (isbn, title, author, publisher, publication_year, category, department, replacement_price) " +
                    "VALUES ('978-0-1414-8888-2', 'F14 Clean Architecture Sec', 'Robert Martin', 'Prentice', 2021, 'CS', 'CSE', 650.00)";
            try (PreparedStatement ps = conn.prepareStatement(insBook, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    bookId = rs.getInt(1);
                }
            }

            // Copy 1
            String insCopy = "INSERT INTO book_copies (book_id, copy_code, status) VALUES (?, ?, 'AVAILABLE')";
            try (PreparedStatement ps = conn.prepareStatement(insCopy, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, bookId);
                ps.setString(2, "PSG-F14-BK2-C01");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    copy1Id = rs.getInt(1);
                }
            }

            // Copy 2
            try (PreparedStatement ps = conn.prepareStatement(insCopy, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, bookId);
                ps.setString(2, "PSG-F14-BK2-C02");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    copy2Id = rs.getInt(1);
                }
            }
        }

        // Student Alpha borrows Copy 1
        BorrowTransaction tx = borrowingService.issueBook("715523F1401", copy1Id, librarianUser.getUserId());
        txAlphaId = tx.getTransactionId();

        // Student Alpha reports lost -> Case created
        LossDamageCase c = lossDamageService.reportLost(txAlphaId, studentAlpha.getUserId(), "Lost on bus", studentAlpha.getUserId(), "STUDENT");
        caseAlphaId = c.getCaseId();
    }

    private static class MockResponseHolder {
        int status = 200;
        final StringWriter stringWriter = new StringWriter();

        HttpServletResponse createProxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("setStatus".equals(name) && args.length >= 1) {
                            status = (Integer) args[0];
                            return null;
                        }
                        if ("getStatus".equals(name)) {
                            return status;
                        }
                        if ("getWriter".equals(name)) {
                            return new PrintWriter(stringWriter);
                        }
                        if ("setContentType".equals(name) || "setCharacterEncoding".equals(name) || "setHeader".equals(name)) {
                            return null;
                        }
                        return null;
                    }
            );
        }
    }

    private HttpServletRequest createMockRequest(Object sessionUser, String method, Map<String, String> params, String jsonBody) {
        Map<String, Object> sessionAttrs = new HashMap<>();
        if (sessionUser != null) {
            sessionAttrs.put("user", sessionUser);
        }

        HttpSession mockSession = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getAttribute".equals(name)) {
                        return sessionAttrs.get(args[0]);
                    }
                    return null;
                }
        );

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getMethod".equals(name)) {
                        return method != null ? method : "POST";
                    }
                    if ("getSession".equals(name)) {
                        return sessionUser != null ? mockSession : null;
                    }
                    if ("getParameter".equals(name)) {
                        return params != null ? params.get(args[0]) : null;
                    }
                    if ("getContentType".equals(name)) {
                        return jsonBody != null ? "application/json" : "application/x-www-form-urlencoded";
                    }
                    if ("getCharacterEncoding".equals(name)) {
                        return "UTF-8";
                    }
                    if ("getReader".equals(name)) {
                        String body = jsonBody != null ? jsonBody : "";
                        return new BufferedReader(new StringReader(body));
                    }
                    return null;
                }
        );
    }

    @Test
    public void testAnonymousAccessReturns401() throws Exception {
        // CaseReportServlet
        CaseReportServlet reportServlet = new CaseReportServlet();
        reportServlet.init();
        MockResponseHolder r1 = new MockResponseHolder();
        reportServlet.service(createMockRequest(null, "POST", null, "{\"transactionId\":" + txAlphaId + "}"), r1.createProxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, r1.status);

        // MyCasesServlet
        MyCasesServlet myServlet = new MyCasesServlet();
        myServlet.init();
        MockResponseHolder r2 = new MockResponseHolder();
        myServlet.service(createMockRequest(null, "GET", null, null), r2.createProxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, r2.status);

        // CaseListServlet
        CaseListServlet listServlet = new CaseListServlet();
        listServlet.init();
        MockResponseHolder r3 = new MockResponseHolder();
        listServlet.service(createMockRequest(null, "GET", null, null), r3.createProxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, r3.status);

        // CaseAssessServlet
        CaseAssessServlet assessServlet = new CaseAssessServlet();
        assessServlet.init();
        MockResponseHolder r4 = new MockResponseHolder();
        assessServlet.service(createMockRequest(null, "POST", null, "{\"caseId\":" + caseAlphaId + "}"), r4.createProxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, r4.status);

        // CaseOverrideServlet
        CaseOverrideServlet overrideServlet = new CaseOverrideServlet();
        overrideServlet.init();
        MockResponseHolder r5 = new MockResponseHolder();
        overrideServlet.service(createMockRequest(null, "POST", null, "{\"caseId\":" + caseAlphaId + ",\"newAmount\":400.00,\"reason\":\"test\"}"), r5.createProxy());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, r5.status);
    }

    @Test
    public void testStudentRoleForbiddenOnStaffEndpointsReturns403() throws Exception {
        // 1. CaseListServlet
        CaseListServlet listServlet = new CaseListServlet();
        listServlet.init();
        MockResponseHolder r1 = new MockResponseHolder();
        listServlet.service(createMockRequest(studentAlpha, "GET", null, null), r1.createProxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, r1.status);

        // 2. CaseAssessServlet
        CaseAssessServlet assessServlet = new CaseAssessServlet();
        assessServlet.init();
        MockResponseHolder r2 = new MockResponseHolder();
        assessServlet.service(createMockRequest(studentAlpha, "POST", null, "{\"caseId\":" + caseAlphaId + "}"), r2.createProxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, r2.status);

        // 3. CaseReplacementReceiveServlet
        CaseReplacementReceiveServlet recvServlet = new CaseReplacementReceiveServlet();
        recvServlet.init();
        MockResponseHolder r3 = new MockResponseHolder();
        recvServlet.service(createMockRequest(studentAlpha, "POST", null, "{\"caseId\":" + caseAlphaId + "}"), r3.createProxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, r3.status);

        // 4. CaseReplacementAcceptServlet
        CaseReplacementAcceptServlet acceptServlet = new CaseReplacementAcceptServlet();
        acceptServlet.init();
        MockResponseHolder r4 = new MockResponseHolder();
        acceptServlet.service(createMockRequest(studentAlpha, "POST", null, "{\"caseId\":" + caseAlphaId + ",\"scannedIsbn\":\"978-0-1414-8888-2\"}"), r4.createProxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, r4.status);

        // 5. CaseReplacementRejectServlet
        CaseReplacementRejectServlet rejectServlet = new CaseReplacementRejectServlet();
        rejectServlet.init();
        MockResponseHolder r5 = new MockResponseHolder();
        rejectServlet.service(createMockRequest(studentAlpha, "POST", null, "{\"caseId\":" + caseAlphaId + ",\"reason\":\"Damaged\"}"), r5.createProxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, r5.status);

        // 6. CaseConvertServlet
        CaseConvertServlet convertServlet = new CaseConvertServlet();
        convertServlet.init();
        MockResponseHolder r6 = new MockResponseHolder();
        convertServlet.service(createMockRequest(studentAlpha, "POST", null, "{\"caseId\":" + caseAlphaId + ",\"reason\":\"Changed mind\"}"), r6.createProxy());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, r6.status);
    }

    @Test
    public void testLibrarianRoleForbiddenOnAdminOverrideReturns403() throws Exception {
        CaseOverrideServlet overrideServlet = new CaseOverrideServlet();
        overrideServlet.init();
        MockResponseHolder resp = new MockResponseHolder();

        String body = "{\"caseId\":" + caseAlphaId + ",\"newAmount\":400.00,\"reason\":\"Librarian discount\"}";
        overrideServlet.service(createMockRequest(librarianUser, "POST", null, body), resp.createProxy());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, resp.status);
    }

    @Test
    public void testCrossTenantIsolation_StudentCannotReportOnOtherStudentLoanReturns403() throws Exception {
        // Student Beta attempts to report loss for Student Alpha's loan txAlphaId
        CaseReportServlet reportServlet = new CaseReportServlet();
        reportServlet.init();
        MockResponseHolder resp = new MockResponseHolder();

        String body = "{\"transactionId\":" + txAlphaId + ",\"note\":\"Beta reporting Alpha's book\"}";
        reportServlet.service(createMockRequest(studentBeta, "POST", null, body), resp.createProxy());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, resp.status);
    }

    @Test
    public void testCrossTenantIsolation_StudentCannotChooseOptionOnOtherStudentCaseReturns403() throws Exception {
        // First assess the case
        lossDamageService.assessLost(caseAlphaId, null, "Assessed", librarianUser.getUserId(), "LIBRARIAN");

        // Student Beta attempts to select payment option for Student Alpha's case
        CaseChooseServlet chooseServlet = new CaseChooseServlet();
        chooseServlet.init();
        MockResponseHolder resp = new MockResponseHolder();

        String body = "{\"caseId\":" + caseAlphaId + ",\"choice\":\"PAYMENT\"}";
        chooseServlet.service(createMockRequest(studentBeta, "POST", null, body), resp.createProxy());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, resp.status);
    }

    @Test
    public void testCrossTenantIsolation_MyCasesReturnsOnlyOwnCases() throws Exception {
        MyCasesServlet myServlet = new MyCasesServlet();
        myServlet.init();

        // Student Alpha calls my-cases
        MockResponseHolder rAlpha = new MockResponseHolder();
        myServlet.service(createMockRequest(studentAlpha, "GET", null, null), rAlpha.createProxy());
        assertEquals(HttpServletResponse.SC_OK, rAlpha.status);
        JsonObject jsonAlpha = JsonParser.parseString(rAlpha.stringWriter.toString()).getAsJsonObject();
        assertTrue(jsonAlpha.get("success").getAsBoolean());
        assertTrue(jsonAlpha.getAsJsonArray("cases").size() >= 1);

        // Student Beta calls my-cases (has 0 cases)
        MockResponseHolder rBeta = new MockResponseHolder();
        myServlet.service(createMockRequest(studentBeta, "GET", null, null), rBeta.createProxy());
        assertEquals(HttpServletResponse.SC_OK, rBeta.status);
        JsonObject jsonBeta = JsonParser.parseString(rBeta.stringWriter.toString()).getAsJsonObject();
        assertTrue(jsonBeta.get("success").getAsBoolean());
        assertEquals(0, jsonBeta.getAsJsonArray("cases").size());
    }

    @Test
    public void testInputValidation_MissingTransactionIdReturns400() throws Exception {
        CaseReportServlet reportServlet = new CaseReportServlet();
        reportServlet.init();
        MockResponseHolder resp = new MockResponseHolder();

        reportServlet.service(createMockRequest(studentAlpha, "POST", null, "{\"note\":\"No transaction ID\"}"), resp.createProxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, resp.status);
    }

    @Test
    public void testInputValidation_InvalidResolutionChoiceReturns400() throws Exception {
        lossDamageService.assessLost(caseAlphaId, null, "Assessed", librarianUser.getUserId(), "LIBRARIAN");

        CaseChooseServlet chooseServlet = new CaseChooseServlet();
        chooseServlet.init();
        MockResponseHolder resp = new MockResponseHolder();

        String body = "{\"caseId\":" + caseAlphaId + ",\"choice\":\"BITCOIN\"}";
        chooseServlet.service(createMockRequest(studentAlpha, "POST", null, body), resp.createProxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, resp.status);
    }

    @Test
    public void testInputValidation_IsbnMismatchOnAcceptReturns422() throws Exception {
        lossDamageService.assessLost(caseAlphaId, null, "Assessed", librarianUser.getUserId(), "LIBRARIAN");
        lossDamageService.chooseReplacement(caseAlphaId, studentAlpha.getUserId(), "STUDENT");
        lossDamageService.receiveReplacement(caseAlphaId, librarianUser.getUserId(), "LIBRARIAN");

        CaseReplacementAcceptServlet acceptServlet = new CaseReplacementAcceptServlet();
        acceptServlet.init();
        MockResponseHolder resp = new MockResponseHolder();

        String body = "{\"caseId\":" + caseAlphaId + ",\"scannedIsbn\":\"978-0-0000-0000-0\",\"condition\":\"NEW\"}";
        acceptServlet.service(createMockRequest(librarianUser, "POST", null, body), resp.createProxy());

        assertEquals(422, resp.status);
    }

    @Test
    public void testInputValidation_AdminOverrideMissingReasonOrNegativeAmountReturns400() throws Exception {
        lossDamageService.assessLost(caseAlphaId, null, "Assessed", librarianUser.getUserId(), "LIBRARIAN");

        CaseOverrideServlet overrideServlet = new CaseOverrideServlet();
        overrideServlet.init();

        // 1. Negative amount
        MockResponseHolder r1 = new MockResponseHolder();
        String bodyNeg = "{\"caseId\":" + caseAlphaId + ",\"newAmount\":-50.00,\"reason\":\"Negative test\"}";
        overrideServlet.service(createMockRequest(adminUser, "POST", null, bodyNeg), r1.createProxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, r1.status);

        // 2. Missing reason
        MockResponseHolder r2 = new MockResponseHolder();
        String bodyNoReason = "{\"caseId\":" + caseAlphaId + ",\"newAmount\":400.00,\"reason\":\"\"}";
        overrideServlet.service(createMockRequest(adminUser, "POST", null, bodyNoReason), r2.createProxy());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, r2.status);
    }
}
