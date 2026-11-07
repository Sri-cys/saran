package com.psgitech.library;

import com.psgitech.library.concurrency.ConcurrencyConfig;
import com.psgitech.library.concurrency.ConcurrencyManager;
import com.psgitech.library.concurrency.ConcurrencyOperation;
import com.psgitech.library.enums.Role;
import com.psgitech.library.filter.ConcurrencyFilter;
import com.psgitech.library.model.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ConcurrencyFilterTest {

    private ConcurrencyManager originalManager;
    private ConcurrencyFilter filter;

    @Before
    public void setUp() throws Exception {
        originalManager = ConcurrencyManager.getInstance();
    }

    @After
    public void tearDown() {
        ConcurrencyManager.setInstance(originalManager);
    }

    private HttpServletRequest createMockRequest(String method, String servletPath, User sessionUser, Map<String, Object> attributes) {
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
                    if ("getDispatcherType".equals(name)) return DispatcherType.REQUEST;
                    if ("getSession".equals(name)) return mockSession;
                    if ("getAttribute".equals(name)) return attributes.get(args[0]);
                    if ("setAttribute".equals(name)) {
                        attributes.put((String) args[0], args[1]);
                        return null;
                    }
                    if ("removeAttribute".equals(name)) {
                        attributes.remove(args[0]);
                        return null;
                    }
                    return null;
                }
        );
    }

    private HttpServletResponse createMockResponse(AtomicInteger statusCode, Map<String, String> headers, StringWriter body) {
        PrintWriter writer = new PrintWriter(body);
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("setStatus".equals(name)) {
                        statusCode.set((Integer) args[0]);
                        return null;
                    }
                    if ("setHeader".equals(name)) {
                        headers.put((String) args[0], String.valueOf(args[1]));
                        return null;
                    }
                    if ("setContentType".equals(name)) {
                        headers.put("Content-Type", (String) args[0]);
                        return null;
                    }
                    if ("getWriter".equals(name)) return writer;
                    if ("isCommitted".equals(name)) return false;
                    return null;
                }
        );
    }

    @Test
    public void testGetRequestsPassThroughWithoutPermit() throws Exception {
        ConcurrencyConfig config = new ConcurrencyConfig(true, 1, 5, 1000L, 3, true);
        ConcurrencyManager manager = new ConcurrencyManager(config);
        ConcurrencyManager.setInstance(manager);

        filter = new ConcurrencyFilter();
        filter.init(null);

        Map<String, Object> attrs = new HashMap<>();
        AtomicInteger status = new AtomicInteger(200);
        Map<String, String> headers = new HashMap<>();
        StringWriter body = new StringWriter();

        HttpServletRequest req = createMockRequest("GET", "/books", null, attrs);
        HttpServletResponse res = createMockResponse(status, headers, body);

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainExecuted.set(true);

        filter.doFilter(req, res, chain);

        assertTrue("Chain must execute for GET", chainExecuted.get());
        assertEquals("Available permits must remain untouched", 1, manager.getAvailablePermits());
        assertEquals("Active requests must remain 0", 0, manager.getActiveRequests());
    }

    @Test
    public void testGovernedPostAdmittedAndCompletedMetrics() throws Exception {
        ConcurrencyConfig config = new ConcurrencyConfig(true, 5, 10, 1000L, 3, true);
        ConcurrencyManager manager = new ConcurrencyManager(config);
        ConcurrencyManager.setInstance(manager);

        filter = new ConcurrencyFilter();
        filter.init(null);

        User mockLibrarian = new com.psgitech.library.model.Librarian(10, "Librarian", "lib@psgitech.ac.in", "hash", "9876543210", true, "EMP010", false);
        Map<String, Object> attrs = new HashMap<>();
        AtomicInteger status = new AtomicInteger(200);
        Map<String, String> headers = new HashMap<>();
        StringWriter body = new StringWriter();

        HttpServletRequest req = createMockRequest("POST", "/borrow/issue", mockLibrarian, attrs);
        HttpServletResponse res = createMockResponse(status, headers, body);

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> {
            chainExecuted.set(true);
            // Verify inside chain that 1 permit is active
            assertEquals(1, manager.getActiveRequests());
            assertEquals(4, manager.getAvailablePermits());
        };

        filter.doFilter(req, res, chain);

        assertTrue("Chain must execute", chainExecuted.get());
        assertEquals("Quiescence: Active requests must return to 0", 0, manager.getActiveRequests());
        assertEquals("Quiescence: Available permits must return to 5", 5, manager.getAvailablePermits());
        assertEquals("Total admitted must be 1", 1, manager.getMetrics().getTotalAdmitted());
        assertEquals("Total completed must be 1", 1, manager.getMetrics().getTotalCompleted());
    }

    @Test
    public void testOverloadReturns503WithRetryAfterAndJsonEnvelope() throws Exception {
        // Capacity 1, timeout 50ms
        ConcurrencyConfig config = new ConcurrencyConfig(true, 1, 5, 50L, 3, true);
        ConcurrencyManager manager = new ConcurrencyManager(config);
        ConcurrencyManager.setInstance(manager);

        filter = new ConcurrencyFilter();
        filter.init(null);

        // Occupy the only permit
        manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 999);

        User mockLibrarian = new com.psgitech.library.model.Librarian(10, "Librarian", "lib@psgitech.ac.in", "hash", "9876543210", true, "EMP010", false);
        Map<String, Object> attrs = new HashMap<>();
        AtomicInteger status = new AtomicInteger(200);
        Map<String, String> headers = new HashMap<>();
        StringWriter body = new StringWriter();

        HttpServletRequest req = createMockRequest("POST", "/borrow/issue", mockLibrarian, attrs);
        HttpServletResponse res = createMockResponse(status, headers, body);

        AtomicBoolean chainExecuted = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainExecuted.set(true);

        filter.doFilter(req, res, chain);

        assertFalse("Downstream chain must NOT execute when rejected by limiter", chainExecuted.get());
        assertEquals(503, status.get());
        assertEquals("2", headers.get("Retry-After"));
        assertTrue(headers.get("Content-Type").contains("application/json"));
        assertTrue(body.toString().contains("\"success\":false"));
        assertTrue(body.toString().contains("System is currently busy"));
    }
}
