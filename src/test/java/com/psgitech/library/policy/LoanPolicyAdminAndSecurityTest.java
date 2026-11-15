package com.psgitech.library.policy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.StaleVersionConflictException;
import com.psgitech.library.filter.AuthorizationFilter;
import com.psgitech.library.model.Admin;
import com.psgitech.library.model.Librarian;
import com.psgitech.library.model.LoanPolicyChangeRecord;
import com.psgitech.library.model.LoanPolicySettings;
import com.psgitech.library.model.LoanPolicyWindow;
import com.psgitech.library.model.PagedResult;
import com.psgitech.library.model.Student;
import com.psgitech.library.model.User;
import com.psgitech.library.service.LoanPolicyService;
import com.psgitech.library.servlet.LoanPolicyUpdateServlet;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Test suite verifying RBAC authorization rules, optimistic concurrency control (version 409 conflict),
 * change audit persistence, and admin service endpoints for Feature 12.
 */
public class LoanPolicyAdminAndSecurityTest {

    private LoanPolicyService policyService;
    private AuthorizationFilter authFilter;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test must run on MySQL", "MySQL", prod);
            assertTrue("Schema must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM loan_policy_changes");
                stmt.executeUpdate("DELETE FROM loan_policy_windows");
                stmt.executeUpdate("DELETE FROM book_loan_policy_state");
                stmt.executeUpdate("UPDATE loan_policy_settings SET enabled = TRUE, enter_threshold = 4, exit_threshold = 1, high_demand_days = 5, version = 1 WHERE id = 1");
            }
        }
        this.policyService = new LoanPolicyService();
        this.authFilter = new AuthorizationFilter();
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM loan_policy_changes");
            stmt.executeUpdate("DELETE FROM loan_policy_windows");
            stmt.executeUpdate("DELETE FROM book_loan_policy_state");
            stmt.executeUpdate("UPDATE loan_policy_settings SET enabled = TRUE, enter_threshold = 4, exit_threshold = 1, high_demand_days = 5, version = 1 WHERE id = 1");
        }
    }

    // =========================================================================
    // 1. RBAC & Access Control Tests (AuthorizationFilter)
    // =========================================================================

    @Test
    public void testAuthFilter_AnonymousAccess_Denied() throws Exception {
        // GET /admin/loan-policy -> 401
        MockResponse resp1 = new MockResponse();
        HttpServletRequest req1 = createMockRequest("GET", "/admin/loan-policy", null);
        AtomicBoolean chained1 = new AtomicBoolean(false);
        authFilter.doFilter(req1, resp1.createProxy(), (req, res) -> chained1.set(true));

        assertFalse("Chain should not proceed for unauthenticated user", chained1.get());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, resp1.status);

        // GET loan-policy.html -> redirect to login
        MockResponse resp2 = new MockResponse();
        HttpServletRequest req2 = createMockRequest("GET", "/loan-policy.html", null);
        AtomicBoolean chained2 = new AtomicBoolean(false);
        authFilter.doFilter(req2, resp2.createProxy(), (req, res) -> chained2.set(true));

        assertFalse(chained2.get());
        assertTrue("Redirect header or location set", resp2.redirectLocation != null && resp2.redirectLocation.contains("login.html"));
    }

    @Test
    public void testAuthFilter_StudentAccess_Forbidden() throws Exception {
        User student = createTestUser(10, Role.STUDENT, "Student User");

        // Student GET /admin/loan-policy -> 403 Forbidden
        MockResponse resp1 = new MockResponse();
        HttpServletRequest req1 = createMockRequest("GET", "/admin/loan-policy", student);
        AtomicBoolean chained1 = new AtomicBoolean(false);
        authFilter.doFilter(req1, resp1.createProxy(), (req, res) -> chained1.set(true));

        assertFalse(chained1.get());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, resp1.status);

        // Student POST /admin/loan-policy/update -> 403 Forbidden
        MockResponse resp2 = new MockResponse();
        HttpServletRequest req2 = createMockRequest("POST", "/admin/loan-policy/update", student);
        AtomicBoolean chained2 = new AtomicBoolean(false);
        authFilter.doFilter(req2, resp2.createProxy(), (req, res) -> chained2.set(true));

        assertFalse(chained2.get());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, resp2.status);
    }

    @Test
    public void testAuthFilter_LibrarianAccess_ViewAllowedModifyForbidden() throws Exception {
        User librarian = createTestUser(2, Role.LIBRARIAN, "Librarian User");

        // Librarian GET /admin/loan-policy -> Allowed
        MockResponse resp1 = new MockResponse();
        HttpServletRequest req1 = createMockRequest("GET", "/admin/loan-policy", librarian);
        AtomicBoolean chained1 = new AtomicBoolean(false);
        authFilter.doFilter(req1, resp1.createProxy(), (req, res) -> chained1.set(true));

        assertTrue("Librarian must be allowed to read policy settings", chained1.get());

        // Librarian POST /admin/loan-policy/update -> 403 Forbidden
        MockResponse resp2 = new MockResponse();
        HttpServletRequest req2 = createMockRequest("POST", "/admin/loan-policy/update", librarian);
        AtomicBoolean chained2 = new AtomicBoolean(false);
        authFilter.doFilter(req2, resp2.createProxy(), (req, res) -> chained2.set(true));

        assertFalse("Librarian must not be allowed to modify policy settings", chained2.get());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, resp2.status);
    }

    @Test
    public void testAuthFilter_AdminAccess_BothViewAndModifyAllowed() throws Exception {
        User admin = createTestUser(1, Role.ADMIN, "System Admin");

        // Admin GET /admin/loan-policy -> Allowed
        MockResponse resp1 = new MockResponse();
        HttpServletRequest req1 = createMockRequest("GET", "/admin/loan-policy", admin);
        AtomicBoolean chained1 = new AtomicBoolean(false);
        authFilter.doFilter(req1, resp1.createProxy(), (req, res) -> chained1.set(true));
        assertTrue("Admin must be allowed to read policy settings", chained1.get());

        // Admin POST /admin/loan-policy/update -> Allowed
        MockResponse resp2 = new MockResponse();
        HttpServletRequest req2 = createMockRequest("POST", "/admin/loan-policy/update", admin);
        AtomicBoolean chained2 = new AtomicBoolean(false);
        authFilter.doFilter(req2, resp2.createProxy(), (req, res) -> chained2.set(true));
        assertTrue("Admin must be allowed to modify policy settings", chained2.get());
    }

    // =========================================================================
    // 2. Optimistic Concurrency Control (OCC 409 Conflict) & Audit Log
    // =========================================================================

    @Test
    public void testOptimisticLocking_VersionConflictTriggersException() {
        LoanPolicySettings initial = policyService.getSettings();
        assertEquals(1, initial.getVersion());

        // Admin 1 updates to version 2 (with expectedVersion = 1) -> Succeeded
        LoanPolicySettings update1 = new LoanPolicySettings(true, 5, 2, 6, 2, 1, null);
        LoanPolicySettings result1 = policyService.updateSettings(update1, 1, 1, "Admin One");
        assertNotNull(result1);
        assertEquals(2, result1.getVersion());
        assertEquals(5, result1.getEnterThreshold());

        // Admin 2 attempts update with stale expectedVersion = 1 -> StaleVersionConflictException (409)
        LoanPolicySettings update2 = new LoanPolicySettings(true, 6, 1, 7, 2, 2, null);
        try {
            policyService.updateSettings(update2, 1, 2, "Admin Two");
            fail("Expected StaleVersionConflictException on version mismatch");
        } catch (StaleVersionConflictException e) {
            assertTrue(e.getMessage().contains("version conflict"));
        }

        // Verify current settings remain at version 2 (not overwritten by Admin 2)
        LoanPolicySettings current = policyService.getSettings();
        assertEquals(2, current.getVersion());
        assertEquals(5, current.getEnterThreshold());

        // Verify change history audit log has 1 entry
        PagedResult<LoanPolicyChangeRecord> history = policyService.getChangeHistory(1, 10);
        assertEquals(1, history.getTotalCount());
        LoanPolicyChangeRecord record = history.getItems().get(0);
        assertEquals(2, record.getVersion());
        assertEquals("Admin One", record.getChangedByName());
        assertEquals(4, record.getOldEnterThreshold());
        assertEquals(5, record.getNewEnterThreshold());
        assertEquals(1, record.getOldExitThreshold());
        assertEquals(2, record.getNewExitThreshold());
        assertEquals(5, record.getOldHighDemandDays());
        assertEquals(6, record.getNewHighDemandDays());
    }

    // =========================================================================
    // 3. Servlet Endpoint Execution Tests (HTTP 200, 400, 409)
    // =========================================================================

    @Test
    public void testLoanPolicyUpdateServlet_VersionConflict_ReturnsHttp409() throws Exception {
        LoanPolicyUpdateServlet servlet = new LoanPolicyUpdateServlet();
        servlet.init();

        // Prepare JSON payload with stale version 99
        JsonObject json = new JsonObject();
        json.addProperty("enabled", true);
        json.addProperty("enterThreshold", 5);
        json.addProperty("exitThreshold", 2);
        json.addProperty("highDemandDays", 6);
        json.addProperty("expectedVersion", 99); // Stale version

        User admin = createTestUser(1, Role.ADMIN, "System Admin");
        HttpServletRequest req = createMockRequestWithBody("POST", "/admin/loan-policy/update", admin, json.toString());
        MockResponse resp = new MockResponse();

        servlet.service(req, resp.createProxy());

        assertEquals("Version mismatch must return HTTP 409 Conflict", HttpServletResponse.SC_CONFLICT, resp.status);
        JsonObject resJson = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertFalse(resJson.get("success").getAsBoolean());
        assertTrue(resJson.get("message").getAsString().contains("version conflict"));
    }

    @Test
    public void testLoanPolicyUpdateServlet_ValidUpdate_ReturnsHttp200() throws Exception {
        LoanPolicyUpdateServlet servlet = new LoanPolicyUpdateServlet();
        servlet.init();

        JsonObject json = new JsonObject();
        json.addProperty("enabled", true);
        json.addProperty("enterThreshold", 5);
        json.addProperty("exitThreshold", 2);
        json.addProperty("highDemandDays", 6);
        json.addProperty("expectedVersion", 1); // Current version

        User admin = createTestUser(1, Role.ADMIN, "System Admin");
        HttpServletRequest req = createMockRequestWithBody("POST", "/admin/loan-policy/update", admin, json.toString());
        MockResponse resp = new MockResponse();

        servlet.service(req, resp.createProxy());

        assertEquals(HttpServletResponse.SC_OK, resp.status);
        JsonObject resJson = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertTrue(resJson.get("success").getAsBoolean());
        assertEquals(2, resJson.getAsJsonObject("data").get("version").getAsInt());
    }

    // =========================================================================
    // 4. Exam Window Management Tests (Stretch D12)
    // =========================================================================

    @Test
    public void testExamWindowCreationAndDeactivation() {
        LoanPolicyWindow window = new LoanPolicyWindow();
        window.setName("End Semester Exams Dec 2026");
        window.setStartDate(LocalDate.of(2026, 12, 1));
        window.setEndDate(LocalDate.of(2026, 12, 20));
        window.setEnterThresholdOverride(2);
        window.setCreatedBy(1);

        boolean created = policyService.createWindow(window);
        assertTrue(created);

        List<LoanPolicyWindow> windows = policyService.listWindows();
        assertFalse(windows.isEmpty());
        LoanPolicyWindow found = windows.get(0);
        assertEquals("End Semester Exams Dec 2026", found.getName());
        assertEquals(2, found.getEnterThresholdOverride());
        assertTrue(found.isActive());

        boolean deactivated = policyService.deactivateWindow(found.getWindowId());
        assertTrue(deactivated);

        List<LoanPolicyWindow> updated = policyService.listWindows();
        assertFalse(updated.get(0).isActive());
    }

    // =========================================================================
    // Helper Mocking Utilities
    // =========================================================================

    private User createTestUser(int id, Role role, String name) {
        User u;
        if (role == Role.ADMIN) {
            u = new Admin();
        } else if (role == Role.LIBRARIAN) {
            u = new Librarian();
        } else {
            u = new Student();
        }
        u.setUserId(id);
        u.setName(name);
        u.setEmail("test" + id + "@psgitech.ac.in");
        u.setRole(role);
        u.setActive(true);
        return u;
    }

    private HttpServletRequest createMockRequest(String method, String servletPath, User sessionUser) {
        return createMockRequestWithBody(method, servletPath, sessionUser, "");
    }

    private HttpServletRequest createMockRequestWithBody(String method, String servletPath, User sessionUser, String body) {
        HttpSession mockSession = (sessionUser != null) ? (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, m, args) -> "getAttribute".equals(m.getName()) && "user".equals(args[0]) ? sessionUser : null
        ) : null;

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getMethod".equals(name)) return method;
                    if ("getServletPath".equals(name)) return servletPath;
                    if ("getPathInfo".equals(name)) return null;
                    if ("getRequestURI".equals(name)) return servletPath;
                    if ("getContextPath".equals(name)) return "";
                    if ("getSession".equals(name)) return mockSession;
                    if ("getReader".equals(name)) return new BufferedReader(new StringReader(body));
                    if ("getParameter".equals(name)) return null;
                    if ("getContentType".equals(name)) return "application/json";
                    return null;
                }
        );
    }

    private static class MockResponse {
        int status = 200;
        String redirectLocation = null;
        final StringWriter stringWriter = new StringWriter();

        HttpServletResponse createProxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, m, args) -> {
                        String name = m.getName();
                        if ("setStatus".equals(name)) {
                            this.status = (int) args[0];
                            return null;
                        }
                        if ("sendError".equals(name)) {
                            this.status = (int) args[0];
                            return null;
                        }
                        if ("sendRedirect".equals(name)) {
                            this.redirectLocation = (String) args[0];
                            return null;
                        }
                        if ("getStatus".equals(name)) return this.status;
                        if ("getWriter".equals(name)) return new PrintWriter(stringWriter);
                        return null;
                    }
            );
        }
    }
}
