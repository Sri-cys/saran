package com.psgitech.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.Librarian;
import com.psgitech.library.model.Student;
import com.psgitech.library.model.User;
import com.psgitech.library.servlet.BookCategoriesServlet;
import com.psgitech.library.servlet.BookSuggestionServlet;
import com.psgitech.library.servlet.PersonalizedRecommendationsServlet;
import com.psgitech.library.servlet.PopularBooksServlet;
import com.psgitech.library.servlet.SimilarBooksServlet;
import com.psgitech.library.servlet.SmartDiscoveryServlet;
import com.psgitech.library.util.AutocompleteRateLimiter;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Layer 3 Security, RBAC, and REST Endpoint Validation Tests for Feature 10.
 * Verifies HTTP response codes, parameter validations (400), rate limiting (429),
 * 401/403 authorization rules, and 404 not found handling.
 */
public class SmartDiscoverySecurityAndEndpointTest {

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test must run on MySQL", "MySQL", prod);
            assertTrue("Schema must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));
        }
        AutocompleteRateLimiter.getInstance().reset();
    }

    private static class MockResponseHolder {
        int status = 200;
        final Map<String, String> headers = new HashMap<>();
        final StringWriter stringWriter = new StringWriter();

        HttpServletResponse createProxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("setStatus".equals(name) && args != null && args.length > 0) {
                            this.status = (Integer) args[0];
                            return null;
                        }
                        if ("sendError".equals(name) && args != null && args.length > 0) {
                            this.status = (Integer) args[0];
                            return null;
                        }
                        if ("getStatus".equals(name)) {
                            return this.status;
                        }
                        if ("setHeader".equals(name) && args != null && args.length >= 2) {
                            headers.put((String) args[0], String.valueOf(args[1]));
                            return null;
                        }
                        if ("getWriter".equals(name)) {
                            return new PrintWriter(stringWriter);
                        }
                        if ("setContentType".equals(name) || "setCharacterEncoding".equals(name)) {
                            return null;
                        }
                        return null;
                    }
            );
        }
    }

    private HttpServletRequest createMockRequest(Map<String, String> params, User sessionUser, String sessionId) {
        HttpSession mockSession = (sessionUser != null || sessionId != null) ? (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getAttribute".equals(name) && args != null && "user".equals(args[0])) {
                        return sessionUser;
                    }
                    if ("getId".equals(name)) {
                        return sessionId != null ? sessionId : "mock-session-id";
                    }
                    return null;
                }
        ) : null;

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) {
                        return "GET";
                    }
                    if ("getParameter".equals(name) && args != null && args.length > 0) {
                        return params != null ? params.get(args[0]) : null;
                    }
                    if ("getSession".equals(name)) {
                        return mockSession;
                    }
                    if ("getHeader".equals(name)) {
                        return null;
                    }
                    if ("getRemoteAddr".equals(name)) {
                        return "127.0.0.1";
                    }
                    return null;
                }
        );
    }

    @Test
    public void testPersonalized_AnonymousUser_Returns401() throws Exception {
        PersonalizedRecommendationsServlet servlet = new PersonalizedRecommendationsServlet();
        servlet.init();

        MockResponseHolder respHolder = new MockResponseHolder();
        HttpServletRequest req = createMockRequest(null, null, null);

        servlet.service(req, respHolder.createProxy());

        assertEquals(401, respHolder.status);
        JsonObject json = JsonParser.parseString(respHolder.stringWriter.toString()).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    public void testPersonalized_StaffUser_Returns403() throws Exception {
        PersonalizedRecommendationsServlet servlet = new PersonalizedRecommendationsServlet();
        servlet.init();

        Librarian librarian = new Librarian();
        librarian.setUserId(2);
        librarian.setRole(Role.LIBRARIAN);
        librarian.setName("Staff User");

        MockResponseHolder respHolder = new MockResponseHolder();
        HttpServletRequest req = createMockRequest(null, librarian, "staff-sess-1");

        servlet.service(req, respHolder.createProxy());

        assertEquals(403, respHolder.status);
        JsonObject json = JsonParser.parseString(respHolder.stringWriter.toString()).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("message").getAsString().toLowerCase().contains("student"));
    }

    @Test
    public void testPersonalized_StudentUser_Returns200() throws Exception {
        PersonalizedRecommendationsServlet servlet = new PersonalizedRecommendationsServlet();
        servlet.init();

        Student student = new Student();
        student.setUserId(5);
        student.setRole(Role.STUDENT);
        student.setName("Student Five");
        student.setDepartment("CSE");

        MockResponseHolder respHolder = new MockResponseHolder();
        HttpServletRequest req = createMockRequest(null, student, "student-sess-5");

        servlet.service(req, respHolder.createProxy());

        assertEquals(200, respHolder.status);
        JsonObject json = JsonParser.parseString(respHolder.stringWriter.toString()).getAsJsonObject();
        assertTrue(json.get("success").getAsBoolean());
        assertTrue(json.has("recommendations"));
    }

    @Test
    public void testSuggestions_RateLimiting_Enforces429() throws Exception {
        BookSuggestionServlet servlet = new BookSuggestionServlet();
        servlet.init();

        String clientSession = "flood-client-session-99";
        Map<String, String> params = new HashMap<>();
        params.put("q", "Java");

        // First 20 calls must succeed (200)
        for (int i = 1; i <= 20; i++) {
            MockResponseHolder resp = new MockResponseHolder();
            HttpServletRequest req = createMockRequest(params, null, clientSession);
            servlet.service(req, resp.createProxy());
            assertEquals("Request " + i + " should succeed", 200, resp.status);
        }

        // 21st call must return 429 Too Many Requests
        MockResponseHolder resp21 = new MockResponseHolder();
        HttpServletRequest req21 = createMockRequest(params, null, clientSession);
        servlet.service(req21, resp21.createProxy());
        assertEquals("Request beyond limit must return 429", 429, resp21.status);
    }

    @Test
    public void testDiscover_InvalidSort_Returns400() throws Exception {
        SmartDiscoveryServlet servlet = new SmartDiscoveryServlet();
        servlet.init();

        Map<String, String> params = new HashMap<>();
        params.put("sort", "NON_EXISTENT_SORT");

        MockResponseHolder resp = new MockResponseHolder();
        HttpServletRequest req = createMockRequest(params, null, "any-session");
        servlet.service(req, resp.createProxy());

        assertEquals(400, resp.status);
        JsonObject json = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    public void testDiscover_PageSizeExceedsMaximum_Returns400() throws Exception {
        SmartDiscoveryServlet servlet = new SmartDiscoveryServlet();
        servlet.init();

        Map<String, String> params = new HashMap<>();
        params.put("pageSize", "999");

        MockResponseHolder resp = new MockResponseHolder();
        HttpServletRequest req = createMockRequest(params, null, "any-session");
        servlet.service(req, resp.createProxy());

        assertEquals(400, resp.status);
        JsonObject json = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    public void testPopular_InvalidType_Returns400() throws Exception {
        PopularBooksServlet servlet = new PopularBooksServlet();
        servlet.init();

        Map<String, String> params = new HashMap<>();
        params.put("type", "random_unsupported");

        MockResponseHolder resp = new MockResponseHolder();
        HttpServletRequest req = createMockRequest(params, null, "any-session");
        servlet.service(req, resp.createProxy());

        assertEquals(400, resp.status);
        JsonObject json = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    public void testSimilar_MissingId_Returns400() throws Exception {
        SimilarBooksServlet servlet = new SimilarBooksServlet();
        servlet.init();

        Map<String, String> params = new HashMap<>();

        MockResponseHolder resp = new MockResponseHolder();
        HttpServletRequest req = createMockRequest(params, null, "any-session");
        servlet.service(req, resp.createProxy());

        assertEquals(400, resp.status);
    }

    @Test
    public void testSimilar_NonExistentBook_Returns404() throws Exception {
        SimilarBooksServlet servlet = new SimilarBooksServlet();
        servlet.init();

        Map<String, String> params = new HashMap<>();
        params.put("id", "999999");

        MockResponseHolder resp = new MockResponseHolder();
        HttpServletRequest req = createMockRequest(params, null, "any-session");
        servlet.service(req, resp.createProxy());

        assertEquals(404, resp.status);
        JsonObject json = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    public void testCategories_Returns200() throws Exception {
        BookCategoriesServlet servlet = new BookCategoriesServlet();
        servlet.init();

        MockResponseHolder resp = new MockResponseHolder();
        HttpServletRequest req = createMockRequest(null, null, "any-session");
        servlet.service(req, resp.createProxy());

        assertEquals(200, resp.status);
        JsonObject json = JsonParser.parseString(resp.stringWriter.toString()).getAsJsonObject();
        assertTrue(json.get("success").getAsBoolean());
        assertTrue(json.has("categories"));
    }
}
