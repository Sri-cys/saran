package com.psgitech.library.repository;

import com.psgitech.library.model.GoodwillExtension;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for managing one-time goodwill loan due date extensions.
 */
public class GoodwillExtensionRepository {
    private static final Logger LOGGER = Logger.getLogger(GoodwillExtensionRepository.class.getName());

    /**
     * Checks if a student has already been granted a goodwill extension.
     * Enforced strictly by UNIQUE(student_id).
     */
    public boolean hasUsedGoodwillExtension(int studentId) {
        String sql = "SELECT COUNT(*) FROM goodwill_extensions WHERE student_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking goodwill extension status for student: " + studentId, e);
            throw new RuntimeException("Database error checking goodwill extension status", e);
        }
        return false;
    }

    /**
     * Retrieves the goodwill extension record for a student, if any.
     */
    public GoodwillExtension findByStudentId(int studentId) {
        String sql = "SELECT ge.*, u.name AS student_name, u.register_number, " +
                "b.title AS book_title, staff.name AS staff_name " +
                "FROM goodwill_extensions ge " +
                "LEFT JOIN users u ON ge.student_id = u.user_id " +
                "LEFT JOIN borrow_transactions bt ON (ge.transaction_id = bt.transaction_id OR ge.loan_id = bt.transaction_id) " +
                "LEFT JOIN books b ON bt.book_id = b.book_id " +
                "LEFT JOIN users staff ON ge.granted_by = staff.user_id " +
                "WHERE ge.student_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToExtension(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding goodwill extension for student: " + studentId, e);
            throw new RuntimeException("Database error finding goodwill extension", e);
        }
        return null;
    }

    /**
     * Inserts a new goodwill extension record within an existing connection/transaction.
     */
    public void insert(GoodwillExtension ext, Connection conn) throws SQLException {
        String sql = "INSERT INTO goodwill_extensions " +
                "(student_id, transaction_id, loan_id, extra_days, reason, previous_due_date, new_due_date, granted_by, granted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, ext.getStudentId());
            ps.setInt(2, ext.getTransactionId());
            ps.setInt(3, ext.getTransactionId()); // Set both loan_id and transaction_id for schema robustness
            ps.setInt(4, ext.getExtraDays());
            ps.setString(5, ext.getReason());
            ps.setDate(6, ext.getPreviousDueDate() != null ? Date.valueOf(ext.getPreviousDueDate()) : null);
            ps.setDate(7, ext.getNewDueDate() != null ? Date.valueOf(ext.getNewDueDate()) : null);
            ps.setInt(8, ext.getGrantedBy());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        ext.setId(keys.getInt(1));
                    }
                }
            }
        }
    }

    /**
     * Lists recent goodwill extensions with student and staff details.
     */
    public List<GoodwillExtension> findRecentExtensions(int limit) {
        List<GoodwillExtension> list = new ArrayList<>();
        String sql = "SELECT ge.*, u.name AS student_name, u.register_number, " +
                "b.title AS book_title, staff.name AS staff_name " +
                "FROM goodwill_extensions ge " +
                "LEFT JOIN users u ON ge.student_id = u.user_id " +
                "LEFT JOIN borrow_transactions bt ON (ge.transaction_id = bt.transaction_id OR ge.loan_id = bt.transaction_id) " +
                "LEFT JOIN books b ON bt.book_id = b.book_id " +
                "LEFT JOIN users staff ON ge.granted_by = staff.user_id " +
                "ORDER BY ge.granted_at DESC LIMIT ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToExtension(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error listing recent goodwill extensions", e);
        }
        return list;
    }

    private GoodwillExtension mapResultSetToExtension(ResultSet rs) throws SQLException {
        GoodwillExtension ext = new GoodwillExtension();
        try {
            ext.setId(rs.getInt("id"));
        } catch (SQLException e) {
            try {
                ext.setId(rs.getInt("extension_id"));
            } catch (SQLException ignored) {}
        }

        ext.setStudentId(rs.getInt("student_id"));
        try {
            ext.setTransactionId(rs.getInt("transaction_id"));
        } catch (SQLException e) {
            ext.setTransactionId(rs.getInt("loan_id"));
        }
        if (ext.getTransactionId() == 0) {
            try {
                ext.setTransactionId(rs.getInt("loan_id"));
            } catch (SQLException ignored) {}
        }

        try {
            ext.setExtraDays(rs.getInt("extra_days"));
        } catch (SQLException e) {
            ext.setExtraDays(7);
        }

        ext.setReason(rs.getString("reason"));
        Date prev = rs.getDate("previous_due_date");
        if (prev != null) ext.setPreviousDueDate(prev.toLocalDate());
        Date next = rs.getDate("new_due_date");
        if (next != null) ext.setNewDueDate(next.toLocalDate());
        ext.setGrantedBy(rs.getInt("granted_by"));
        ext.setGrantedAt(rs.getTimestamp("granted_at"));

        ext.setStudentName(rs.getString("student_name"));
        ext.setRegisterNumber(rs.getString("register_number"));
        ext.setBookTitle(rs.getString("book_title"));
        ext.setGrantedByName(rs.getString("staff_name"));
        return ext;
    }
}
