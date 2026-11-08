package com.psgitech.library;

import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.NotificationDraft;
import com.psgitech.library.model.Student;
import com.psgitech.library.repository.NotificationRepository;
import com.psgitech.library.service.NotificationRecorder;
import com.psgitech.library.servlet.AdminNotificationStatsServlet;
import com.psgitech.library.servlet.NotificationReadServlet;
import com.psgitech.library.servlet.NotificationsServlet;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Layer 4 & 5 Security, RBAC, Cross-Tenant Isolation, and Concurrency Tests (Feature 11).
 */
public class NotificationSecurityAndConcurrencyTest {

    private final NotificationRepository notificationRepo = new NotificationRepository();
    private final NotificationRecorder recorder = new NotificationRecorder();

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test must run on MySQL", "MySQL", prod);
            assertTrue("Schema must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM notifications WHERE recipient_user_id IN (5, 6, 999)");
            }
        }
    }

    private static class MockResponseHolder {
        int status = 200;
        final StringWriter stringWriter = new StringWriter();

        HttpServletResponse createProxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("setStatus".equals(name) && args.length >= 1) {
                            status = (Integer) args[0];
                            return null;
                        }
                        if ("getStatus".equals(name)) {
                            return status;
                        }
                        if ("getWriter".equals(name)) {
                            return new PrintWriter(stringWriter);
                        }
                        if ("setContentType".equals(name) || "setCharacterEncoding".equals(name) || "setHeader".equals(name)) {
                            return null;
                        }
                        return null;
                    }
            );
        }
    }

    private HttpServletRequest createMockRequest(Object sessionUser, Map<String, String> params) {
        Map<String, Object> sessionAttrs = new HashMap<>();
        if (sessionUser != null) {
            sessionAttrs.put("user", sessionUser);
        }

        HttpSession mockSession = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getAttribute".equals(name)) {
                        return sessionAttrs.get(args[0]);
                    }
                    return null;
                }
        );

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) {
                        return "GET";
                    }
                    if ("getSession".equals(name)) {
                        return sessionUser != null ? mockSession : null;
                    }
                    if ("getParameter".equals(name)) {
                        return params != null ? params.get(args[0]) : null;
                    }
                    if ("getContentType".equals(name)) {
                        return "application/json";
                    }
                    if ("getCharacterEncoding".equals(name)) {
                        return "UTF-8";
                    }
                    return null;
                }
        );
    }

    @Test
    public void testSecurity_AnonymousAccessReturns401() throws Exception {
        NotificationsServlet servlet = new NotificationsServlet();
        servlet.init();

        MockResponseHolder holder = new MockResponseHolder();
        servlet.service(createMockRequest(null, null), holder.createProxy());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, holder.status);
    }

    @Test
    public void testSecurity_NonAdminForbiddenOnAdminStats() throws Exception {
        AdminNotificationStatsServlet servlet = new AdminNotificationStatsServlet();
        servlet.init();

        Student student = new Student();
        student.setUserId(5);
        student.setRole(Role.STUDENT);

        MockResponseHolder holder = new MockResponseHolder();
        servlet.service(createMockRequest(student, null), holder.createProxy());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, holder.status);
    }

    @Test
    public void testCrossTenantIsolation_StudentCannotMutateOthersNotifications() throws Exception {
        long student6NotifId;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            NotificationDraft draft = NotificationDraft.builder(NotificationType.LOAN_ISSUED, 6, "CROSS_TENANT:1")
                    .param("bookTitle", "Private Student 6 Book")
                    .build();
            student6NotifId = recorder.record(conn, draft);
            conn.commit();
        }

        // Student 5 attempts to mark Student 6's notification as read
        boolean updated = notificationRepo.markAsRead(5, student6NotifId);
        assertFalse("Student 5 must not be able to mark Student 6's notification as read", updated);

        // Student 5 queries their notifications; must not contain Student 6's record
        var student5List = notificationRepo.findList(5, null, null, null, 10);
        assertTrue(student5List.stream().noneMatch(n -> n.getNotificationId() == student6NotifId));
    }

    @Test
    public void testZeroDataLeakage_NoPasswordsOrTokensPersisted() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            NotificationDraft draft = NotificationDraft.builder(NotificationType.PAYMENT_SUBMITTED, 5, "LEAK_AUDIT:1")
                    .param("amount", "100.00")
                    .param("utr", "123456789012") // 12-digit UPI reference
                    .param("password", "Secret123#")
                    .param("token", "super-secret-jwt")
                    .build();
            recorder.record(conn, draft);
            conn.commit();
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT title, message FROM notifications WHERE deduplication_key = 'LEAK_AUDIT:1'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String msg = rs.getString("message");
                assertFalse("Must NOT contain full 12-digit UTR", msg.contains("123456789012"));
                assertTrue("Must contain masked UTR ending in 9012", msg.contains("9012"));
                assertFalse("Must NOT contain raw password", msg.contains("Secret123#"));
                assertFalse("Must NOT contain token", msg.contains("super-secret-jwt"));
            }
        }
    }

    @Test
    public void testConcurrency_20ConcurrentThreadsSameDedupeKey_ExactlyOneRowPersisted() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        String concurrentDedupeKey = "CONCURRENT_DEDUPE:" + System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    try (Connection conn = DBConnection.getConnection()) {
                        conn.setAutoCommit(false);
                        NotificationDraft draft = NotificationDraft.builder(NotificationType.LOAN_ISSUED, 5, concurrentDedupeKey)
                                .param("bookTitle", "Concurrent Programming in Java")
                                .build();
                        long id = recorder.record(conn, draft);
                        conn.commit();
                        if (id > 0) {
                            successCounter.incrementAndGet();
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all 20 threads simultaneously
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly 1 row must exist with this deduplication key
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM notifications WHERE recipient_user_id = 5 AND deduplication_key = ?")) {
            ps.setString(1, concurrentDedupeKey);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Exactly ONE notification must be persisted across 20 concurrent attempts", 1, rs.getInt(1));
            }
        }
    }
}
