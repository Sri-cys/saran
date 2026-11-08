package com.psgitech.library.repository;

import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.model.NotificationPreference;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for user notification preferences.
 */
public class NotificationPreferenceRepository {
    private static final Logger LOGGER = Logger.getLogger(NotificationPreferenceRepository.class.getName());

    public Map<NotificationCategory, NotificationPreference> findByUserId(int userId) {
        Map<NotificationCategory, NotificationPreference> map = new EnumMap<>(NotificationCategory.class);
        String sql = "SELECT preference_id, user_id, category, in_app_enabled, email_enabled, updated_at " +
                "FROM notification_preferences WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    NotificationPreference pref = mapRow(rs);
                    if (pref.getCategory() != null) {
                        map.put(pref.getCategory(), pref);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching notification preferences for user: " + userId, e);
        }
        return map;
    }

    /**
     * Snapshot read inside an existing transaction (never uses FOR UPDATE or FOR SHARE).
     */
    public NotificationPreference findByUserAndCategory(Connection conn, int userId, NotificationCategory category) {
        String sql = "SELECT preference_id, user_id, category, in_app_enabled, email_enabled, updated_at " +
                "FROM notification_preferences WHERE user_id = ? AND category = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, category.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error reading preference inside transaction for user: " + userId + ", cat: " + category, e);
        }
        return null;
    }

    public boolean upsert(int userId, NotificationCategory category, boolean inApp, boolean email) {
        String sql = "INSERT INTO notification_preferences (user_id, category, in_app_enabled, email_enabled) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE in_app_enabled = VALUES(in_app_enabled), " +
                "email_enabled = VALUES(email_enabled), updated_at = NOW()";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, category.name());
            stmt.setBoolean(3, inApp);
            stmt.setBoolean(4, email);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating notification preference for user: " + userId + ", cat: " + category, e);
            return false;
        }
    }

    public void upsertBatch(int userId, List<NotificationPreference> preferences) {
        String sql = "INSERT INTO notification_preferences (user_id, category, in_app_enabled, email_enabled) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE in_app_enabled = VALUES(in_app_enabled), " +
                "email_enabled = VALUES(email_enabled), updated_at = NOW()";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (NotificationPreference p : preferences) {
                stmt.setInt(1, userId);
                stmt.setString(2, p.getCategory().name());
                stmt.setBoolean(3, p.isInAppEnabled());
                stmt.setBoolean(4, p.isEmailEnabled());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error batch updating preferences for user: " + userId, e);
        }
    }

    private NotificationPreference mapRow(ResultSet rs) throws SQLException {
        NotificationPreference p = new NotificationPreference();
        p.setPreferenceId(rs.getInt("preference_id"));
        p.setUserId(rs.getInt("user_id"));
        p.setCategory(NotificationCategory.fromString(rs.getString("category")));
        p.setInAppEnabled(rs.getBoolean("in_app_enabled"));
        p.setEmailEnabled(rs.getBoolean("email_enabled"));
        return p;
    }
}
