package com.psgitech.library.repository;

import com.psgitech.library.model.LoanRenewalRecord;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RenewalRepository {

    public LoanRenewalRecord saveRenewal(Connection conn, LoanRenewalRecord record) throws SQLException {
        String sql = "INSERT INTO loan_renewals (transaction_id, student_id, previous_due_date, new_due_date, renewal_number, renewed_by) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, record.getTransactionId());
            ps.setInt(2, record.getStudentId());
            ps.setDate(3, Date.valueOf(record.getPreviousDueDate()));
            ps.setDate(4, Date.valueOf(record.getNewDueDate()));
            ps.setInt(5, record.getRenewalNumber());
            ps.setInt(6, record.getRenewedBy());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    record.setRenewalId(rs.getInt(1));
                }
            }
        }
        return record;
    }

    public List<LoanRenewalRecord> findRenewalsByTransaction(int transactionId) throws SQLException {
        String sql = "SELECT * FROM loan_renewals WHERE transaction_id = ? ORDER BY renewal_id ASC";
        List<LoanRenewalRecord> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LoanRenewalRecord rec = new LoanRenewalRecord();
                    rec.setRenewalId(rs.getInt("renewal_id"));
                    rec.setTransactionId(rs.getInt("transaction_id"));
                    rec.setStudentId(rs.getInt("student_id"));
                    Date prev = rs.getDate("previous_due_date");
                    if (prev != null) rec.setPreviousDueDate(prev.toLocalDate());
                    Date next = rs.getDate("new_due_date");
                    if (next != null) rec.setNewDueDate(next.toLocalDate());
                    rec.setRenewalNumber(rs.getInt("renewal_number"));
                    rec.setRenewedAt(rs.getTimestamp("renewed_at"));
                    rec.setRenewedBy(rs.getInt("renewed_by"));
                    list.add(rec);
                }
            }
        }
        return list;
    }

    public int countRenewalsByTransaction(int transactionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM loan_renewals WHERE transaction_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}
