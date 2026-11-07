package com.psgitech.library.repository;

import com.psgitech.library.enums.AuditEventType;
import com.psgitech.library.model.PaymentAuditEntry;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Append-only Repository for recording financial and payment state transitions.
 * Guarantees that payment_audit_log only receives INSERT statements from application code.
 */
public class PaymentAuditRepository {
    private static final Logger LOGGER = Logger.getLogger(PaymentAuditRepository.class.getName());

    public PaymentAuditEntry recordAuditEvent(Connection conn, PaymentAuditEntry entry) throws SQLException {
        String sql = "INSERT INTO payment_audit_log (fine_id, payment_id, event_type, old_status, " +
                "new_status, amount, actor_user_id, actor_role, details) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, entry.getFineId());
            if (entry.getPaymentId() != null) {
                ps.setInt(2, entry.getPaymentId());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, entry.getEventType().name());
            ps.setString(4, entry.getOldStatus());
            ps.setString(5, entry.getNewStatus());
            if (entry.getAmount() != null) {
                ps.setBigDecimal(6, entry.getAmount());
            } else {
                ps.setNull(6, Types.DECIMAL);
            }
            if (entry.getActorUserId() != null) {
                ps.setInt(7, entry.getActorUserId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setString(8, entry.getActorRole());
            ps.setString(9, entry.getDetails());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Failed to write audit record: no rows affected.");
            }

            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    entry.setAuditId(gk.getInt(1));
                }
            }
        }
        return entry;
    }

    public List<PaymentAuditEntry> findByFineId(int fineId) {
        List<PaymentAuditEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM payment_audit_log WHERE fine_id = ? ORDER BY created_at ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving audit logs for fine #" + fineId, e);
            throw new RuntimeException("Database error retrieving audit records: " + e.getMessage(), e);
        }
        return list;
    }

    private PaymentAuditEntry mapRow(ResultSet rs) throws SQLException {
        PaymentAuditEntry e = new PaymentAuditEntry();
        e.setAuditId(rs.getInt("audit_id"));
        e.setFineId(rs.getInt("fine_id"));
        int pid = rs.getInt("payment_id");
        e.setPaymentId(rs.wasNull() ? null : pid);
        e.setEventType(AuditEventType.fromString(rs.getString("event_type")));
        e.setOldStatus(rs.getString("old_status"));
        e.setNewStatus(rs.getString("new_status"));
        e.setAmount(rs.getBigDecimal("amount"));
        int actor = rs.getInt("actor_user_id");
        e.setActorUserId(rs.wasNull() ? null : actor);
        e.setActorRole(rs.getString("actor_role"));
        e.setDetails(rs.getString("details"));
        e.setCreatedAt(rs.getTimestamp("created_at"));
        return e;
    }
}

