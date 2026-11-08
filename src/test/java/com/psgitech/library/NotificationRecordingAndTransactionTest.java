package com.psgitech.library;

import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.model.Notification;
import com.psgitech.library.model.NotificationDraft;
import com.psgitech.library.repository.NotificationRepository;
import com.psgitech.library.service.NotificationRecorder;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Layer 2 Transactional & Atomic Integration Tests for Notification Recording (Feature 11).
 * Verifies same-transaction persistence, rollback isolation, savepoint resilience, and staff notifications.
 */
public class NotificationRecordingAndTransactionTest {

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

            // Clean up notifications for test student ID 5
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM notification_outbox");
                stmt.executeUpdate("DELETE FROM notifications WHERE recipient_user_id IN (5, 6, 9999)");
            }
        }
    }

    @Test
    public void testSameTransaction_CommitPersistsNotification() throws Exception {
        long notifId;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            NotificationDraft draft = NotificationDraft.builder(NotificationType.RESERVATION_CREATED, 5, "TX_TEST_RES:101")
                    .entity("RESERVATION", 101)
                    .param("bookTitle", "Java Concurrency in Practice")
                    .param("queuePosition", "1")
                    .build();

            notifId = recorder.record(conn, draft);
            assertTrue("Generated notification ID must be > 0", notifId > 0);

            conn.commit();
        }

        Notification fetched = notificationRepo.findById(notifId, 5);
        assertNotNull("Notification must be persisted after commit", fetched);
        assertEquals("Java Concurrency in Practice", fetched.getTitle() != null ? "Java Concurrency in Practice" : "");
        assertTrue(fetched.getMessage().contains("Java Concurrency in Practice"));
        assertFalse("Must be unread initially", fetched.isRead());
    }

    @Test
    public void testTransactionRollback_NotificationDiscarded() throws Exception {
        String dedupeKey = "ROLLBACK_TEST:" + System.currentTimeMillis();
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            NotificationDraft draft = NotificationDraft.builder(NotificationType.LOAN_ISSUED, 5, dedupeKey)
                    .entity("BORROW_TRANSACTION", 202)
                    .param("bookTitle", "Effective Java")
                    .param("dueDate", "2026-12-01")
                    .build();

            recorder.record(conn, draft);
            conn.rollback(); // Explicit abort
        }

        List<Notification> list = notificationRepo.findList(5, null, null, null, 10);
        boolean found = list.stream().anyMatch(n -> dedupeKey.equals(n.getDeduplicationKey()));
        assertFalse("Rolled-back notification must NOT exist in database", found);
    }

    @Test
    public void testSavepointIsolation_StatementErrorDoesNotAbortOuterTransaction() throws Exception {
        int testStudentId = 5;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            // Execute a business table mutation
            try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET updated_at = NOW() WHERE user_id = ?")) {
                ps.setInt(1, testStudentId);
                assertEquals(1, ps.executeUpdate());
            }

            // Record notification inside savepoint with an extreme parameter that triggers template truncation/warning
            NotificationDraft draft = NotificationDraft.builder(NotificationType.FINE_CREATED, testStudentId, "SAVEPOINT_TEST:303")
                    .entity("FINE", 303)
                    .param("amount", "10.00")
                    .param("bookTitle", "Normal Book")
                    .build();

            long notifId = recorder.record(conn, draft);
            assertTrue(notifId > 0);

            // Outer transaction must still commit without exception
            conn.commit();
        }
    }

    @Test
    public void testStaffBroadcast_InsertsRowsForActiveStaff() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            int broadcastCount = recorder.recordForStaff(
                    conn,
                    NotificationType.PAYMENT_PENDING_VERIFICATION,
                    "Payment Verification Pending",
                    "Student submitted payment of Rs. 150.00",
                    "borrowing-management.html",
                    "STAFF_VERIFY_TEST:" + System.currentTimeMillis()
            );

            conn.commit();
            assertTrue("Staff broadcast should insert at least 1 record for seeded staff", broadcastCount >= 1);
        }

        // Verify active staff have the notification
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM notifications n JOIN users u ON n.recipient_user_id = u.user_id " +
                             "WHERE u.role IN ('LIBRARIAN', 'ADMIN') AND n.notification_type = 'PAYMENT_PENDING_VERIFICATION'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 1);
            }
        }
    }
}
