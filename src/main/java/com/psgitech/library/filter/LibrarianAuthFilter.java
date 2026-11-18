package com.psgitech.library.filter;

import com.google.gson.Gson;
import com.psgitech.library.dto.librarian.ApiResponse;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.Librarian;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Filter enforcing staff authentication, role checks, and security headers for the Librarian Portal.
 * Handles /librarian-dashboard.html and /api/librarian/*
 */
public class LibrarianAuthFilter implements Filter {
    private UserRepository userRepository;
    private Gson gson;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.userRepository = new UserRepository();
        this.gson = GsonProvider.getGson();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Apply strict security headers to all librarian portal responses
        res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        res.setHeader("Pragma", "no-cache");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");

        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String path = uri.substring(contextPath.length());

        boolean isApi = path.startsWith("/api/librarian")
                || "XMLHttpRequest".equalsIgnoreCase(req.getHeader("X-Requested-With"))
                || (req.getHeader("Accept") != null && req.getHeader("Accept").contains("application/json"));

        HttpSession session = req.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("user") : null;

        // 1. Unauthenticated check
        if (user == null) {
            if (isApi) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json");
                res.setCharacterEncoding("UTF-8");
                res.getWriter().write(gson.toJson(ApiResponse.error("UNAUTHENTICATED", "Authentication required for Librarian Portal.")));
            } else {
                res.sendRedirect(contextPath + "/librarian-login.html");
            }
            return;
        }

        // 2. Student role block
        if (user.getRole() == Role.STUDENT) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            if (isApi) {
                res.setContentType("application/json");
                res.setCharacterEncoding("UTF-8");
                res.getWriter().write(gson.toJson(ApiResponse.error("FORBIDDEN", "Access Denied: Librarian or Administrator role required.")));
            } else {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Librarian role required.");
            }
            return;
        }

        // 3. Real-time active check against DB
        User dbUser = userRepository.findById(user.getUserId());
        if (dbUser == null || !dbUser.isActive()) {
            if (session != null) session.invalidate();
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            if (isApi) {
                res.setContentType("application/json");
                res.setCharacterEncoding("UTF-8");
                res.getWriter().write(gson.toJson(ApiResponse.error("ACCOUNT_INACTIVE", "Your staff account is inactive. Please contact the Library Administrator.")));
            } else {
                res.sendRedirect(contextPath + "/librarian-login.html?error=inactive");
            }
            return;
        }

        // 4. Force first-login password change for librarians
        if (dbUser instanceof Librarian) {
            Librarian lib = (Librarian) dbUser;
            if (lib.isMustChangePassword()) {
                if (!path.contains("change-password") && !path.contains("logout")) {
                    if (isApi) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType("application/json");
                        res.setCharacterEncoding("UTF-8");
                        res.getWriter().write(gson.toJson(ApiResponse.error("PASSWORD_CHANGE_REQUIRED", "Mandatory password change required on first login.")));
                    } else {
                        res.sendRedirect(contextPath + "/first-login-change-password.html");
                    }
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
