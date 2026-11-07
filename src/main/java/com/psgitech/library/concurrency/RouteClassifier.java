package com.psgitech.library.concurrency;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fast method + path request classifier.
 * Inspects only HTTP method, servletPath, and pathInfo.
 * NEVER reads request body or request parameters.
 */
public class RouteClassifier {

    private static final Set<String> EXCLUDED_PATHS;
    private static final Map<String, ConcurrencyOperation> POST_ROUTES;

    static {
        Set<String> excluded = new HashSet<>();
        excluded.add("/login");
        excluded.add("/logout");
        excluded.add("/register");
        EXCLUDED_PATHS = Collections.unmodifiableSet(excluded);

        Map<String, ConcurrencyOperation> routes = new HashMap<>();
        // Borrowing & returns
        routes.put("/borrow/issue", ConcurrencyOperation.BOOK_ISSUE);
        routes.put("/borrowings/issue", ConcurrencyOperation.BOOK_ISSUE);
        routes.put("/borrow/return", ConcurrencyOperation.BOOK_RETURN);
        routes.put("/borrowings/return", ConcurrencyOperation.BOOK_RETURN);

        // Reservations & queue
        routes.put("/reservations", ConcurrencyOperation.RESERVATION_CREATE);
        routes.put("/reservations/cancel", ConcurrencyOperation.RESERVATION_CANCEL);

        // Fines & Payments
        routes.put("/payments", ConcurrencyOperation.PAYMENT_MUTATION);
        routes.put("/payments/verify", ConcurrencyOperation.PAYMENT_VERIFICATION);
        routes.put("/fines", ConcurrencyOperation.FINE_WAIVER);

        // Books & Copies
        routes.put("/books/add", ConcurrencyOperation.BOOK_CATALOG_MUTATION);
        routes.put("/books/update", ConcurrencyOperation.BOOK_CATALOG_MUTATION);
        routes.put("/books/archive", ConcurrencyOperation.BOOK_CATALOG_MUTATION);
        routes.put("/books/copies/add", ConcurrencyOperation.BOOK_COPY_MUTATION);

        // Users & Profiles
        routes.put("/admin/users", ConcurrencyOperation.USER_ADMIN_MUTATION);
        routes.put("/profile", ConcurrencyOperation.USER_PROFILE_MUTATION);
        routes.put("/change-password", ConcurrencyOperation.USER_PROFILE_MUTATION);

        // Feature 8: Book Interchanges
        routes.put("/interchanges", ConcurrencyOperation.BOOK_INTERCHANGE);
        routes.put("/interchanges/accept", ConcurrencyOperation.BOOK_INTERCHANGE);
        routes.put("/interchanges/reject", ConcurrencyOperation.BOOK_INTERCHANGE);
        routes.put("/interchanges/cancel", ConcurrencyOperation.BOOK_INTERCHANGE);

        POST_ROUTES = Collections.unmodifiableMap(routes);
    }

    /**
     * Normalizes request path from servletPath and pathInfo, removing trailing slash if present (except root).
     */
    public static String normalizePath(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        String pathInfo = req.getPathInfo();
        String fullPath = servletPath != null ? servletPath : "";
        if (pathInfo != null) {
            fullPath += pathInfo;
        }
        if (fullPath.length() > 1 && fullPath.endsWith("/")) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }
        return fullPath;
    }

    /**
     * Determines whether an HTTP request should be governed by the concurrency admission limiter.
     * Only classified mutating requests (POST, PUT, PATCH, DELETE) are governed.
     * All GET requests, authentication endpoints, and static files are excluded.
     */
    public static boolean isGoverned(HttpServletRequest req) {
        return classify(req) != null;
    }

    /**
     * Classifies a request into its ConcurrencyOperation, or returns null if excluded/non-mutating.
     */
    public static ConcurrencyOperation classify(HttpServletRequest req) {
        String method = req.getMethod();
        if (method == null) {
            return null;
        }
        method = method.toUpperCase();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method) && !"DELETE".equals(method)) {
            return null;
        }

        String path = normalizePath(req);
        if (EXCLUDED_PATHS.contains(path)) {
            return null;
        }

        return POST_ROUTES.get(path);
    }

    /**
     * Checks if a path is on the explicit exclusion list.
     */
    public static boolean isExplicitlyExcluded(String path) {
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return EXCLUDED_PATHS.contains(path);
    }

    public static Map<String, ConcurrencyOperation> getPostRoutes() {
        return POST_ROUTES;
    }

    public static Set<String> getExcludedPaths() {
        return EXCLUDED_PATHS;
    }
}

