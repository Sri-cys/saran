package com.psgitech.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.Admin;
import com.psgitech.library.model.Librarian;
import com.psgitech.library.model.Student;
import com.psgitech.library.model.User;
import com.psgitech.library.service.AuthenticationService;
import com.psgitech.library.servlet.LoginServlet;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class StudentLoginRedirectTest {

    private LoginServlet servlet;
    private MockAuthService mockAuthService;

    static class MockAuthService extends AuthenticationService {
        User userToReturn;

        @Override
        public User login(String loginKey, String password) {
            if (userToReturn == null) {
                throw new RuntimeException("Mock user not configured");
            }
            return userToReturn;
        }

        @Override
        public String createRememberToken(int userId) {
            return "mock-token-xyz";
        }
    }

    @Before
    public void setUp() throws Exception {
        servlet = new LoginServlet();
        mockAuthService = new MockAuthService();

        Field authField = LoginServlet.class.getDeclaredField("authService");
        authField.setAccessible(true);
        authField.set(servlet, mockAuthService);

        Field gsonField = LoginServlet.class.getDeclaredField("gson");
        gsonField.setAccessible(true);
        gsonField.set(servlet, new com.google.gson.Gson());
    }

    @Test
    public void testStudentSignsInDirectlyToIndexHtml() throws Exception {
        Student student = new Student(1, "Aarav Sharma", "aarav@psgitech.ac.in", "hash", "9876543210", true, "21CS001", "CSE", 3);
        mockAuthService.userToReturn = student;

        Map<String, String> params = new HashMap<>();
        params.put("loginKey", "aarav@psgitech.ac.in");
        params.put("password", "Password123!");

        Map<String, Object> sessionAttrs = new HashMap<>();
        StringWriter responseWriter = new StringWriter();

        HttpServletRequest req = createMockRequest(params, sessionAttrs, "POST");
        HttpServletResponse resp = createMockResponse(responseWriter);

        servlet.service(req, resp);

        JsonObject json = JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
        assertTrue(json.get("success").getAsBoolean());
        String redirect = json.get("redirect").getAsString();

        // Must redirect to index.html (digital library Home page) instead of student-home.html
        assertTrue("Redirect should end with /index.html but was: " + redirect, redirect.endsWith("/index.html"));
        assertFalse("Redirect should not point to student-home.html", redirect.contains("student-home.html"));
    }

    @Test
    public void testStudentLoginHonorsSafeReturnUrlParameter() throws Exception {
        Student student = new Student(1, "Aarav Sharma", "aarav@psgitech.ac.in", "hash", "9876543210", true, "21CS001", "CSE", 3);
        mockAuthService.userToReturn = student;

        Map<String, String> params = new HashMap<>();
        params.put("loginKey", "aarav@psgitech.ac.in");
        params.put("password", "Password123!");
        params.put("returnUrl", "/browse.html?category=Computer+Science");

        Map<String, Object> sessionAttrs = new HashMap<>();
        StringWriter responseWriter = new StringWriter();

        HttpServletRequest req = createMockRequest(params, sessionAttrs, "POST");
        HttpServletResponse resp = createMockResponse(responseWriter);

        servlet.service(req, resp);

        JsonObject json = JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
        assertTrue(json.get("success").getAsBoolean());
        String redirect = json.get("redirect").getAsString();

        assertTrue(redirect.contains("/browse.html?category=Computer+Science"));
    }

    @Test
    public void testOpenRedirectAttemptFallsBackToIndexHtml() throws Exception {
        Student student = new Student(1, "Aarav Sharma", "aarav@psgitech.ac.in", "hash", "9876543210", true, "21CS001", "CSE", 3);
        mockAuthService.userToReturn = student;

        Map<String, String> params = new HashMap<>();
        params.put("loginKey", "aarav@psgitech.ac.in");
        params.put("password", "Password123!");
        params.put("returnUrl", "https://malicious-external-site.com");

        Map<String, Object> sessionAttrs = new HashMap<>();
        StringWriter responseWriter = new StringWriter();

        HttpServletRequest req = createMockRequest(params, sessionAttrs, "POST");
        HttpServletResponse resp = createMockResponse(responseWriter);

        servlet.service(req, resp);

        JsonObject json = JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
        String redirect = json.get("redirect").getAsString();

        // Must reject external URL and fall back to local index.html
        assertFalse(redirect.contains("malicious-external-site.com"));
        assertTrue(redirect.endsWith("/index.html"));
    }

    @Test
    public void testLibrarianRedirectsToLibrarianDashboard() throws Exception {
        Librarian lib = new Librarian(2, "Dr. Ramanathan", "librarian@psgitech.ac.in", "hash", "9876543211", true, "LIB101", false);
        mockAuthService.userToReturn = lib;

        Map<String, String> params = new HashMap<>();
        params.put("loginKey", "librarian@psgitech.ac.in");
        params.put("password", "Password123!");

        Map<String, Object> sessionAttrs = new HashMap<>();
        StringWriter responseWriter = new StringWriter();

        HttpServletRequest req = createMockRequest(params, sessionAttrs, "POST");
        HttpServletResponse resp = createMockResponse(responseWriter);

        servlet.service(req, resp);

        JsonObject json = JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
        String redirect = json.get("redirect").getAsString();
        assertTrue(redirect.endsWith("/librarian-dashboard.html"));
    }

    @Test
    public void testAdminRedirectsToAdminUsers() throws Exception {
        Admin admin = new Admin(3, "System Admin", "admin@psgitech.ac.in", "hash", "9876543212", true);
        mockAuthService.userToReturn = admin;

        Map<String, String> params = new HashMap<>();
        params.put("loginKey", "admin@psgitech.ac.in");
        params.put("password", "Password123!");

        Map<String, Object> sessionAttrs = new HashMap<>();
        StringWriter responseWriter = new StringWriter();

        HttpServletRequest req = createMockRequest(params, sessionAttrs, "POST");
        HttpServletResponse resp = createMockResponse(responseWriter);

        servlet.service(req, resp);

        JsonObject json = JsonParser.parseString(responseWriter.toString()).getAsJsonObject();
        String redirect = json.get("redirect").getAsString();
        assertTrue(redirect.endsWith("/admin-users.html"));
    }

    private HttpServletRequest createMockRequest(Map<String, String> params, Map<String, Object> sessionAttrs, String method) {
        HttpSession mockSession = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getAttribute".equals(name)) return sessionAttrs.get(args[0]);
                    if ("setAttribute".equals(name)) {
                        sessionAttrs.put((String) args[0], args[1]);
                        return null;
                    }
                    if ("getId".equals(name)) return "test-session-id";
                    return null;
                }
        );

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getMethod".equals(name)) return method;
                    if ("getParameter".equals(name)) return params.get(args[0]);
                    if ("getHeader".equals(name)) {
                        if ("X-Requested-With".equalsIgnoreCase((String) args[0])) return "XMLHttpRequest";
                        return null;
                    }
                    if ("getContentType".equals(name)) return "application/x-www-form-urlencoded";
                    if ("getContextPath".equals(name)) return "";
                    if ("getSession".equals(name)) return mockSession;
                    if ("getRemoteAddr".equals(name)) return "127.0.0.1";
                    if ("setCharacterEncoding".equals(name)) return null;
                    return null;
                }
        );
    }

    private HttpServletResponse createMockResponse(StringWriter writer) {
        PrintWriter pw = new PrintWriter(writer);
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getWriter".equals(name)) return pw;
                    if ("setContentType".equals(name) || "setCharacterEncoding".equals(name) || "setStatus".equals(name)) return null;
                    if ("addCookie".equals(name)) return null;
                    return null;
                }
        );
    }
}
