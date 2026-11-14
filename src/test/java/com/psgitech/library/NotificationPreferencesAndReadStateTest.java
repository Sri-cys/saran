package com.psgitech.library;

import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.model.Notification;
import com.psgitech.library.model.NotificationDraft;
import com.psgitech.library.model.NotificationPreference;
import com.psgitech.library.repository.NotificationRepository;
import com.psgitech.library.service.NotificationPreferenceService;
import com.psgitech.library.service.NotificationRecorder;
import com.psgitech.library.service.NotificationService;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Layer 2 & 3 Integration Tests for Read States, Keyset Pagination, and Preferences (Feature 11).
 */
public class NotificationPreferencesAndReadStateTest {

    private final NotificationService notificationService = new NotificationService();
    private final NotificationPreferenceService preferenceService = new NotificationPreferenceService();
    private final NotificationRepository notificationRepo = new NotificationRepository();
    private final NotificationRecorder recorder = new NotificationRecorder();

    private final int testStudentId = 5;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test must run on MySQL", "MySQL", prod);
            assertTrue("Schema must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM notifications WHERE recipient_user_id = " + testStudentId);
                stmt.executeUpdate("DELETE FROM notification_preferences WHERE user_id = " + testStudentId);
            }
        }
    }

    @Test
    public void testMarkAsRead_UpdatesStateAndTimestamp() throws Exception {
        long notifId;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            NotificationDraft draft = NotificationDraft.builder(NotificationType.LOAN_ISSUED, testStudentId, "READ_TEST:1")
                    .param("bookTitle", "Database Systems Concepts")
                    .param("dueDate", "2026-11-20")
                    .build();
            notifId = recorder.record(conn, draft);
            conn.commit();
        }

        assertEquals(1, notificationService.getUnreadCount(testStudentId));

        boolean marked = notificationService.markAsRead(testStudentId, notifId);
        assertTrue("Marking as read should return true", marked);

        Notification updated = notificationRepo.findById(notifId, testStudentId);
        assertNotNull(updated);
        assertTrue("is_read must now be true", updated.isRead());
        assertNotNull("read_at must now be set", updated.getReadAt());
        assertEquals(0, notificationService.getUnreadCount(testStudentId));
    }

    @Test
    public void testMarkAllAsRead_BoundedUpdates() throws Exception {
        long lastId = 0L;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            for (int i = 1; i <= 5; i++) {
                NotificationDraft draft = NotificationDraft.builder(NotificationType.LOAN_RETURNED, testStudentId, "BULK_READ:" + i)
                        .param("bookTitle", "Book " + i)
                        .build();
                lastId = recorder.record(conn, draft);
            }
            conn.commit();
        }

        assertEquals(5, notificationService.getUnreadCount(testStudentId));

        int updatedCount = notificationService.markAllAsRead(testStudentId, lastId);
        assertEquals("Must mark all 5 as read", 5, updatedCount);
        assertEquals(0, notificationService.getUnreadCount(testStudentId));
    }

    @Test
    public void testKeysetPagination_DescendingSequenceWithLimit() throws Exception {
        List<Long> createdIds = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            for (int i = 1; i <= 6; i++) {
                NotificationDraft draft = NotificationDraft.builder(NotificationType.FINE_CREATED, testStudentId, "PAGINATE:" + i)
                        .param("amount", "10.00")
                        .param("bookTitle", "Book " + i)
                        .build();
                createdIds.add(recorder.record(conn, draft));
            }
            conn.commit();
        }

        // Page 1: limit = 3, cursor = null
        List<Notification> page1 = notificationService.getNotifications(testStudentId, null, null, null, 3);
        assertEquals(3, page1.size());
        long cursor = page1.get(page1.size() - 1).getNotificationId();

        // Page 2: limit = 3, cursor = page1 last ID
        List<Notification> page2 = notificationService.getNotifications(testStudentId, null, null, cursor, 3);
        assertEquals(3, page2.size());

        // Verify ordering: descending notification IDs
        assertTrue(page1.get(0).getNotificationId() > page1.get(1).getNotificationId());
        assertTrue(page1.get(2).getNotificationId() > page2.get(0).getNotificationId());
        assertTrue(page2.get(0).getNotificationId() > page2.get(1).getNotificationId());
    }

    @Test
    public void testPreferences_LockedCategoriesCannotBeDisabled() {
        List<NotificationPreference> updates = new ArrayList<>();
        // Try to disable mandatory financial email notifications
        updates.add(new NotificationPreference(testStudentId, NotificationCategory.FINES_PAYMENTS, false, false));
        // Try to disable security alerts
        updates.add(new NotificationPreference(testStudentId, NotificationCategory.SYSTEM, false, false));
        // Legitimate preference update for BORROWING
        updates.add(new NotificationPreference(testStudentId, NotificationCategory.BORROWING, true, false));

        preferenceService.updateUserPreferences(testStudentId, updates);

        List<NotificationPreference> persisted = preferenceService.getUserPreferences(testStudentId);

        for (NotificationPreference p : persisted) {
            if (p.getCategory() == NotificationCategory.FINES_PAYMENTS) {
                assertTrue("FINES_PAYMENTS email must remain forced TRUE", p.isEmailEnabled());
                assertTrue("Locked flag must be true", p.isLocked());
            }
            if (p.getCategory() == NotificationCategory.SYSTEM) {
                assertTrue("SYSTEM email must remain forced TRUE", p.isEmailEnabled());
                assertTrue("Locked flag must be true", p.isLocked());
            }
            if (p.getCategory() == NotificationCategory.BORROWING) {
                assertFalse("BORROWING email can be disabled by user", p.isEmailEnabled());
            }
        }
    }
}

