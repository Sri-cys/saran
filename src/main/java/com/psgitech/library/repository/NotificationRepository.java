package com.psgitech.library.repository;

import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.model.Notification;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for notifications.
 * Implements keyset pagination, bounded batch read updates, and idempotent upserts.
 */
public class NotificationRepository {
    private static final Logger LOGGER = Logger.getLogger(NotificationRepository.class.getName());

    public long insert(Connection conn, Notification notif) throws SQLException {
        String sql = "INSERT INTO notifications (recipient_user_id, category, notification_type, " +
                "title, message, action_url, deduplication_key, is_read, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, NOW()) " +
                "ON DUPLICATE KEY UPDATE notification_id = LAST_INSERT_ID(notification_id)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, notif.getRecipientUserId());
            stmt.setString(2, notif.getCategory().name());
            stmt.setString(3, notif.getNotificationType().name());
            stmt.setString(4, notif.getTitle());
            stmt.setString(5, notif.getMessage());
            stmt.setString(6, notif.getActionUrl());
            stmt.setString(7, notif.getDeduplicationKey());

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long generatedId = rs.getLong(1);
                        notif.setNotificationId(generatedId);
                        return generatedId;
                    }
                }
            }
            return 0L;
        }
    }

    public Notification findById(long notificationId, int userId) {
        String sql = "SELECT notification_id, recipient_user_id, category, notification_type, " +
                "title, message, action_url, deduplication_key, is_read, created_at, read_at " +
                "FROM notifications WHERE notification_id = ? AND recipient_user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, notificationId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding notification by ID: " + notificationId, e);
        }
        return null;
    }

    public List<Notification> findList(int userId, NotificationCategory category, Boolean unreadOnly, Long cursor, int limit) {
        int safeLimit = (limit <= 0 || limit > 50) ? 20 : limit;

        StringBuilder sql = new StringBuilder("SELECT notification_id, recipient_user_id, category, notification_type, ")
                .append("title, message, action_url, deduplication_key, is_read, created_at, read_at ")
                .append("FROM notifications WHERE recipient_user_id = ? ");

        if (category != null) {
            sql.append("AND category = ? ");
        }
        if (unreadOnly != null && unreadOnly) {
            sql.append("AND is_read = FALSE ");
        }
        if (cursor != null && cursor > 0) {
            sql.append("AND notification_id < ? ");
        }
        sql.append("ORDER BY notification_id DESC LIMIT ?");

        List<Notification> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            stmt.setInt(paramIndex++, userId);

            if (category != null) {
                stmt.setString(paramIndex++, category.name());
            }
            if (cursor != null && cursor > 0) {
                stmt.setLong(paramIndex++, cursor);
            }
            stmt.setInt(paramIndex, safeLimit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching notifications list for user: " + userId, e);
        }
        return list;
    }

    public int countUnread(int userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ? AND is_read = FALSE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting unread notifications for user: " + userId, e);
        }
        return 0;
    }

    public Long findLatestId(int userId) {
        String sql = "SELECT MAX(notification_id) FROM notifications WHERE recipient_user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return rs.wasNull() ? 0L : id;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching latest notification ID for user: " + userId, e);
        }
        return 0L;
    }

    public boolean markAsRead(int userId, long notificationId) {
        String sql = "UPDATE notifications SET is_read = TRUE, read_at = NOW() " +
                "WHERE notification_id = ? AND recipient_user_id = ? AND is_read = FALSE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, notificationId);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error marking notification as read: " + notificationId, e);
            return false;
        }
    }

    public int markAllAsRead(int userId, long upToId, int batchSize) {
        int safeBatch = (batchSize <= 0 || batchSize > 500) ? 500 : batchSize;
        String sql = "UPDATE notifications SET is_read = TRUE, read_at = NOW() " +
                "WHERE recipient_user_id = ? AND is_read = FALSE AND notification_id <= ? LIMIT ?";

        int totalUpdated = 0;
        try (Connection conn = DBConnection.getConnection()) {
            boolean hasMore = true;
            while (hasMore) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, userId);
                    stmt.setLong(2, upToId > 0 ? upToId : Long.MAX_VALUE);
                    stmt.setInt(3, safeBatch);
                    int updated = stmt.executeUpdate();
                    totalUpdated += updated;
                    if (updated < safeBatch) {
                        hasMore = false;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error marking all notifications as read for user: " + userId, e);
        }
        return totalUpdated;
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setNotificationId(rs.getLong("notification_id"));
        n.setRecipientUserId(rs.getInt("recipient_user_id"));
        n.setCategory(NotificationCategory.fromString(rs.getString("category")));
        n.setNotificationType(NotificationType.fromString(rs.getString("notification_type")));
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setActionUrl(rs.getString("action_url"));
        n.setDeduplicationKey(rs.getString("deduplication_key"));
        n.setRead(rs.getBoolean("is_read"));
        n.setCreatedAt(rs.getTimestamp("created_at"));
        n.setReadAt(rs.getTimestamp("read_at"));
        return n;
    }
}

