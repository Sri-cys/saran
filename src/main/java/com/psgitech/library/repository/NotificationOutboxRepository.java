package com.psgitech.library.repository;

import com.psgitech.library.enums.DeliveryStatus;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.model.NotificationOutboxRecord;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for asynchronous email outbox queue.
 * Implements atomic token-based leasing, exponential backoff, and terminal status handling.
 */
public class NotificationOutboxRepository {
    private static final Logger LOGGER = Logger.getLogger(NotificationOutboxRepository.class.getName());

    public long insert(Connection conn, NotificationOutboxRecord record) throws SQLException {
        String sql = "INSERT INTO notification_outbox (notification_id, recipient_user_id, notification_type, " +
                "delivery_status, attempt_count, next_attempt_at, created_at) " +
                "VALUES (?, ?, ?, 'PENDING', 0, NOW(6), NOW())";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, record.getNotificationId());
            stmt.setInt(2, record.getRecipientUserId());
            stmt.setString(3, record.getNotificationType().name());

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long generatedId = rs.getLong(1);
                        record.setOutboxId(generatedId);
                        return generatedId;
                    }
                }
            }
            return 0L;
        }
    }

    public List<NotificationOutboxRecord> claimBatch(int batchSize, String claimToken, int leaseSeconds) {
        int safeBatch = (batchSize <= 0 || batchSize > 100) ? 20 : batchSize;
        int safeLease = (leaseSeconds <= 0) ? 120 : leaseSeconds;

        String claimSql = "UPDATE notification_outbox " +
                "SET delivery_status = 'PROCESSING', claimed_at = NOW(), claim_token = ? " +
                "WHERE (delivery_status = 'PENDING' OR (delivery_status = 'FAILED' AND next_attempt_at <= CURRENT_TIMESTAMP)) " +
                "AND attempt_count < 5 " +
                "AND (claim_token IS NULL OR claimed_at < DATE_SUB(NOW(), INTERVAL ? SECOND)) " +
                "ORDER BY outbox_id ASC LIMIT ?";

        String selectSql = "SELECT o.outbox_id, o.notification_id, o.recipient_user_id, o.notification_type, " +
                "o.delivery_status, o.attempt_count, o.next_attempt_at, o.claimed_at, o.claim_token, " +
                "o.created_at, o.sent_at, o.last_error_code, " +
                "u.email AS recipient_email, n.title, n.message, n.action_url " +
                "FROM notification_outbox o " +
                "JOIN notifications n ON o.notification_id = n.notification_id " +
                "JOIN users u ON o.recipient_user_id = u.user_id " +
                "WHERE o.claim_token = ? AND o.delivery_status = 'PROCESSING'";

        List<NotificationOutboxRecord> claimed = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(claimSql)) {
                stmt.setString(1, claimToken);
                stmt.setInt(2, safeLease);
                stmt.setInt(3, safeBatch);
                int count = stmt.executeUpdate();
                if (count == 0) {
                    return claimed;
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, claimToken);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        claimed.add(mapRow(rs));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error claiming outbox batch with token " + claimToken, e);
        }
        return claimed;
    }

    public boolean markSent(long outboxId, String claimToken) {
        String sql = "UPDATE notification_outbox " +
                "SET delivery_status = 'SENT', sent_at = NOW(), claim_token = NULL " +
                "WHERE outbox_id = ? AND (claim_token = ? OR claim_token IS NULL)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, outboxId);
            stmt.setString(2, claimToken);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error marking outbox ID " + outboxId + " as SENT", e);
            return false;
        }
    }

    public boolean recordFailure(long outboxId, String claimToken, int currentRetryCount, String errorMessage, int backoffSeconds, boolean permanentlyFailed) {
        String targetStatus = permanentlyFailed ? "SKIPPED" : "FAILED";
        String sql = "UPDATE notification_outbox " +
                "SET delivery_status = ?, attempt_count = attempt_count + 1, " +
                "next_attempt_at = DATE_ADD(NOW(6), INTERVAL ? SECOND), " +
                "claim_token = NULL, last_error_code = ? " +
                "WHERE outbox_id = ? AND (claim_token = ? OR claim_token IS NULL)";

        String safeError = errorMessage != null && errorMessage.length() > 35
                ? errorMessage.substring(0, 35) : errorMessage;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetStatus);
            stmt.setInt(2, Math.max(backoffSeconds, 1));
            stmt.setString(3, safeError != null ? safeError : "DELIVERY_ERROR");
            stmt.setLong(4, outboxId);
            stmt.setString(5, claimToken);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error recording outbox failure for ID " + outboxId, e);
            return false;
        }
    }

    public int recoverStaleLeases() {
        return recoverStaleLeases(120);
    }

    public int recoverStaleLeases(int leaseSeconds) {
        String sql = "UPDATE notification_outbox " +
                "SET delivery_status = 'PENDING', claim_token = NULL, claimed_at = NULL " +
                "WHERE delivery_status = 'PROCESSING' AND claimed_at < DATE_SUB(NOW(), INTERVAL ? SECOND)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, leaseSeconds);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error recovering stale outbox leases", e);
            return 0;
        }
    }

    public Map<String, Object> getAdminStats() {
        Map<String, Object> stats = new HashMap<>();
        String countSql = "SELECT delivery_status, COUNT(*) AS cnt FROM notification_outbox GROUP BY delivery_status";
        String failure24hSql = "SELECT COUNT(*) FROM notification_outbox " +
                "WHERE delivery_status IN ('FAILED', 'SKIPPED') AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)";
        String oldestPendingSql = "SELECT TIMESTAMPDIFF(SECOND, MIN(created_at), NOW()) FROM notification_outbox " +
                "WHERE delivery_status = 'PENDING'";
        String activeLeaseSql = "SELECT COUNT(*) FROM notification_outbox " +
                "WHERE delivery_status = 'PROCESSING'";

        stats.put("PENDING", 0L);
        stats.put("PROCESSING", 0L);
        stats.put("SENT", 0L);
        stats.put("FAILED", 0L);
        stats.put("SKIPPED", 0L);
        stats.put("failuresLast24h", 0L);
        stats.put("oldestPendingSeconds", 0L);
        stats.put("activeLeases", 0L);

        try (Connection conn = DBConnection.getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSql)) {
                while (rs.next()) {
                    String status = rs.getString("delivery_status");
                    long cnt = rs.getLong("cnt");
                    stats.put(status, cnt);
                }
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(failure24hSql)) {
                if (rs.next()) {
                    stats.put("failuresLast24h", rs.getLong(1));
                }
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(oldestPendingSql)) {
                if (rs.next()) {
                    long oldest = rs.getLong(1);
                    stats.put("oldestPendingSeconds", rs.wasNull() ? 0L : oldest);
                }
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(activeLeaseSql)) {
                if (rs.next()) {
                    stats.put("activeLeases", rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error gathering admin outbox stats", e);
        }
        return stats;
    }

    private NotificationOutboxRecord mapRow(ResultSet rs) throws SQLException {
        NotificationOutboxRecord r = new NotificationOutboxRecord();
        r.setOutboxId(rs.getLong("outbox_id"));
        r.setNotificationId(rs.getLong("notification_id"));
        r.setRecipientUserId(rs.getInt("recipient_user_id"));
        r.setNotificationType(NotificationType.fromString(rs.getString("notification_type")));
        r.setDeliveryStatus(DeliveryStatus.fromString(rs.getString("delivery_status")));
        r.setAttemptCount(rs.getInt("attempt_count"));
        r.setNextAttemptAt(rs.getTimestamp("next_attempt_at"));
        r.setClaimedAt(rs.getTimestamp("claimed_at"));
        r.setClaimToken(rs.getString("claim_token"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        r.setSentAt(rs.getTimestamp("sent_at"));
        r.setLastErrorCode(rs.getString("last_error_code"));

        try {
            r.setRecipientEmail(rs.getString("recipient_email"));
            r.setTitle(rs.getString("title"));
            r.setMessage(rs.getString("message"));
            r.setActionUrl(rs.getString("action_url"));
        } catch (SQLException ignored) {}

        return r;
    }
}
