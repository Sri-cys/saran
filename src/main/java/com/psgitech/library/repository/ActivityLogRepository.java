package com.psgitech.library.repository;

import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for audit activity logging of circulation and staff operations.
 */
public class ActivityLogRepository {
    private static final Logger LOGGER = Logger.getLogger(ActivityLogRepository.class.getName());

    public void insert(String eventType, int actorId, String actorRole, String targetEntity,
                       String targetId, String details, String ipAddress) {
        String sql = "INSERT INTO library_activity_logs (event_type, actor_id, actor_role, target_entity, " +
                     "target_id, action_details, ip_address, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setInt(2, actorId);
            ps.setString(3, actorRole);
            ps.setString(4, targetEntity);
            ps.setString(5, targetId != null ? targetId : "");
            ps.setString(6, details);
            ps.setString(7, ipAddress);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert activity log entry: " + eventType, e);
        }
    }

    public List<Map<String, Object>> findLogs(int offset, int limit, String eventType, String targetEntity,
                                              Integer actorId, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder(
            "SELECT l.log_id, l.event_type, l.actor_id, l.actor_role, l.target_entity, l.target_id, " +
            "l.action_details, l.ip_address, l.created_at, u.name AS actor_name " +
            "FROM library_activity_logs l " +
            "LEFT JOIN users u ON l.actor_id = u.user_id " +
            "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (eventType != null && !eventType.trim().isEmpty()) {
            sql.append("AND l.event_type = ? ");
            params.add(eventType.trim());
        }
        if (targetEntity != null && !targetEntity.trim().isEmpty()) {
            sql.append("AND l.target_entity = ? ");
            params.add(targetEntity.trim());
        }
        if (actorId != null && actorId > 0) {
            sql.append("AND l.actor_id = ? ");
            params.add(actorId);
        }
        if (fromDate != null) {
            sql.append("AND l.created_at >= ? ");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            sql.append("AND l.created_at <= ? ");
            params.add(Timestamp.valueOf(toDate.atTime(23, 59, 59)));
        }

        sql.append("ORDER BY l.log_id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("logId", rs.getLong("log_id"));
                    row.put("eventType", rs.getString("event_type"));
                    row.put("actorId", rs.getInt("actor_id"));
                    row.put("actorRole", rs.getString("actor_role"));
                    row.put("actorName", rs.getString("actor_name") != null ? rs.getString("actor_name") : "System");
                    row.put("targetEntity", rs.getString("target_entity"));
                    row.put("targetId", rs.getString("target_id"));
                    row.put("actionDetails", rs.getString("action_details"));
                    row.put("ipAddress", rs.getString("ip_address"));
                    row.put("createdAt", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : "");
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query activity logs", e);
        }
        return results;
    }

    public int countLogs(String eventType, String targetEntity, Integer actorId, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM library_activity_logs l WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (eventType != null && !eventType.trim().isEmpty()) {
            sql.append("AND l.event_type = ? ");
            params.add(eventType.trim());
        }
        if (targetEntity != null && !targetEntity.trim().isEmpty()) {
            sql.append("AND l.target_entity = ? ");
            params.add(targetEntity.trim());
        }
        if (actorId != null && actorId > 0) {
            sql.append("AND l.actor_id = ? ");
            params.add(actorId);
        }
        if (fromDate != null) {
            sql.append("AND l.created_at >= ? ");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            sql.append("AND l.created_at <= ? ");
            params.add(Timestamp.valueOf(toDate.atTime(23, 59, 59)));
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to count activity logs", e);
        }
        return 0;
    }
}
