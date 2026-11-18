package com.psgitech.library.filter;

import com.psgitech.library.model.User;
import com.psgitech.library.service.AuthenticationService;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Filter that verifies authentication and automatically restores sessions
 * from secure Remember Me persistent cookies.
 * Filter registration and order is defined deterministically in web.xml.
 */
public class AuthenticationFilter implements Filter {
    public static final String INVOCATION_COUNT_ATTR = "com.psgitech.library.filter.AuthenticationFilter.invocations";
    private AuthenticationService authService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.authService = new AuthenticationService();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Track invocation count for filter-chain verification
        Integer count = (Integer) req.getAttribute(INVOCATION_COUNT_ATTR);
        req.setAttribute(INVOCATION_COUNT_ATTR, (count == null ? 1 : count + 1));

        HttpSession session = req.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute("user") : null;

        // If user not in session, check for Remember Me cookie
        if (currentUser == null) {
            Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (AuthenticationService.REMEMBER_ME_COOKIE_NAME.equals(c.getName())) {
                        String rawToken = c.getValue();
                        User restored = authService.validateAndRestoreUserFromToken(rawToken);
                        if (restored != null) {
                            session = req.getSession(true);
                            session.setAttribute("user", restored);
                            session.setAttribute("userId", restored.getUserId());
                            session.setAttribute("userRole", restored.getRole().name());
                            session.setAttribute("userName", restored.getName());
                            currentUser = restored;
                        }
                        break;
                    }
                }
            }
        }

        // Pass down chain
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
