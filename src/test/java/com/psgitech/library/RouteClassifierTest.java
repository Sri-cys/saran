package com.psgitech.library;

import com.psgitech.library.concurrency.ConcurrencyOperation;
import com.psgitech.library.concurrency.RouteClassifier;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class RouteClassifierTest {

    private HttpServletRequest createMockRequest(String method, String servletPath, String pathInfo) {
        Map<String, Object> values = new HashMap<>();
        values.put("getMethod", method);
        values.put("getServletPath", servletPath);
        values.put("getPathInfo", pathInfo);

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if (values.containsKey(name)) {
                        return values.get(name);
                    }
                    return null;
                }
        );
    }

    @Test
    public void testClassifyClassifiedPostRoutes() {
        // Book Issue
        assertEquals(ConcurrencyOperation.BOOK_ISSUE,
                RouteClassifier.classify(createMockRequest("POST", "/borrow/issue", null)));
        assertEquals(ConcurrencyOperation.BOOK_ISSUE,
                RouteClassifier.classify(createMockRequest("POST", "/borrowings/issue", null)));

        // Book Return
        assertEquals(ConcurrencyOperation.BOOK_RETURN,
                RouteClassifier.classify(createMockRequest("POST", "/borrow/return", null)));
        assertEquals(ConcurrencyOperation.BOOK_RETURN,
                RouteClassifier.classify(createMockRequest("POST", "/borrowings/return", null)));

        // Reservations
        assertEquals(ConcurrencyOperation.RESERVATION_CREATE,
                RouteClassifier.classify(createMockRequest("POST", "/reservations", null)));
        assertEquals(ConcurrencyOperation.RESERVATION_CREATE,
                RouteClassifier.classify(createMockRequest("POST", "/reservations/", null))); // trailing slash
        assertEquals(ConcurrencyOperation.RESERVATION_CANCEL,
                RouteClassifier.classify(createMockRequest("POST", "/reservations/cancel", null)));

        // Payments & Fines
        assertEquals(ConcurrencyOperation.PAYMENT_MUTATION,
                RouteClassifier.classify(createMockRequest("POST", "/payments", null)));
        assertEquals(ConcurrencyOperation.PAYMENT_VERIFICATION,
                RouteClassifier.classify(createMockRequest("POST", "/payments/verify", null)));
        assertEquals(ConcurrencyOperation.FINE_WAIVER,
                RouteClassifier.classify(createMockRequest("POST", "/fines", null)));

        // Catalog & Copies
        assertEquals(ConcurrencyOperation.BOOK_CATALOG_MUTATION,
                RouteClassifier.classify(createMockRequest("POST", "/books/add", null)));
        assertEquals(ConcurrencyOperation.BOOK_CATALOG_MUTATION,
                RouteClassifier.classify(createMockRequest("POST", "/books/update", null)));
        assertEquals(ConcurrencyOperation.BOOK_CATALOG_MUTATION,
                RouteClassifier.classify(createMockRequest("POST", "/books/archive", null)));
        assertEquals(ConcurrencyOperation.BOOK_COPY_MUTATION,
                RouteClassifier.classify(createMockRequest("POST", "/books/copies/add", null)));

        // Users & Profiles
        assertEquals(ConcurrencyOperation.USER_ADMIN_MUTATION,
                RouteClassifier.classify(createMockRequest("POST", "/admin/users", null)));
        assertEquals(ConcurrencyOperation.USER_PROFILE_MUTATION,
                RouteClassifier.classify(createMockRequest("POST", "/profile", null)));
        assertEquals(ConcurrencyOperation.USER_PROFILE_MUTATION,
                RouteClassifier.classify(createMockRequest("POST", "/change-password", null)));

        // Feature 8: Book Interchanges
        assertEquals(ConcurrencyOperation.BOOK_INTERCHANGE,
                RouteClassifier.classify(createMockRequest("POST", "/interchanges", null)));
        assertEquals(ConcurrencyOperation.BOOK_INTERCHANGE,
                RouteClassifier.classify(createMockRequest("POST", "/interchanges/accept", null)));
        assertEquals(ConcurrencyOperation.BOOK_INTERCHANGE,
                RouteClassifier.classify(createMockRequest("POST", "/interchanges/reject", null)));
        assertEquals(ConcurrencyOperation.BOOK_INTERCHANGE,
                RouteClassifier.classify(createMockRequest("POST", "/interchanges/cancel", null)));
    }

    @Test
    public void testExclusionsAndGetRequests() {
        // GET requests are never governed
        assertNull(RouteClassifier.classify(createMockRequest("GET", "/borrow/issue", null)));
        assertNull(RouteClassifier.classify(createMockRequest("GET", "/books", null)));
        assertNull(RouteClassifier.classify(createMockRequest("GET", "/books/details", null)));
        assertNull(RouteClassifier.classify(createMockRequest("GET", "/reservations/my", null)));
        assertNull(RouteClassifier.classify(createMockRequest("GET", "/admin/concurrency/status", null)));

        // Excluded authentication POST requests
        assertNull(RouteClassifier.classify(createMockRequest("POST", "/login", null)));
        assertNull(RouteClassifier.classify(createMockRequest("POST", "/logout", null)));
        assertNull(RouteClassifier.classify(createMockRequest("POST", "/register", null)));
    }
}

