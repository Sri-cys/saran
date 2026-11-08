package com.psgitech.library.service;

import com.psgitech.library.config.NotificationPolicy;
import com.psgitech.library.enums.EmailPolicy;
import com.psgitech.library.enums.InAppPolicy;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.model.Notification;
import com.psgitech.library.model.NotificationDraft;
import com.psgitech.library.model.NotificationOutboxRecord;
import com.psgitech.library.model.NotificationPreference;
import com.psgitech.library.repository.NotificationOutboxRepository;
import com.psgitech.library.repository.NotificationPreferenceRepository;
import com.psgitech.library.repository.NotificationRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transactional Notification Recorder.
 * Records in-app notifications and email outbox entries inside the business event's
 * active JDBC Connection using Savepoint failure isolation.
 */
public class NotificationRecorder {
    private static final Logger LOGGER = Logger.getLogger(NotificationRecorder.class.getName());

    private final NotificationRepository notificationRepo;
    private final NotificationPreferenceRepository preferenceRepo;
    private final NotificationOutboxRepository outboxRepo;
    private final NotificationTemplateService templateService;

    public NotificationRecorder() {
        this.notificationRepo = new NotificationRepository();
        this.preferenceRepo = new NotificationPreferenceRepository();
        this.outboxRepo = new NotificationOutboxRepository();
        this.templateService = new NotificationTemplateService();
    }

    public NotificationRecorder(NotificationRepository notificationRepo,
                                NotificationPreferenceRepository preferenceRepo,
                                NotificationOutboxRepository outboxRepo,
                                NotificationTemplateService templateService) {
        this.notificationRepo = notificationRepo;
        this.preferenceRepo = preferenceRepo;
        this.outboxRepo = outboxRepo;
        this.templateService = templateService;
    }

    /**
     * Records a notification on the provided active database connection.
     * Wrapped in a JDBC Savepoint. Any non-deadlock statement failure rolls back to the
     * savepoint and logs, guaranteeing the outer business transaction is never aborted.
     * Deadlocks (1213/1205) are rethrown to allow TransactionRetryHelper to retry cleanly.
     *
     * @param conn  active connection with ongoing transaction
     * @param draft immutable draft containing event data
     * @return notification_id if inserted, or 0 if duplicate/skipped
     * @throws SQLException only on deadlock or lock wait timeout
     */
    public long record(Connection conn, NotificationDraft draft) throws SQLException {
        if (conn == null || draft == null) {
            return 0L;
        }

        Savepoint sp = null;
        try {
            sp = conn.setSavepoint("sp_notif_" + System.nanoTime());

            NotificationPolicy.PolicyRule rule = NotificationPolicy.getRule(draft.getType());

            // 1. Snapshot read for user preferences
            NotificationPreference pref = preferenceRepo.findByUserAndCategory(conn, draft.getRecipientUserId(), rule.getCategory());

            // 2. Evaluate delivery policies
            boolean inApp = evaluateInApp(rule.getInAppPolicy(), pref, rule.getCategory());
            boolean email = evaluateEmail(rule.getEmailPolicy(), pref, rule.getCategory());

            // 3. Render template
            NotificationTemplateService.RenderedTemplate rendered = templateService.render(
                    draft.getType(), new HashMap<>(draft.getParameters()));

            String title = draft.getCustomTitle() != null ? draft.getCustomTitle() : rendered.getTitle();
            String message = draft.getCustomMessage() != null ? draft.getCustomMessage() : rendered.getMessage();
            String actionUrl = draft.getCustomActionUrl() != null ? draft.getCustomActionUrl() : rendered.getActionUrl();

            long notifId = 0L;
            if (inApp || email) {
                Notification notif = new Notification();
                notif.setRecipientUserId(draft.getRecipientUserId());
                notif.setCategory(rule.getCategory());
                notif.setNotificationType(draft.getType());
                notif.setTitle(title);
                notif.setMessage(message);
                notif.setActionUrl(actionUrl);
                notif.setDeduplicationKey(draft.getDeduplicationKey());
                notif.setRead(!inApp); // Marked read if inApp was disabled by user preference

                notifId = notificationRepo.insert(conn, notif);
            }

            if (email && isMailConfigured() && notifId > 0) {
                NotificationOutboxRecord outbox = new NotificationOutboxRecord(
                        notifId,
                        draft.getRecipientUserId(),
                        draft.getType()
                );
                outboxRepo.insert(conn, outbox);
            }

            conn.releaseSavepoint(sp);
            return notifId;

        } catch (SQLException e) {
            int errorCode = e.getErrorCode();
            // MySQL 1213: Deadlock found when trying to get lock
            // MySQL 1205: Lock wait timeout exceeded
            if (errorCode == 1213 || errorCode == 1205) {
                LOGGER.log(Level.WARNING, "Deadlock/timeout during notification recording (error code " + errorCode + "), bubbling up for retry");
                throw e;
            }

            // Normal statement / data error: rollback savepoint and suppress
            if (sp != null) {
                try {
                    conn.rollback(sp);
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.WARNING, "Failed to rollback savepoint during notification error", rollbackEx);
                }
            }
            LOGGER.log(Level.WARNING, "Notification recording suppressed due to error: " + e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Records a notification for all active library staff (LIBRARIAN and ADMIN).
     * Uses a single batched INSERT ... SELECT up to 200 staff members.
     */
    public int recordForStaff(Connection conn, NotificationType type, String title,
                              String message, String actionUrl, String dedupePrefix) throws SQLException {
        if (conn == null) {
            return 0;
        }

        Savepoint sp = null;
        try {
            sp = conn.setSavepoint("sp_staff_notif_" + System.nanoTime());

            String sql = "INSERT INTO notifications (recipient_user_id, category, notification_type, " +
                    "title, message, action_url, deduplication_key, is_read, created_at) " +
                    "SELECT user_id, 'STAFF', ?, ?, ?, ?, CONCAT(?, ':', user_id), FALSE, NOW() " +
                    "FROM users WHERE role IN ('LIBRARIAN', 'ADMIN') AND active = TRUE " +
                    "LIMIT 200 " +
                    "ON DUPLICATE KEY UPDATE notification_id = notification_id";

            int inserted = 0;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, type.name());
                stmt.setString(2, NotificationTemplateService.sanitizeText(title, 100));
                stmt.setString(3, NotificationTemplateService.sanitizeText(message, 500));
                stmt.setString(4, templateService.resolveActionUrl(actionUrl));
                stmt.setString(5, dedupePrefix != null ? dedupePrefix : ("STAFF:" + type.name()));

                inserted = stmt.executeUpdate();
            }

            conn.releaseSavepoint(sp);
            return inserted;

        } catch (SQLException e) {
            int errorCode = e.getErrorCode();
            if (errorCode == 1213 || errorCode == 1205) {
                LOGGER.log(Level.WARNING, "Deadlock during staff notification recording, bubbling up", e);
                throw e;
            }
            if (sp != null) {
                try {
                    conn.rollback(sp);
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.WARNING, "Failed to rollback staff notification savepoint", rollbackEx);
                }
            }
            LOGGER.log(Level.WARNING, "Staff notification recording suppressed: " + e.getMessage(), e);
            return 0;
        }
    }

    private boolean evaluateInApp(InAppPolicy policy, NotificationPreference pref, com.psgitech.library.enums.NotificationCategory category) {
        if (policy == InAppPolicy.MANDATORY) {
            return true;
        }
        if (pref != null) {
            return pref.isInAppEnabled();
        }
        return NotificationPolicy.getDefaultInApp(category);
    }

    private boolean evaluateEmail(EmailPolicy policy, NotificationPreference pref, com.psgitech.library.enums.NotificationCategory category) {
        if (policy == EmailPolicy.MANDATORY) {
            return true;
        }
        if (policy == EmailPolicy.NEVER) {
            return false;
        }
        if (pref != null) {
            return pref.isEmailEnabled();
        }
        return NotificationPolicy.getDefaultEmail(category);
    }

    private boolean isMailConfigured() {
        MailService service = MailServiceFactory.getMailService();
        return service != null && !(service instanceof DisabledMailService);
    }

    private String lookupUserEmail(Connection conn, int userId) {
        String sql = "SELECT email FROM users WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("email");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to look up user email for user_id=" + userId, e);
        }
        return null;
    }
}
