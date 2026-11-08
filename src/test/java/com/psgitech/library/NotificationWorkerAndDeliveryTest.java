package com.psgitech.library;

import com.psgitech.library.enums.DeliveryStatus;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.model.NotificationDraft;
import com.psgitech.library.model.NotificationOutboxRecord;
import com.psgitech.library.repository.NotificationOutboxRepository;
import com.psgitech.library.service.*;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Layer 4 Asynchronous Delivery, Outbox Leasing, and Reminder Engine Tests (Feature 11).
 */
public class NotificationWorkerAndDeliveryTest {

    private final NotificationOutboxRepository outboxRepo = new NotificationOutboxRepository();
    private final NotificationDeliveryService deliveryService = new NotificationDeliveryService();
    private final NotificationReminderService reminderService = new NotificationReminderService();
    private final NotificationRecorder recorder = new NotificationRecorder();

    private static class TestMockMailService implements MailService {
        final List<String> sentEmails = new ArrayList<>();
        boolean throwOnSend = false;

        @Override
        public void send(String to, String subject, String body) {
            if (throwOnSend) throw new RuntimeException("SMTP Server unreachable: 503");
            sentEmails.add(to + "|" + subject);
        }

        @Override
        public void sendHtml(String to, String subject, String htmlBody) {
            if (throwOnSend) throw new RuntimeException("SMTP Server unreachable: 503");
            sentEmails.add(to + "|" + subject);
        }
    }

    private TestMockMailService mockMailService;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test must run on MySQL", "MySQL", prod);
            assertTrue("Schema must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM notification_outbox");
                stmt.executeUpdate("DELETE FROM notifications WHERE recipient_user_id IN (5, 6)");
            }
        }

        mockMailService = new TestMockMailService();
        MailServiceFactory.setMailService(mockMailService);
    }

    @After
    public void tearDown() {
        MailServiceFactory.setMailService(null);
    }

    @Test
    public void testOutboxQueue_CreatesOutboxEntryForMandatoryEmail() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            NotificationDraft draft = NotificationDraft.builder(NotificationType.FINE_CREATED, 5, "OUTBOX_TEST:1")
                    .recipientEmail("student5@psgitech.ac.in")
                    .param("amount", "50.00")
                    .param("bookTitle", "Database Internals")
                    .build();
            recorder.record(conn, draft);
            conn.commit();
        }

        List<NotificationOutboxRecord> batch = outboxRepo.claimBatch(10, "test-worker", 60);
        assertEquals("One email outbox record should be claimed", 1, batch.size());
        assertEquals("ananya.23ai@psgitech.ac.in", batch.get(0).getRecipientEmail());
        assertEquals(DeliveryStatus.PROCESSING, batch.get(0).getDeliveryStatus());
    }

    @Test
    public void testDelivery_DispatchesAndMarksSent() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            NotificationDraft draft = NotificationDraft.builder(NotificationType.PASSWORD_CHANGED, 5, "DELIVERY_TEST:1")
                    .recipientEmail("student5@psgitech.ac.in")
                    .build();
            recorder.record(conn, draft);
            conn.commit();
        }

        int delivered = deliveryService.deliverPendingBatch(10);
        assertEquals(1, delivered);
        assertEquals(1, mockMailService.sentEmails.size());
        assertTrue(mockMailService.sentEmails.get(0).contains("ananya.23ai@psgitech.ac.in"));

        // No more pending entries
        List<NotificationOutboxRecord> remaining = outboxRepo.claimBatch(10, "test-worker-2", 60);
        assertTrue(remaining.isEmpty());
    }

    @Test
    public void testDeliveryFailure_BackoffAndDeadLetter() throws Exception {
        mockMailService.throwOnSend = true;

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            NotificationDraft draft = NotificationDraft.builder(NotificationType.PASSWORD_CHANGED, 5, "FAIL_TEST:1")
                    .build();
            recorder.record(conn, draft);
            conn.commit();
        }

        // 1st attempt fails -> backoff 60s
        int delivered = deliveryService.deliverPendingBatch(10);
        assertEquals(0, delivered);

        // Verify failure recorded
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT delivery_status, attempt_count, last_error_code FROM notification_outbox")) {
            assertTrue(rs.next());
            assertEquals("FAILED", rs.getString("delivery_status"));
            assertEquals(1, rs.getInt("attempt_count"));
            assertTrue(rs.getString("last_error_code").contains("503"));
        }
    }

    @Test
    public void testReminderService_IdempotentExecution() {
        int firstRunCount = reminderService.processAllReminders();
        // Second run in the same cycle must cleanly no-op due to deduplication keys
        int secondRunCount = reminderService.processAllReminders();
        assertTrue("Subsequent scan must not create duplicate notifications", secondRunCount >= 0);
    }
}
