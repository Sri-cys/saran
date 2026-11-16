package com.psgitech.library.repository;

import com.psgitech.library.config.FineConfig;
import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.util.DBConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository / Data Access Object for overdue fine records.
 * Uses PreparedStatement for all queries and provides Connection-accepting overloads
 * for atomic multi-table transaction orchestration.
 */
public class FineRepository {
    private static final Logger LOGGER = Logger.getLogger(FineRepository.class.getName());

    private static final String BASE_JOINED_SELECT =
            "SELECT f.*, " +
            "u.name AS student_name, u.register_number, u.department AS student_department, " +
            "b.title AS book_title, b.isbn AS book_isbn, " +
            "c.copy_code, " +
            "bt.due_date, bt.return_date " +
            "FROM fines f " +
            "JOIN users u ON f.student_id = u.user_id " +
            "JOIN books b ON f.book_id = b.book_id " +
            "JOIN book_copies c ON f.copy_id = c.copy_id " +
            "JOIN borrow_transactions bt ON f.borrow_transaction_id = bt.transaction_id ";

    /**
     * Inserts a new fine record inside an existing transaction connection.
     */
    public FineRecord createFine(Connection conn, FineRecord record) throws SQLException {
        String sql = "INSERT INTO fines (borrow_transaction_id, student_id, book_id, copy_id, " +
                "overdue_days, chargeable_days, fine_per_day, grace_period_days, max_fine_applied, " +
                "original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, record.getBorrowTransactionId());
            ps.setInt(2, record.getStudentId());
            ps.setInt(3, record.getBookId());
            ps.setInt(4, record.getCopyId());
            ps.setInt(5, record.getOverdueDays());
            ps.setInt(6, record.getChargeableDays());
            ps.setBigDecimal(7, record.getFinePerDay());
            ps.setInt(8, record.getGracePeriodDays());
            if (record.getMaxFineApplied() != null) {
                ps.setBigDecimal(9, record.getMaxFineApplied());
            } else {
                ps.setNull(9, Types.DECIMAL);
            }
            ps.setBigDecimal(10, record.getOriginalAmount());
            ps.setBigDecimal(11, record.getPaidAmount());
            ps.setBigDecimal(12, record.getWaivedAmount());
            ps.setBigDecimal(13, record.getOutstandingAmount());
            ps.setString(14, record.getStatus().name());
            ps.setString(15, record.getFineType());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Failed to create fine record: no rows affected.");
            }

            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    record.setFineId(gk.getInt(1));
                }
            }
        }
        return record;
    }

    /**
     * Locks a fine record FOR UPDATE inside an active transaction.
     */
    public FineRecord lockFineById(Connection conn, int fineId) throws SQLException {
        String sql = BASE_JOINED_SELECT + " WHERE f.fine_id = ? FOR UPDATE";
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

    /**
     * Finds a fine by its primary key with joined metadata.
     */
    public FineRecord findById(Connection conn, int fineId) throws SQLException {
        String sql = BASE_JOINED_SELECT + " WHERE f.fine_id = ?";
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

    public FineRecord findById(int fineId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, fineId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding fine #" + fineId, e);
            throw new RuntimeException("Database error retrieving fine: " + e.getMessage(), e);
        }
    }

    /**
     * Finds fine record for a specific borrow transaction.
     */
    public FineRecord findByBorrowTransactionId(Connection conn, int txId) throws SQLException {
        String sql = BASE_JOINED_SELECT + " WHERE f.borrow_transaction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, txId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Checks if a student has any unpaid or partially paid fine exceeding the configured threshold.
     */
    public boolean hasOutstandingFines(int studentId, Connection conn) throws SQLException {
        BigDecimal threshold = FineConfig.getBorrowBlockThreshold();
        String sql = "SELECT COUNT(*) FROM fines WHERE student_id = ? AND outstanding_amount > ? AND status IN ('UNPAID', 'PARTIALLY_PAID')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setBigDecimal(2, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public boolean hasOutstandingFines(int studentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return hasOutstandingFines(studentId, conn);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking outstanding fines for student #" + studentId, e);
            throw new RuntimeException("Database error checking student fine dues: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all fines for a specific student ordered chronologically.
     */
    public List<FineRecord> findFinesByStudent(int studentId) {
        List<FineRecord> list = new ArrayList<>();
        String sql = BASE_JOINED_SELECT + " WHERE f.student_id = ? ORDER BY f.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding fines for student #" + studentId, e);
            throw new RuntimeException("Database error retrieving student fines: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Updates fine amounts and status inside a transaction with conditional status guard.
     */
    public int updateFineAmountsAndStatus(Connection conn, int fineId, BigDecimal paid,
                                         BigDecimal waived, BigDecimal outstanding,
                                         FineStatus newStatus) throws SQLException {
        String sql = "UPDATE fines SET paid_amount = ?, waived_amount = ?, outstanding_amount = ?, " +
                "status = ?, updated_at = CURRENT_TIMESTAMP WHERE fine_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, paid.setScale(2, RoundingMode.HALF_UP));
            ps.setBigDecimal(2, waived.setScale(2, RoundingMode.HALF_UP));
            ps.setBigDecimal(3, outstanding.setScale(2, RoundingMode.HALF_UP));
            ps.setString(4, newStatus.name());
            ps.setInt(5, fineId);
            return ps.executeUpdate();
        }
    }

    /**
     * Paginated search for staff fine workspace.
     */
    public List<FineRecord> findFinesPaginated(String regNoFilter, FineStatus statusFilter, int page, int pageSize) {
        List<FineRecord> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(BASE_JOINED_SELECT).append(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (regNoFilter != null && !regNoFilter.trim().isEmpty()) {
            sql.append(" AND u.register_number LIKE ? ");
            params.add("%" + regNoFilter.trim() + "%");
        }
        if (statusFilter != null) {
            sql.append(" AND f.status = ? ");
            params.add(statusFilter.name());
        }

        sql.append(" ORDER BY f.created_at DESC LIMIT ? OFFSET ? ");
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
            LOGGER.log(Level.SEVERE, "Error querying paginated fines", e);
            throw new RuntimeException("Database error retrieving fines list: " + e.getMessage(), e);
        }
        return list;
    }

    public int countFines(String regNoFilter, FineStatus statusFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM fines f JOIN users u ON f.student_id = u.user_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (regNoFilter != null && !regNoFilter.trim().isEmpty()) {
            sql.append(" AND u.register_number LIKE ? ");
            params.add("%" + regNoFilter.trim() + "%");
        }
        if (statusFilter != null) {
            sql.append(" AND f.status = ? ");
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
            LOGGER.log(Level.SEVERE, "Error counting fines", e);
            throw new RuntimeException("Database error counting fines: " + e.getMessage(), e);
        }
        return 0;
    }

    private FineRecord mapRow(ResultSet rs) throws SQLException {
        FineRecord f = new FineRecord();
        f.setFineId(rs.getInt("fine_id"));
        f.setBorrowTransactionId(rs.getInt("borrow_transaction_id"));
        f.setStudentId(rs.getInt("student_id"));
        f.setBookId(rs.getInt("book_id"));
        f.setCopyId(rs.getInt("copy_id"));
        f.setOverdueDays(rs.getInt("overdue_days"));
        f.setChargeableDays(rs.getInt("chargeable_days"));
        f.setFinePerDay(rs.getBigDecimal("fine_per_day"));
        f.setGracePeriodDays(rs.getInt("grace_period_days"));
        f.setMaxFineApplied(rs.getBigDecimal("max_fine_applied"));
        f.setOriginalAmount(rs.getBigDecimal("original_amount"));
        f.setPaidAmount(rs.getBigDecimal("paid_amount"));
        f.setWaivedAmount(rs.getBigDecimal("waived_amount"));
        f.setOutstandingAmount(rs.getBigDecimal("outstanding_amount"));
        f.setStatus(FineStatus.fromString(rs.getString("status")));
        try {
            String ft = rs.getString("fine_type");
            if (ft != null && !ft.trim().isEmpty()) {
                f.setFineType(ft.trim().toUpperCase());
            }
        } catch (SQLException ignored) {}
        f.setCreatedAt(rs.getTimestamp("created_at"));
        f.setUpdatedAt(rs.getTimestamp("updated_at"));

        f.setStudentName(rs.getString("student_name"));
        f.setRegisterNumber(rs.getString("register_number"));
        f.setStudentDepartment(rs.getString("student_department"));
        f.setBookTitle(rs.getString("book_title"));
        f.setBookIsbn(rs.getString("book_isbn"));
        f.setCopyCode(rs.getString("copy_code"));

        Date dueDate = rs.getDate("due_date");
        if (dueDate != null) f.setDueDate(dueDate.toLocalDate());
        Date returnDate = rs.getDate("return_date");
        if (returnDate != null) f.setReturnDate(returnDate.toLocalDate());

        return f;
    }
}

