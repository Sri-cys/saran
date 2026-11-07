package com.psgitech.library.filter;

import com.psgitech.library.concurrency.AdmissionTicket;
import com.psgitech.library.concurrency.ConcurrencyLimitExceededException;
import com.psgitech.library.concurrency.ConcurrencyManager;
import com.psgitech.library.concurrency.ConcurrencyOperation;
import com.psgitech.library.concurrency.RouteClassifier;
import com.psgitech.library.model.User;
import com.psgitech.library.util.DBConnection;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fair request admission filter for classified mutating operations.
 * Bounded by a fair Semaphore, a bounded waiting room, and a per-user in-flight cap.
 * Filter registration and order is defined deterministically in web.xml.
 */
public class ConcurrencyFilter implements Filter {
    private static final Logger LOGGER = Logger.getLogger(ConcurrencyFilter.class.getName());

    public static final String INVOCATION_COUNT_ATTR = "com.psgitech.library.filter.ConcurrencyFilter.invocations";
    public static final String ADMITTED_ATTR = "com.psgitech.library.concurrency.admitted";

    private ConcurrencyManager concurrencyManager;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.concurrencyManager = ConcurrencyManager.getInstance();
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

        // Track invocation count for filter-chain verification
        Integer count = (Integer) req.getAttribute(INVOCATION_COUNT_ATTR);
        req.setAttribute(INVOCATION_COUNT_ATTR, (count == null ? 1 : count + 1));

        // Only govern standard REQUEST dispatches
        if (req.getDispatcherType() != DispatcherType.REQUEST) {
            chain.doFilter(request, response);
            return;
        }

        // Prevent double permit acquisition on internal forward/include
        if (Boolean.TRUE.equals(req.getAttribute(ADMITTED_ATTR))) {
            chain.doFilter(request, response);
            return;
        }

        // Fast classification by method and path
        ConcurrencyOperation op = RouteClassifier.classify(req);
        if (op == null) {
            // Unclassified, GET, or explicitly excluded endpoint
            chain.doFilter(request, response);
            return;
        }

        req.setAttribute(ADMITTED_ATTR, Boolean.TRUE);

        HttpSession session = req.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("user") : null;
        Integer userId = (user != null) ? user.getUserId() : null;

        AdmissionTicket ticket = null;
        try {
            ticket = concurrencyManager.acquireTicket(op, userId);

            StatusTrackingResponseWrapper trackingResp = new StatusTrackingResponseWrapper(res);
            long processingStartTime = System.currentTimeMillis();

            try {
                chain.doFilter(request, trackingResp);
            } finally {
                long duration = System.currentTimeMillis() - processingStartTime;
                concurrencyManager.getMetrics().recordCompleted(op, trackingResp.getStatus(), duration);
            }

        } catch (ConcurrencyLimitExceededException e) {
            String requestPath = RouteClassifier.normalizePath(req);
            concurrencyManager.getMetrics().recordRejection(e.getReason(), requestPath, op);
            sendJsonError(res, e.getHttpStatus(), e.getRetryAfterSeconds(), e.getMessage());
        } catch (Throwable t) {
            SQLException sqle = findSQLException(t);
            if (sqle != null && DBConnection.isPoolExhaustedOrMaxConnections(sqle)) {
                String requestPath = RouteClassifier.normalizePath(req);
                concurrencyManager.getMetrics().recordRejection(ConcurrencyLimitExceededException.Reason.POOL_EXHAUSTED, requestPath, op);
                sendJsonError(res, HttpServletResponse.SC_SERVICE_UNAVAILABLE, 2,
                        "The library system is experiencing high traffic. Please retry in a few moments.");
            } else if (t instanceof ServletException) {
                throw (ServletException) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new ServletException(t.getMessage(), t);
            }
        } finally {
            if (ticket != null) {
                ticket.close();
            }
            req.removeAttribute(ADMITTED_ATTR);
        }
    }

    private void sendJsonError(HttpServletResponse res, int statusCode, int retryAfter, String message) throws IOException {
        if (!res.isCommitted()) {
            res.setStatus(statusCode);
            res.setContentType("application/json;charset=UTF-8");
            res.setHeader("Retry-After", String.valueOf(retryAfter));
            res.setHeader("Cache-Control", "no-store");
            String json = "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}";
            res.getWriter().write(json);
            res.getWriter().flush();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static SQLException findSQLException(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof SQLException) {
                return (SQLException) curr;
            }
            curr = curr.getCause();
        }
        return null;
    }

    @Override
    public void destroy() {}

    /**
     * Response wrapper that captures the HTTP status code set by servlets.
     */
    public static class StatusTrackingResponseWrapper extends HttpServletResponseWrapper {
        private int httpStatus = HttpServletResponse.SC_OK;

        public StatusTrackingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            super.setStatus(sc);
            this.httpStatus = sc;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            super.sendError(sc, msg);
            this.httpStatus = sc;
        }

        @Override
        public void sendError(int sc) throws IOException {
            super.sendError(sc);
            this.httpStatus = sc;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            super.sendRedirect(location);
            this.httpStatus = HttpServletResponse.SC_FOUND;
        }

        @Override
        public int getStatus() {
            return httpStatus;
        }
    }
}

