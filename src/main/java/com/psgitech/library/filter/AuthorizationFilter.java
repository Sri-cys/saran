package com.psgitech.library.filter;

import com.psgitech.library.enums.Role;
import com.psgitech.library.model.Librarian;
import com.psgitech.library.model.User;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Filter enforcing Role-Based Access Control (RBAC) and mandatory first-login password change.
 * Filter registration and order is defined deterministically in web.xml.
 */
public class AuthorizationFilter implements Filter {

    public static final String INVOCATION_COUNT_ATTR = "com.psgitech.library.filter.AuthorizationFilter.invocations";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Track invocation count for filter-chain verification
        Integer count = (Integer) req.getAttribute(INVOCATION_COUNT_ATTR);
        req.setAttribute(INVOCATION_COUNT_ATTR, (count == null ? 1 : count + 1));

        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String path = uri.substring(contextPath.length());

        HttpSession session = req.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("user") : null;

        // Check first-login password change requirement
        if (user instanceof Librarian) {
            Librarian lib = (Librarian) user;
            if (lib.isMustChangePassword()) {
                boolean isAllowedPath = path.contains("first-login-change-password")
                        || path.contains("change-password")
                        || path.contains("logout")
                        || path.startsWith("/css")
                        || path.startsWith("/js")
                        || path.startsWith("/images");

                if (!isAllowedPath) {
                    res.sendRedirect(contextPath + "/first-login-change-password.html");
                    return;
                }
            }
        }

        // Staff Interchange Monitoring (Librarian & Admin)
        if (path.startsWith("/api/admin/interchanges")) {
            if (user == null) {
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
                return;
            }
            if (user.getRole() != Role.LIBRARIAN && user.getRole() != Role.ADMIN) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Staff role required.");
                return;
            }
        }

        // Student-Only Personalized Recommendations (Feature 10 Decision D8)
        if (path.startsWith("/books/recommendations")) {
            if (user == null) {
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
                return;
            }
            if (user.getRole() != Role.STUDENT) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Student role required.");
                return;
            }
        }

        // Admin-only protection (including fine waivers via POST /fines)
        if (path.startsWith("/admin/") || (path.startsWith("/api/admin/") && !path.startsWith("/api/admin/interchanges"))
                || path.contains("admin-users") || path.contains("concurrency-monitor")
                || (path.startsWith("/fines") && "POST".equalsIgnoreCase(req.getMethod()))) {
            if (user == null) {
                if (path.startsWith("/api/admin/") || path.startsWith("/fines") || (!path.endsWith(".html") && !path.contains("."))) {
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
                } else {
                    res.sendRedirect(contextPath + "/login.html?redirect=" + path);
                }
                return;
            }
            if (user.getRole() != Role.ADMIN) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Administrator role required.");
                return;
            }
        }

        // Librarian & Staff protection (Librarian Operations, Borrowing Management & Fines)
        if (path.startsWith("/librarian/") || path.contains("librarian-dashboard") || path.contains("borrowing-management")
                || path.startsWith("/borrow/issue") || path.startsWith("/borrow/return") || path.startsWith("/borrow/active")
                || path.startsWith("/borrow/overdue") || path.startsWith("/borrow/lookup")
                || path.startsWith("/fines") || path.startsWith("/payments/verify")
                || path.startsWith("/books/copies/barcode") || path.contains("barcode-labels")) {
            if (user == null) {
                if (path.endsWith(".html") || !path.contains(".")) {
                    res.sendRedirect(contextPath + "/login.html?redirect=" + path);
                } else {
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
                }
                return;
            }
            if (user.getRole() != Role.LIBRARIAN && user.getRole() != Role.ADMIN) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Librarian or Administrator role required.");
                return;
            }
        }

        // Student-only protection (Student Portal, My Borrowings, My Reservations & My Fines)
        if (path.startsWith("/student/") || path.contains("student-home") || path.contains("my-borrowings") 
                || path.contains("my-reservations") || path.contains("my-books") || path.contains("my-fines")) {
            if (user == null) {
                res.sendRedirect(contextPath + "/login.html?redirect=" + path);
                return;
            }
            if (user.getRole() != Role.STUDENT && user.getRole() != Role.ADMIN) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Student role required.");
                return;
            }
        }

        // Student & Staff API & Receipt protection
        if (path.startsWith("/borrow/student") || path.startsWith("/borrow/history") 
                || path.startsWith("/borrow/renew")
                || path.startsWith("/reservations") || path.startsWith("/allocations")
                || path.startsWith("/my-fines") || path.startsWith("/payments") || path.startsWith("/receipt")
                || path.startsWith("/interchanges")
                || path.startsWith("/notifications")
                || path.contains("notifications.html")
                || path.contains("receipt.html")) {
            if (user == null) {
                if (path.endsWith(".html")) {
                    res.sendRedirect(contextPath + "/login.html?redirect=" + path);
                } else {
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
                }
                return;
            }
        }

        // Profile / Change Password protection
        if (path.contains("profile.html") || path.equals("/profile")) {
            if (user == null) {
                res.sendRedirect(contextPath + "/login.html?redirect=" + path);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
