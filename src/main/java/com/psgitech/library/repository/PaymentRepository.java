package com.psgitech.library.repository;

import com.psgitech.library.enums.PaymentMethodType;
import com.psgitech.library.enums.PaymentStatus;
import com.psgitech.library.model.Payment;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository / Data Access Object for fine payment transactions.
 * Manages lifecycle status transitions, idempotency lookups, and audit synchronization.
 */
public class PaymentRepository {
    private static final Logger LOGGER = Logger.getLogger(PaymentRepository.class.getName());

    private static final String BASE_JOINED_SELECT =
            "SELECT p.*, " +
            "u.name AS student_name, u.register_number, " +
            "b.title AS book_title " +
            "FROM payments p " +
            "JOIN users u ON p.student_id = u.user_id " +
            "JOIN fines f ON p.fine_id = f.fine_id " +
            "JOIN books b ON f.book_id = b.book_id ";

    /**
     * Inserts a new payment record inside an active connection.
     */
    public Payment createPayment(Connection conn, Payment payment) throws SQLException {
        String sql = "INSERT INTO payments (fine_id, student_id, amount, method, status, " +
                "payment_code, reference_id, idempotency_key, receipt_number) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, payment.getFineId());
            ps.setInt(2, payment.getStudentId());
            ps.setBigDecimal(3, payment.getAmount());
            ps.setString(4, payment.getMethod().name());
            ps.setString(5, payment.getStatus().name());
            ps.setString(6, payment.getPaymentCode());
            ps.setString(7, payment.getReferenceId());
            ps.setString(8, payment.getIdempotencyKey());
            ps.setString(9, payment.getReceiptNumber());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Failed to create payment record: no rows affected.");
            }

            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    payment.setPaymentId(gk.getInt(1));
                }
            }
        }
        return payment;
    }

    /**
     * Locks a payment record FOR UPDATE inside an active transaction.
     */
    public Payment lockPaymentById(Connection conn, int paymentId) throws SQLException {
        String sql = BASE_JOINED_SELECT + " WHERE p.payment_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Payment lockPaymentByCode(Connection conn, String code) throws SQLException {
        String sql = BASE_JOINED_SELECT + " WHERE p.payment_code = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Payment findById(Connection conn, int paymentId) throws SQLException {
        String sql = BASE_JOINED_SELECT + " WHERE p.payment_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Payment findById(int paymentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, paymentId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding payment #" + paymentId, e);
            throw new RuntimeException("Database error retrieving payment: " + e.getMessage(), e);
        }
    }

    public Payment findByPaymentCode(String code) {
        String sql = BASE_JOINED_SELECT + " WHERE p.payment_code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding payment by code: " + code, e);
            throw new RuntimeException("Database error retrieving payment: " + e.getMessage(), e);
        }
        return null;
    }

    public Payment findByReference(String method, String referenceId) {
        String sql = BASE_JOINED_SELECT + " WHERE p.method = ? AND p.reference_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, method);
            ps.setString(2, referenceId.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding payment by reference: " + referenceId, e);
            throw new RuntimeException("Database error retrieving payment: " + e.getMessage(), e);
        }
        return null;
    }

    public Payment findByIdempotencyKey(int studentId, String idempotencyKey) {
        String sql = BASE_JOINED_SELECT + " WHERE p.student_id = ? AND p.idempotency_key = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setString(2, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding payment by idempotency key: " + idempotencyKey, e);
            throw new RuntimeException("Database error retrieving payment: " + e.getMessage(), e);
        }
        return null;
    }

    public Payment findActiveByFineId(Connection conn, int fineId) throws SQLException {
        String sql = BASE_JOINED_SELECT + " WHERE p.fine_id = ? AND p.status IN ('INITIATED', 'PENDING_VERIFICATION')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Payment> findPaymentsByStudent(int studentId) {
        List<Payment> list = new ArrayList<>();
        String sql = BASE_JOINED_SELECT + " WHERE p.student_id = ? ORDER BY p.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Payment p = mapRow(rs);
                    List<Integer> fIds = findFineIdsByPaymentId(conn, p.getPaymentId());
                    if (!fIds.isEmpty()) {
                        p.setSettledFineIds(fIds);
                        p.setFinesSettledCount(fIds.size());
                        p.setPaymentType(fIds.size() > 1 ? "BULK" : "INDIVIDUAL");
                    } else {
                        List<Integer> single = new ArrayList<>();
                        if (p.getFineId() > 0) single.add(p.getFineId());
                        p.setSettledFineIds(single);
                        p.setFinesSettledCount(1);
                        p.setPaymentType("INDIVIDUAL");
                    }
                    list.add(p);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding payments for student #" + studentId, e);
            throw new RuntimeException("Database error retrieving student payments: " + e.getMessage(), e);
        }
        return list;
    }

    public void linkPaymentFines(Connection conn, int paymentId, List<com.psgitech.library.model.FineRecord> fines) throws SQLException {
        String sql = "INSERT INTO payment_fines (payment_id, fine_id, amount_applied) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (com.psgitech.library.model.FineRecord fine : fines) {
                ps.setInt(1, paymentId);
                ps.setInt(2, fine.getFineId());
                ps.setBigDecimal(3, fine.getOutstandingAmount());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<Integer> findFineIdsByPaymentId(Connection conn, int paymentId) {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT fine_id FROM payment_fines WHERE payment_id = ? ORDER BY fine_id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getInt(1));
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    public List<Integer> findFineIdsByPaymentId(int paymentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findFineIdsByPaymentId(conn, paymentId);
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }

    public boolean isFineInActivePayment(int fineId) {
        String sql = "SELECT COUNT(*) FROM payments p " +
                     "LEFT JOIN payment_fines pf ON p.payment_id = pf.payment_id " +
                     "WHERE (p.fine_id = ? OR pf.fine_id = ?) AND p.status IN ('INITIATED', 'PENDING_VERIFICATION')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fineId);
            ps.setInt(2, fineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error checking active payment for fine #" + fineId, e);
        }
        return false;
    }

    public List<Payment> findPendingVerifications() {
        List<Payment> list = new ArrayList<>();
        String sql = BASE_JOINED_SELECT + " WHERE p.status = 'PENDING_VERIFICATION' ORDER BY p.submitted_at ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding pending verifications", e);
            throw new RuntimeException("Database error retrieving pending verifications: " + e.getMessage(), e);
        }
        return list;
    }

    public int countRecentInitiations(int studentId, int minutes) {
        String sql = "SELECT COUNT(*) FROM payments WHERE student_id = ? AND created_at >= ?";
        java.sql.Timestamp cutoff = new java.sql.Timestamp(System.currentTimeMillis() - (minutes * 60L * 1000L));
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setTimestamp(2, cutoff);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking initiation rate limit for student #" + studentId, e);
            throw new RuntimeException("Database error checking rate limit: " + e.getMessage(), e);
        }
        return 0;
    }

    public int cancelStaleInitiations(Connection conn, int fineId, int ttlMinutes) throws SQLException {
        String sql = "UPDATE payments SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP " +
                "WHERE fine_id = ? AND status = 'INITIATED' AND created_at < ?";
        java.sql.Timestamp cutoff = new java.sql.Timestamp(System.currentTimeMillis() - (ttlMinutes * 60L * 1000L));
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fineId);
            ps.setTimestamp(2, cutoff);
            return ps.executeUpdate();
        }
    }

    public int updatePayment(Connection conn, Payment payment) throws SQLException {
        String sql = "UPDATE payments SET status = ?, reference_id = ?, receipt_number = ?, " +
                "submitted_at = ?, verified_by = ?, verified_at = ?, rejection_reason = ?, " +
                "student_note = ?, disputed = ?, refunded_by = ?, refunded_at = ?, refund_reason = ?, " +
                "updated_at = CURRENT_TIMESTAMP WHERE payment_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payment.getStatus().name());
            ps.setString(2, payment.getReferenceId());
            ps.setString(3, payment.getReceiptNumber());
            ps.setTimestamp(4, payment.getSubmittedAt());

            if (payment.getVerifiedBy() != null) {
                ps.setInt(5, payment.getVerifiedBy());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setTimestamp(6, payment.getVerifiedAt());
            ps.setString(7, payment.getRejectionReason());
            ps.setString(8, payment.getStudentNote());
            ps.setBoolean(9, payment.isDisputed());

            if (payment.getRefundedBy() != null) {
                ps.setInt(10, payment.getRefundedBy());
            } else {
                ps.setNull(10, Types.INTEGER);
            }
            ps.setTimestamp(11, payment.getRefundedAt());
            ps.setString(12, payment.getRefundReason());
            ps.setInt(13, payment.getPaymentId());

            return ps.executeUpdate();
        }
    }

    public List<Payment> findAllPaymentsPaginated(String regNoFilter, PaymentStatus statusFilter, int page, int pageSize) {
        List<Payment> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(BASE_JOINED_SELECT).append(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (regNoFilter != null && !regNoFilter.trim().isEmpty()) {
            sql.append(" AND u.register_number LIKE ? ");
            params.add("%" + regNoFilter.trim() + "%");
        }
        if (statusFilter != null) {
            sql.append(" AND p.status = ? ");
            params.add(statusFilter.name());
        }

        sql.append(" ORDER BY p.created_at DESC LIMIT ? OFFSET ? ");
        int offset = Math.max(0, (page - 1) * pageSize);
        params.add(pageSize);
        params.add(offset);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error querying paginated payments", e);
            throw new RuntimeException("Database error retrieving payments list: " + e.getMessage(), e);
        }
        return list;
    }

    public int countPayments(String regNoFilter, PaymentStatus statusFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM payments p JOIN users u ON p.student_id = u.user_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (regNoFilter != null && !regNoFilter.trim().isEmpty()) {
            sql.append(" AND u.register_number LIKE ? ");
            params.add("%" + regNoFilter.trim() + "%");
        }
        if (statusFilter != null) {
            sql.append(" AND p.status = ? ");
            params.add(statusFilter.name());
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting payments", e);
            throw new RuntimeException("Database error counting payments: " + e.getMessage(), e);
        }
        return 0;
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setFineId(rs.getInt("fine_id"));
        p.setStudentId(rs.getInt("student_id"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setMethod(PaymentMethodType.fromString(rs.getString("method")));
        p.setStatus(PaymentStatus.fromString(rs.getString("status")));
        p.setPaymentCode(rs.getString("payment_code"));
        p.setReferenceId(rs.getString("reference_id"));
        p.setIdempotencyKey(rs.getString("idempotency_key"));
        p.setReceiptNumber(rs.getString("receipt_number"));
        p.setSubmittedAt(rs.getTimestamp("submitted_at"));

        int verifier = rs.getInt("verified_by");
        p.setVerifiedBy(rs.wasNull() ? null : verifier);
        p.setVerifiedAt(rs.getTimestamp("verified_at"));
        p.setRejectionReason(rs.getString("rejection_reason"));
        p.setStudentNote(rs.getString("student_note"));
        p.setDisputed(rs.getBoolean("disputed"));

        int refundActor = rs.getInt("refunded_by");
        p.setRefundedBy(rs.wasNull() ? null : refundActor);
        p.setRefundedAt(rs.getTimestamp("refunded_at"));
        p.setRefundReason(rs.getString("refund_reason"));

        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));

        p.setStudentName(rs.getString("student_name"));
        p.setRegisterNumber(rs.getString("register_number"));
        p.setBookTitle(rs.getString("book_title"));

        return p;
    }
}
