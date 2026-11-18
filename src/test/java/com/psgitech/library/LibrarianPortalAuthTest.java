package com.psgitech.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.filter.LibrarianAuthFilter;
import com.psgitech.library.model.Admin;
import com.psgitech.library.model.Librarian;
import com.psgitech.library.model.Student;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.UserRepository;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class LibrarianPortalAuthTest {

    private LibrarianAuthFilter filter;

    @Before
    public void setUp() throws ServletException {
        filter = new LibrarianAuthFilter();
        FilterConfig mockConfig = (FilterConfig) Proxy.newProxyInstance(
                FilterConfig.class.getClassLoader(),
                new Class<?>[]{FilterConfig.class},
                (proxy, method, args) -> null
        );
        filter.init(mockConfig);
    }

    @Test
    public void testActiveLibrarianAllowedThroughFilter() throws Exception {
        Librarian librarian = new Librarian(2, "Dr. K. Ramanathan", "librarian@psgitech.ac.in", "hash", "9876543211", true, "LIB101", false);
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("user", librarian);
        sessionAttrs.put("userId", librarian.getUserId());
        sessionAttrs.put("userRole", Role.LIBRARIAN.name());

        Map<String, String> responseHeaders = new HashMap<>();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        HttpServletRequest req = createMockRequest("/psgitech-library/api/librarian/dashboard/stats", "/psgitech-library", sessionAttrs);
        HttpServletResponse resp = createMockResponse(new StringWriter(), responseHeaders);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertTrue("Chain must proceed for active librarian", chainCalled.get());
        assertEquals("no-store, no-cache, must-revalidate", responseHeaders.get("Cache-Control"));
        assertEquals("nosniff", responseHeaders.get("X-Content-Type-Options"));
        assertEquals("DENY", responseHeaders.get("X-Frame-Options"));
    }

    @Test
    public void testAdminAllowedThroughLibrarianFilter() throws Exception {
        Admin admin = new Admin(1, "Chief Administrator", "admin@psgitech.ac.in", "hash", "9876543210", true);
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("user", admin);
        sessionAttrs.put("userId", admin.getUserId());
        sessionAttrs.put("userRole", Role.ADMIN.name());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        HttpServletRequest req = createMockRequest("/psgitech-library/librarian-dashboard.html", "/psgitech-library", sessionAttrs);
        HttpServletResponse resp = createMockResponse(new StringWriter(), new HashMap<>());
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertTrue("Chain must proceed for admin supervisory role", chainCalled.get());
    }

    @Test
    public void testStudentBlockedWith403OnLibrarianPortal() throws Exception {
        Student student = new Student(10, "Aravind Kumar", "aravind@psgitech.ac.in", "hash", "9876543215", true, "715521104001", "CSE", 3);
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("user", student);
        sessionAttrs.put("userId", student.getUserId());
        sessionAttrs.put("userRole", Role.STUDENT.name());

        StringWriter sw = new StringWriter();
        Map<String, Integer> statusHolder = new HashMap<>();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        HttpServletRequest req = createMockRequest("/psgitech-library/api/librarian/inventory", "/psgitech-library", sessionAttrs);
        HttpServletResponse resp = createMockResponseWithStatus(sw, new HashMap<>(), statusHolder);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertFalse("Chain must not be called for student", chainCalled.get());
        assertEquals(Integer.valueOf(403), statusHolder.get("status"));
        JsonObject json = JsonParser.parseString(sw.toString()).getAsJsonObject();
        assertEquals("FORBIDDEN", json.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    public void testUnauthenticatedApiReturns401Json() throws Exception {
        StringWriter sw = new StringWriter();
        Map<String, Integer> statusHolder = new HashMap<>();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        HttpServletRequest req = createMockRequest("/psgitech-library/api/librarian/dashboard/stats", "/psgitech-library", null);
        HttpServletResponse resp = createMockResponseWithStatus(sw, new HashMap<>(), statusHolder);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertFalse("Chain must not be called when unauthenticated", chainCalled.get());
        assertEquals(Integer.valueOf(401), statusHolder.get("status"));
        JsonObject json = JsonParser.parseString(sw.toString()).getAsJsonObject();
        assertEquals("UNAUTHENTICATED", json.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    public void testUnauthenticatedPageRedirectsToLibrarianLogin() throws Exception {
        Map<String, String> redirects = new HashMap<>();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        HttpServletRequest req = createMockRequest("/psgitech-library/librarian-dashboard.html", "/psgitech-library", null);
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("sendRedirect".equals(method.getName())) {
                        redirects.put("url", (String) args[0]);
                        return null;
                    }
                    if ("setHeader".equals(method.getName())) return null;
                    return null;
                }
        );
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertFalse(chainCalled.get());
        assertEquals("/psgitech-library/librarian-login.html", redirects.get("url"));
    }

    private HttpServletRequest createMockRequest(String uri, String contextPath, Map<String, Object> sessionAttrs) {
        HttpSession session = sessionAttrs != null ? (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) return sessionAttrs.get(args[0]);
                    if ("setAttribute".equals(method.getName())) { sessionAttrs.put((String) args[0], args[1]); return null; }
                    return null;
                }
        ) : null;

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getRequestURI".equals(method.getName())) return uri;
                    if ("getContextPath".equals(method.getName())) return contextPath;
                    if ("getSession".equals(method.getName())) return session;
                    if ("getHeader".equals(method.getName())) {
                        String h = (String) args[0];
                        if ("X-Requested-With".equalsIgnoreCase(h)) return uri.contains("/api/") ? "XMLHttpRequest" : null;
                        if ("Accept".equalsIgnoreCase(h)) return uri.contains("/api/") ? "application/json" : "text/html";
                        return null;
                    }
                    return null;
                }
        );
    }

    private HttpServletResponse createMockResponse(StringWriter sw, Map<String, String> headers) {
        return createMockResponseWithStatus(sw, headers, new HashMap<>());
    }

    private HttpServletResponse createMockResponseWithStatus(StringWriter sw, Map<String, String> headers, Map<String, Integer> statusHolder) {
        PrintWriter pw = new PrintWriter(sw);
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("setHeader".equals(method.getName())) {
                        headers.put((String) args[0], (String) args[1]);
                        return null;
                    }
                    if ("setStatus".equals(method.getName())) {
                        statusHolder.put("status", (Integer) args[0]);
                        return null;
                    }
                    if ("setContentType".equals(method.getName()) || "setCharacterEncoding".equals(method.getName())) {
                        return null;
                    }
                    if ("getWriter".equals(method.getName())) {
                        return pw;
                    }
                    return null;
                }
        );
    }
}
