package com.psgitech.library.repository;

import com.psgitech.library.model.FineAdjustment;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for fine adjustments and waivers.
 */
public class FineAdjustmentRepository {
    private static final Logger LOGGER = Logger.getLogger(FineAdjustmentRepository.class.getName());

    public FineAdjustment createAdjustment(Connection conn, FineAdjustment adj) throws SQLException {
        String sql = "INSERT INTO fine_adjustments (fine_id, type, amount, reason, approved_by) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, adj.getFineId());
            ps.setString(2, adj.getType());
            ps.setBigDecimal(3, adj.getAmount());
            ps.setString(4, adj.getReason());
            ps.setInt(5, adj.getApprovedBy());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Failed to record fine adjustment: no rows affected.");
            }

            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    adj.setAdjustmentId(gk.getInt(1));
                }
            }
        }
        return adj;
    }

    public List<FineAdjustment> findByFineId(int fineId) {
        List<FineAdjustment> list = new ArrayList<>();
        String sql = "SELECT fa.*, u.name AS approved_by_name FROM fine_adjustments fa " +
                "JOIN users u ON fa.approved_by = u.user_id WHERE fa.fine_id = ? ORDER BY fa.created_at ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FineAdjustment fa = new FineAdjustment();
                    fa.setAdjustmentId(rs.getInt("adjustment_id"));
                    fa.setFineId(rs.getInt("fine_id"));
                    fa.setType(rs.getString("type"));
                    fa.setAmount(rs.getBigDecimal("amount"));
                    fa.setReason(rs.getString("reason"));
                    fa.setApprovedBy(rs.getInt("approved_by"));
                    fa.setCreatedAt(rs.getTimestamp("created_at"));
                    fa.setApprovedByName(rs.getString("approved_by_name"));
                    list.add(fa);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving fine adjustments for fine #" + fineId, e);
            throw new RuntimeException("Database error retrieving adjustments: " + e.getMessage(), e);
        }
        return list;
    }
}

