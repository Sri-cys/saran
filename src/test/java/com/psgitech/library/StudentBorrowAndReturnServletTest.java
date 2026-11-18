package com.psgitech.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.Student;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.servlet.IssueBookServlet;
import com.psgitech.library.servlet.ReturnBookServlet;
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
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Endpoint and Access Control Tests for Student Self-Borrowing and Return Servlets.
 */
public class StudentBorrowAndReturnServletTest {

    private Student studentAlpha;
    private Student studentBeta;
    private UserRepository userRepo;
    private BorrowRepository borrowRepo;

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        this.userRepo = new UserRepository();
        this.borrowRepo = new BorrowRepository();

        studentAlpha = (Student) userRepo.findById(4); // Sivakathir
        studentBeta = (Student) userRepo.findById(5); // Ananya

        resetDatabaseState();
    }

    @After
    public void tearDown() {
        resetDatabaseState();
    }

    private void resetDatabaseState() {
        try (java.sql.Connection conn = DBConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
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
        } catch (java.sql.SQLException ignored) {}
    }

    private static class MockResponseHolder {
        int status = 200;
        StringWriter stringWriter = new StringWriter();

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
                        return new BufferedReader(new StringReader(jsonBody != null ? jsonBody : ""));
                    }
                    if ("getHeader".equals(name)) {
                        return "XMLHttpRequest";
                    }
                    return null;
                }
        );
    }

    @Test
    public void testStudentSelfBorrow_Success() throws Exception {
        IssueBookServlet servlet = new IssueBookServlet();
        servlet.init();

        MockResponseHolder resp = new MockResponseHolder();
        // Student Beta (Ananya) borrows Book 4
        String body = "{\"bookId\": 4}";
        servlet.service(createMockRequest(studentBeta, "POST", null, body), resp.createProxy());

        assertEquals(HttpServletResponse.SC_CREATED, resp.status);
        JsonObject json = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertTrue(json.get("success").getAsBoolean());
        assertNotNull(json.getAsJsonObject("data").get("transactionId"));
        assertNotNull(json.getAsJsonObject("data").get("dueDate"));
    }

    @Test
    public void testStudentSelfBorrow_Unauthenticated_Returns401() throws Exception {
        IssueBookServlet servlet = new IssueBookServlet();
        servlet.init();

        MockResponseHolder resp = new MockResponseHolder();
        String body = "{\"bookId\": 4}";
        servlet.service(createMockRequest(null, "POST", null, body), resp.createProxy());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, resp.status);
    }

    @Test
    public void testStudentReturnBook_OwnLoan_Success() throws Exception {
        ReturnBookServlet servlet = new ReturnBookServlet();
        servlet.init();

        MockResponseHolder resp = new MockResponseHolder();
        // Transaction 1 is an active loan owned by Student Alpha (Sivakathir, userId 4)
        String body = "{\"transactionId\": 1, \"condition\": \"GOOD\"}";
        servlet.service(createMockRequest(studentAlpha, "POST", null, body), resp.createProxy());

        assertEquals(HttpServletResponse.SC_OK, resp.status);
        JsonObject json = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertTrue(json.get("success").getAsBoolean());
    }

    @Test
    public void testStudentReturnBook_OtherStudentLoan_Forbidden403() throws Exception {
        ReturnBookServlet servlet = new ReturnBookServlet();
        servlet.init();

        MockResponseHolder resp = new MockResponseHolder();
        // Transaction 1 belongs to Alpha (userId 4). Student Beta (userId 5) attempts to return it.
        String body = "{\"transactionId\": 1, \"condition\": \"GOOD\"}";
        servlet.service(createMockRequest(studentBeta, "POST", null, body), resp.createProxy());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, resp.status);
        JsonObject json = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("message").getAsString().contains("own borrowed books"));
    }

    @Test
    public void testStudentReturnBook_Unauthenticated_Returns401() throws Exception {
        ReturnBookServlet servlet = new ReturnBookServlet();
        servlet.init();

        MockResponseHolder resp = new MockResponseHolder();
        String body = "{\"transactionId\": 1}";
        servlet.service(createMockRequest(null, "POST", null, body), resp.createProxy());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, resp.status);
    }
}
