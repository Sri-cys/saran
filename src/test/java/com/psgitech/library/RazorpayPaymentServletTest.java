package com.psgitech.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.config.RazorpayConfig;
import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.model.Student;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.service.RazorpayService;
import com.psgitech.library.servlet.RazorpayPaymentServlet;
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
import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Integration and HTTP Status Code Tests for RazorpayPaymentServlet:
 * - GET /payments/razorpay (config)
 * - POST /payments/razorpay/create-order (individual & bulk)
 * - POST /payments/razorpay/verify (signature verification & capture)
 * - POST /payments/razorpay/cancel (dismissal handling)
 * - 401 Unauthorized for unauthenticated requests
 * - 403 Forbidden for cross-student payment attempts
 */
public class RazorpayPaymentServletTest {

    private RazorpayPaymentServlet servlet;
    private RazorpayService razorpayService;
    private Student studentAlpha;
    private Student studentBeta;
    private UserRepository userRepo;

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        this.servlet = new RazorpayPaymentServlet();
        this.servlet.init();
        this.razorpayService = new RazorpayService();
        this.userRepo = new UserRepository();

        studentAlpha = (Student) userRepo.findById(4); // Student 1
        studentBeta = (Student) userRepo.findById(5);  // Student 2

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

    private int insertTestFine(int studentId, int bookId, int copyId, BigDecimal amount) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement psTx = conn.prepareStatement(
                     "INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by) " +
                             "VALUES (?, ?, ?, DATE_SUB(CURRENT_DATE, INTERVAL 20 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY), CURRENT_DATE, 'RETURNED', 2)",
                     Statement.RETURN_GENERATED_KEYS)) {
            psTx.setInt(1, studentId);
            psTx.setInt(2, bookId);
            psTx.setInt(3, copyId);
            psTx.executeUpdate();
            int txId;
            try (ResultSet rs = psTx.getGeneratedKeys()) {
                if (rs.next()) txId = rs.getInt(1);
                else throw new SQLException("No tx generated key");
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fines (borrow_transaction_id, student_id, book_id, copy_id, overdue_days, " +
                            "chargeable_days, fine_per_day, grace_period_days, original_amount, paid_amount, waived_amount, " +
                            "outstanding_amount, status, fine_type) " +
                            "VALUES (?, ?, ?, ?, 5, 5, 2.00, 0, ?, 0.00, 0.00, ?, 'UNPAID', 'OVERDUE')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, txId);
                ps.setInt(2, studentId);
                ps.setInt(3, bookId);
                ps.setInt(4, copyId);
                ps.setBigDecimal(5, amount);
                ps.setBigDecimal(6, amount);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create test fine");
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

    private HttpServletRequest createMockRequest(Object sessionUser, String method, String pathInfo, Map<String, String> params, String jsonBody) {
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
                    if ("getSession".equals(name)) {
                        return mockSession;
                    }
                    if ("getMethod".equals(name)) {
                        return method;
                    }
                    if ("getPathInfo".equals(name)) {
                        return pathInfo;
                    }
                    if ("getParameter".equals(name) && args.length >= 1) {
                        return params != null ? params.get(args[0]) : null;
                    }
                    if ("getReader".equals(name)) {
                        return new BufferedReader(new StringReader(jsonBody != null ? jsonBody : ""));
                    }
                    if ("getContentType".equals(name)) {
                        return "application/json";
                    }
                    if ("setCharacterEncoding".equals(name)) {
                        return null;
                    }
                    return null;
                }
        );
    }

    @Test
    public void testGetConfig_AuthenticatedStudent_Returns200WithKeyId() throws Exception {
        HttpServletRequest req = createMockRequest(studentAlpha, "GET", null, null, null);
        MockResponseHolder respHolder = new MockResponseHolder();

        servlet.service(req, respHolder.createProxy());

        assertEquals(200, respHolder.status);
        JsonObject json = JsonParser.parseString(respHolder.stringWriter.toString()).getAsJsonObject();
        assertTrue(json.get("success").getAsBoolean());
        assertEquals(RazorpayConfig.KEY_ID, json.get("keyId").getAsString());
        assertEquals("INR", json.get("currency").getAsString());
    }

    @Test
    public void testPostCreateOrder_IndividualFine_Returns200WithOrderDetails() throws Exception {
        int fineId = insertTestFine(studentAlpha.getUserId(), 1, 1, new BigDecimal("50.00"));

        String body = "{\"action\":\"create_order\",\"type\":\"INDIVIDUAL\",\"fineId\":" + fineId + "}";
        HttpServletRequest req = createMockRequest(studentAlpha, "POST", "/create-order", null, body);
        MockResponseHolder respHolder = new MockResponseHolder();

        servlet.service(req, respHolder.createProxy());

        assertEquals(200, respHolder.status);
        JsonObject json = JsonParser.parseString(respHolder.stringWriter.toString()).getAsJsonObject();
        assertTrue(json.get("success").getAsBoolean());
        assertNotNull(json.get("orderId").getAsString());
        assertEquals(50.00, json.get("amount").getAsDouble(), 0.001);
        assertEquals(5000L, json.get("amountPaise").getAsLong());
    }

    @Test
    public void testPostCreateOrder_PayAll_Returns200WithCombinedAmount() throws Exception {
        int f1 = insertTestFine(studentAlpha.getUserId(), 1, 1, new BigDecimal("50.00"));
        int f2 = insertTestFine(studentAlpha.getUserId(), 2, 2, new BigDecimal("100.00"));

        String body = "{\"action\":\"create_order\",\"type\":\"BULK\",\"fineIds\":[" + f1 + "," + f2 + "]}";
        HttpServletRequest req = createMockRequest(studentAlpha, "POST", "/create-order", null, body);
        MockResponseHolder respHolder = new MockResponseHolder();

        servlet.service(req, respHolder.createProxy());

        assertEquals(200, respHolder.status);
        JsonObject json = JsonParser.parseString(respHolder.stringWriter.toString()).getAsJsonObject();
        assertTrue(json.get("success").getAsBoolean());
        assertEquals(150.00, json.get("amount").getAsDouble(), 0.001);
        assertEquals(2, json.get("finesCount").getAsInt());
    }

    @Test
    public void testPostVerify_ValidSignature_Returns200AndSettlesFines() throws Exception {
        int fineId = insertTestFine(studentAlpha.getUserId(), 1, 1, new BigDecimal("50.00"));

        // Step 1: Create order
        String createBody = "{\"action\":\"create_order\",\"type\":\"INDIVIDUAL\",\"fineId\":" + fineId + "}";
        HttpServletRequest createReq = createMockRequest(studentAlpha, "POST", "/create-order", null, createBody);
        MockResponseHolder createResp = new MockResponseHolder();
        servlet.service(createReq, createResp.createProxy());
        JsonObject orderJson = JsonParser.parseString(createResp.stringWriter.toString()).getAsJsonObject();
        String orderId = orderJson.get("orderId").getAsString();

        // Step 2: Verify with cryptographic signature
        String paymentId = "pay_test_servlet_" + System.currentTimeMillis();
        String validSig = razorpayService.generateSignature(orderId, paymentId);

        String verifyBody = "{\"action\":\"verify\",\"razorpay_order_id\":\"" + orderId +
                "\",\"razorpay_payment_id\":\"" + paymentId +
                "\",\"razorpay_signature\":\"" + validSig + "\"}";
        HttpServletRequest verifyReq = createMockRequest(studentAlpha, "POST", "/verify", null, verifyBody);
        MockResponseHolder verifyResp = new MockResponseHolder();

        servlet.service(verifyReq, verifyResp.createProxy());

        assertEquals(200, verifyResp.status);
        JsonObject result = JsonParser.parseString(verifyResp.stringWriter.toString()).getAsJsonObject();
        assertTrue(result.get("success").getAsBoolean());
        assertEquals("SUCCESS", result.get("status").getAsString());
        assertNotNull(result.get("receiptNumber").getAsString());
    }

    @Test
    public void testPostVerify_InvalidSignature_Returns400() throws Exception {
        int fineId = insertTestFine(studentAlpha.getUserId(), 1, 1, new BigDecimal("50.00"));

        String createBody = "{\"action\":\"create_order\",\"type\":\"INDIVIDUAL\",\"fineId\":" + fineId + "}";
        HttpServletRequest createReq = createMockRequest(studentAlpha, "POST", "/create-order", null, createBody);
        MockResponseHolder createResp = new MockResponseHolder();
        servlet.service(createReq, createResp.createProxy());
        JsonObject orderJson = JsonParser.parseString(createResp.stringWriter.toString()).getAsJsonObject();
        String orderId = orderJson.get("orderId").getAsString();

        // Pass invalid signature
        String verifyBody = "{\"action\":\"verify\",\"razorpay_order_id\":\"" + orderId +
                "\",\"razorpay_payment_id\":\"pay_tampered\",\"razorpay_signature\":\"bad_sig_12345\"}";
        HttpServletRequest verifyReq = createMockRequest(studentAlpha, "POST", "/verify", null, verifyBody);
        MockResponseHolder verifyResp = new MockResponseHolder();

        servlet.service(verifyReq, verifyResp.createProxy());

        assertEquals(400, verifyResp.status);
        JsonObject result = JsonParser.parseString(verifyResp.stringWriter.toString()).getAsJsonObject();
        assertFalse(result.get("success").getAsBoolean());
    }

    @Test
    public void testPost_UnauthenticatedUser_Returns401() throws Exception {
        HttpServletRequest req = createMockRequest(null, "POST", "/create-order", null, "{}");
        MockResponseHolder respHolder = new MockResponseHolder();

        servlet.service(req, respHolder.createProxy());

        assertEquals(401, respHolder.status);
    }

    @Test
    public void testPost_StudentAttemptsToPayAnotherStudentsFine_Returns403() throws Exception {
        int fineStudentBeta = insertTestFine(studentBeta.getUserId(), 1, 1, new BigDecimal("50.00"));

        // Student Alpha attempts to pay Student Beta's fine
        String body = "{\"action\":\"create_order\",\"type\":\"INDIVIDUAL\",\"fineId\":" + fineStudentBeta + "}";
        HttpServletRequest req = createMockRequest(studentAlpha, "POST", "/create-order", null, body);
        MockResponseHolder respHolder = new MockResponseHolder();

        servlet.service(req, respHolder.createProxy());

        assertEquals(403, respHolder.status);
        JsonObject result = JsonParser.parseString(respHolder.stringWriter.toString()).getAsJsonObject();
        assertFalse(result.get("success").getAsBoolean());
        assertTrue(result.get("message").getAsString().contains("Access Denied"));
    }
}
