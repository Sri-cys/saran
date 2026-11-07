package com.psgitech.library;

import com.psgitech.library.enums.Role;
import com.psgitech.library.filter.CsrfFilter;
import com.psgitech.library.model.Student;
import com.psgitech.library.model.User;
import com.psgitech.library.util.CsrfTokenManager;
import org.junit.Before;
import org.junit.Test;

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

import static org.junit.Assert.*;

public class CsrfFilterTest {

    private CsrfFilter filter;

    @Before
    public void setUp() {
        this.filter = new CsrfFilter();
    }

    private User createSampleUser() {
        Student user = new Student();
        user.setUserId(101);
        user.setEmail("student@psgitech.ac.in");
        user.setRole(Role.STUDENT);
        user.setRegisterNumber("715521104001");
        user.setName("Test Student");
        return user;
    }

    private HttpServletRequest createMockRequest(String method, String servletPath, User sessionUser, String csrfHeader, String csrfParam, Map<String, Object> sessionAttributes) {
        HttpSession mockSession = (sessionUser != null || (sessionAttributes != null && !sessionAttributes.isEmpty())) ? (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getAttribute".equals(name)) {
                        String key = (String) args[0];
                        if ("user".equals(key)) return sessionUser;
                        return sessionAttributes != null ? sessionAttributes.get(key) : null;
                    }
                    if ("setAttribute".equals(name)) {
                        if (sessionAttributes != null) {
                            sessionAttributes.put((String) args[0], args[1]);
                        }
                        return null;
                    }
                    return null;
                }
        ) : null;

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("getMethod".equals(name)) return method;
                    if ("getServletPath".equals(name)) return servletPath;
                    if ("getPathInfo".equals(name)) return null;
                    if ("getSession".equals(name)) {
                        boolean create = args == null || args.length == 0 || (boolean) args[0];
                        return mockSession;
                    }
                    if ("getHeader".equals(name)) {
                        String header = (String) args[0];
                        if ("X-CSRF-Token".equalsIgnoreCase(header)) return csrfHeader;
                        return null;
                    }
                    if ("getParameter".equals(name)) {
                        String param = (String) args[0];
                        if ("_csrf".equals(param)) return csrfParam;
                        return null;
                    }
                    return null;
                }
        );
    }

    private HttpServletResponse createMockResponse(int[] statusHolder, StringWriter out) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("setStatus".equals(name) || "sendError".equals(name)) {
                        statusHolder[0] = (int) args[0];
                        return null;
                    }
                    if ("getStatus".equals(name)) return statusHolder[0];
                    if ("setContentType".equals(name) || "setCharacterEncoding".equals(name)) return null;
                    if ("getWriter".equals(name)) return new PrintWriter(out);
                    return null;
                }
        );
    }

    @Test
    public void testSafeGetMethodBypassesCsrfCheck() throws Exception {
        HttpServletRequest req = createMockRequest("GET", "/borrow/my-books", createSampleUser(), null, null, new HashMap<>());
        int[] status = new int[]{200};
        HttpServletResponse resp = createMockResponse(status, new StringWriter());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(req, resp, (q, r) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertEquals(200, status[0]);
    }

    @Test
    public void testUnauthenticatedPostBypassesCsrfCheck() throws Exception {
        // Pre-login request with no user in session
        HttpServletRequest req = createMockRequest("POST", "/login", null, null, null, null);
        int[] status = new int[]{200};
        HttpServletResponse resp = createMockResponse(status, new StringWriter());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(req, resp, (q, r) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertEquals(200, status[0]);
    }

    @Test
    public void testAuthenticatedPostWithValidHeaderTokenSucceeds() throws Exception {
        Map<String, Object> sessionAttrs = new HashMap<>();
        String validToken = "valid-test-csrf-token-1234567890";
        sessionAttrs.put(CsrfTokenManager.CSRF_SESSION_ATTR, validToken);

        HttpServletRequest req = createMockRequest("POST", "/borrow/issue", createSampleUser(), validToken, null, sessionAttrs);
        int[] status = new int[]{200};
        HttpServletResponse resp = createMockResponse(status, new StringWriter());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(req, resp, (q, r) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertEquals(200, status[0]);
    }

    @Test
    public void testAuthenticatedPostWithMissingTokenReturns403() throws Exception {
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(CsrfTokenManager.CSRF_SESSION_ATTR, "expected-token-12345");

        StringWriter sw = new StringWriter();
        HttpServletRequest req = createMockRequest("POST", "/borrow/issue", createSampleUser(), null, null, sessionAttrs);
        int[] status = new int[]{200};
        HttpServletResponse resp = createMockResponse(status, sw);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(req, resp, (q, r) -> chainCalled.set(true));

        assertFalse(chainCalled.get());
        assertEquals(403, status[0]);
        assertTrue(sw.toString().contains("CSRF"));
    }

    @Test
    public void testAuthenticatedPostWithInvalidTokenReturns403() throws Exception {
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(CsrfTokenManager.CSRF_SESSION_ATTR, "expected-token-12345");

        StringWriter sw = new StringWriter();
        HttpServletRequest req = createMockRequest("POST", "/borrow/issue", createSampleUser(), "wrong-token-abcde", null, sessionAttrs);
        int[] status = new int[]{200};
        HttpServletResponse resp = createMockResponse(status, sw);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(req, resp, (q, r) -> chainCalled.set(true));

        assertFalse(chainCalled.get());
        assertEquals(403, status[0]);
        assertTrue(sw.toString().contains("CSRF"));
    }

    @Test
    public void testRegistrationOtpEndpointsAreExemptFromCsrf() throws Exception {
        HttpServletRequest verifyReq = createMockRequest("POST", "/register/verify-otp", null, null, null, null);
        int[] status1 = new int[]{200};
        HttpServletResponse resp1 = createMockResponse(status1, new StringWriter());
        AtomicBoolean chain1 = new AtomicBoolean(false);
        filter.doFilter(verifyReq, resp1, (q, r) -> chain1.set(true));
        assertTrue(chain1.get());

        HttpServletRequest resendReq = createMockRequest("POST", "/register/resend-otp", null, null, null, null);
        int[] status2 = new int[]{200};
        HttpServletResponse resp2 = createMockResponse(status2, new StringWriter());
        AtomicBoolean chain2 = new AtomicBoolean(false);
        filter.doFilter(resendReq, resp2, (q, r) -> chain2.set(true));
        assertTrue(chain2.get());
    }
}
