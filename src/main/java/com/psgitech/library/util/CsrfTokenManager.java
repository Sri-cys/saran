package com.psgitech.library.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Utility for generating, storing, and validating CSRF synchronizer tokens.
 * Complies with Decision D1:
 * - Cryptographically secure 32-byte tokens.
 * - Constant-time comparison preventing timing attacks.
 * - Bound to the active HTTP session.
 */
public class CsrfTokenManager {
    public static final String CSRF_SESSION_ATTR = "com.psgitech.library.csrf.token";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";
    public static final String CSRF_PARAM_NAME = "_csrf";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates a new 32-byte hex CSRF token.
     */
    public static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Retrieves the current token from session, or generates and stores one if none exists.
     */
    public static String getOrCreateToken(HttpSession session) {
        if (session == null) {
            return null;
        }
        synchronized (session) {
            String token = (String) session.getAttribute(CSRF_SESSION_ATTR);
            if (token == null || token.trim().isEmpty()) {
                token = generateToken();
                session.setAttribute(CSRF_SESSION_ATTR, token);
            }
            return token;
        }
    }

    /**
     * Rotates the token for the given session (e.g. after login).
     */
    public static String rotateToken(HttpSession session) {
        if (session == null) {
            return null;
        }
        synchronized (session) {
            String newToken = generateToken();
            session.setAttribute(CSRF_SESSION_ATTR, newToken);
            return newToken;
        }
    }

    /**
     * Validates an incoming request token against the active session token using constant-time comparison.
     */
    public static boolean isValidToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }

        String sessionToken = (String) session.getAttribute(CSRF_SESSION_ATTR);
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            return false;
        }

        String requestToken = request.getHeader(CSRF_HEADER_NAME);
        if (requestToken == null || requestToken.trim().isEmpty()) {
            requestToken = request.getParameter(CSRF_PARAM_NAME);
        }

        if (requestToken == null || requestToken.trim().isEmpty()) {
            return false;
        }

        return MessageDigest.isEqual(
                sessionToken.getBytes(StandardCharsets.UTF_8),
                requestToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}
