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
        excluded.add("/register/verify-otp");
        excluded.add("/register/resend-otp");
        EXCLUDED_PATHS = Collections.unmodifiableSet(excluded);

        Map<String, ConcurrencyOperation> routes = new HashMap<>();
        // Borrowing & returns
        routes.put("/borrow/issue", ConcurrencyOperation.BOOK_ISSUE);
        routes.put("/borrowings/issue", ConcurrencyOperation.BOOK_ISSUE);
        routes.put("/borrow/return", ConcurrencyOperation.BOOK_RETURN);
        routes.put("/borrowings/return", ConcurrencyOperation.BOOK_RETURN);
        routes.put("/borrow/renew", ConcurrencyOperation.BOOK_RENEWAL);

        // Reservations & queue
        routes.put("/reservations", ConcurrencyOperation.RESERVATION_CREATE);
        routes.put("/reservations/cancel", ConcurrencyOperation.RESERVATION_CANCEL);

        // Fines & Payments
        routes.put("/payments", ConcurrencyOperation.PAYMENT_MUTATION);
        routes.put("/payments/verify", ConcurrencyOperation.PAYMENT_VERIFICATION);
        routes.put("/payments/razorpay", ConcurrencyOperation.PAYMENT_MUTATION);
        routes.put("/payments/razorpay/*", ConcurrencyOperation.PAYMENT_MUTATION);
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
        routes.put("/interchanges/otp/generate", ConcurrencyOperation.BOOK_INTERCHANGE);

        // Feature 11: Notifications
        routes.put("/notifications/read", ConcurrencyOperation.NOTIFICATION);
        routes.put("/notifications/read-all", ConcurrencyOperation.NOTIFICATION);
        routes.put("/notifications/preferences", ConcurrencyOperation.NOTIFICATION);

        // Feature 12: Dynamic Loan Policy
        routes.put("/admin/loan-policy/update", ConcurrencyOperation.POLICY_ADMIN);
        routes.put("/admin/loan-policy/windows/create", ConcurrencyOperation.POLICY_ADMIN);
        routes.put("/admin/loan-policy/windows/deactivate", ConcurrencyOperation.POLICY_ADMIN);

        // Feature 14: Loss & Damage Cases
        routes.put("/cases/report", ConcurrencyOperation.PENALTY_CASE);
        routes.put("/cases/choose", ConcurrencyOperation.PENALTY_CASE);
        routes.put("/cases/assess", ConcurrencyOperation.PENALTY_CASE);
        routes.put("/cases/replacement/receive", ConcurrencyOperation.PENALTY_CASE);
        routes.put("/cases/replacement/accept", ConcurrencyOperation.PENALTY_CASE);
        routes.put("/cases/replacement/reject", ConcurrencyOperation.PENALTY_CASE);
        routes.put("/cases/convert", ConcurrencyOperation.PENALTY_CASE);
        routes.put("/cases/cancel", ConcurrencyOperation.PENALTY_CASE);
        routes.put("/cases/override", ConcurrencyOperation.PENALTY_CASE);

        // Librarian Portal
        routes.put("/api/librarian/inventory/*", ConcurrencyOperation.BOOK_CATALOG_MUTATION);
        routes.put("/api/librarian/inventory/book", ConcurrencyOperation.BOOK_CATALOG_MUTATION);
        routes.put("/api/librarian/inventory/copies", ConcurrencyOperation.BOOK_COPY_MUTATION);
        routes.put("/api/librarian/inventory/retire", ConcurrencyOperation.BOOK_CATALOG_MUTATION);
        routes.put("/api/librarian/inventory/shelf-location", ConcurrencyOperation.BOOK_CATALOG_MUTATION);
        routes.put("/api/librarian/circulation/*", ConcurrencyOperation.BOOK_ISSUE);
        routes.put("/api/librarian/circulation/issue", ConcurrencyOperation.BOOK_ISSUE);
        routes.put("/api/librarian/circulation/return", ConcurrencyOperation.BOOK_RETURN);
        routes.put("/api/librarian/circulation/renew", ConcurrencyOperation.BOOK_RENEWAL);
        routes.put("/api/librarian/circulation/confirm-pickup", ConcurrencyOperation.BOOK_ISSUE);
        routes.put("/api/librarian/circulation/overdue-reminder", ConcurrencyOperation.NOTIFICATION);
        routes.put("/api/librarian/students/*", ConcurrencyOperation.USER_ADMIN_MUTATION);
        routes.put("/api/librarian/students/toggle-status", ConcurrencyOperation.USER_ADMIN_MUTATION);
        routes.put("/api/librarian/fines/*", ConcurrencyOperation.FINE_WAIVER);

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

        ConcurrencyOperation op = POST_ROUTES.get(path);
        if (op != null) {
            return op;
        }

        for (Map.Entry<String, ConcurrencyOperation> entry : POST_ROUTES.entrySet()) {
            String route = entry.getKey();
            if (route.endsWith("/*")) {
                String prefix = route.substring(0, route.length() - 2);
                if (path.startsWith(prefix)) {
                    return entry.getValue();
                }
            }
        }

        return null;
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

