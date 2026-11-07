package com.psgitech.library.filter;

import com.google.gson.Gson;
import com.psgitech.library.concurrency.ConcurrencyManager;
import com.psgitech.library.util.CsrfTokenManager;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Filter enforcing CSRF protection via synchronizer tokens on all mutating HTTP requests.
 * Complies with Decision D1:
 * - Positioned before ConcurrencyFilter to reject forged requests before consuming admission permits.
 * - Exempts pre-login endpoints (login, registration, public OTP verification).
 * - Enforces constant-time token comparison for all authenticated sessions.
 */
public class CsrfFilter implements Filter {
    private static final Logger LOGGER = Logger.getLogger(CsrfFilter.class.getName());
    public static final String INVOCATION_COUNT_ATTR = "com.psgitech.library.filter.CsrfFilter.invocations";

    private static final Set<String> EXEMPT_PATHS;

    static {
        Set<String> exempt = new HashSet<>();
        exempt.add("/login");
        exempt.add("/register");
        exempt.add("/register/verify-otp");
        exempt.add("/register/resend-otp");
        exempt.add("/forgot-password");
        exempt.add("/reset-password");
        EXEMPT_PATHS = Collections.unmodifiableSet(exempt);
    }

    private Gson gson = GsonProvider.getGson();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (this.gson == null) {
            this.gson = GsonProvider.getGson();
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Track invocation count for filter-chain verification tests
        Integer count = (Integer) req.getAttribute(INVOCATION_COUNT_ATTR);
        req.setAttribute(INVOCATION_COUNT_ATTR, (count == null ? 1 : count + 1));

        String method = req.getMethod();
        if (method == null) {
            chain.doFilter(request, response);
            return;
        }
        method = method.toUpperCase();

        // Safe methods (GET, HEAD, OPTIONS) do not require CSRF validation
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            chain.doFilter(request, response);
            return;
        }

        // Check path exemptions
        String path = req.getServletPath();
        if (req.getPathInfo() != null) {
            path += req.getPathInfo();
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (EXEMPT_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Only enforce for authenticated sessions
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            // Unauthenticated requests are handled by AuthenticationFilter
            chain.doFilter(request, response);
            return;
        }

        // Validate synchronizer token
        if (!CsrfTokenManager.isValidToken(req)) {
            try {
                ConcurrencyManager.getInstance().getMetrics().recordCsrfFailure(path);
            } catch (Exception ignored) {}

            LOGGER.warning("CSRF validation failed for authenticated user on " + method + " " + path);

            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");

            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Invalid or missing CSRF token. Please refresh the page and try again.");
            res.getWriter().write(gson.toJson(err));
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
