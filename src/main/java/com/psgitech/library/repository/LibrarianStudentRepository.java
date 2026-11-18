package com.psgitech.library.repository;

import com.psgitech.library.dto.librarian.NoDuesSlipDTO;
import com.psgitech.library.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for student management, profile audit, and library No-Dues clearance checks.
 */
public class LibrarianStudentRepository {
    private static final Logger LOGGER = Logger.getLogger(LibrarianStudentRepository.class.getName());
    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<Map<String, Object>> findStudents(int offset, int limit, String query, String filter) {
        StringBuilder sql = new StringBuilder(
            "SELECT u.user_id, u.name, u.email, u.phone, u.register_number, u.department, u.year, u.active, " +
            "COUNT(DISTINCT CASE WHEN t.status = 'ACTIVE' THEN t.transaction_id END) AS active_loans, " +
            "COUNT(DISTINCT CASE WHEN t.status = 'ACTIVE' AND t.due_date < CURRENT_DATE() THEN t.transaction_id END) AS overdue_loans, " +
            "COALESCE(SUM(f.outstanding_amount), 0.00) AS unpaid_fines " +
            "FROM users u " +
            "LEFT JOIN borrow_transactions t ON u.user_id = t.student_id " +
            "LEFT JOIN fines f ON u.user_id = f.student_id AND f.status IN ('UNPAID', 'PARTIALLY_PAID') " +
            "WHERE u.role = 'STUDENT' "
        );

        List<Object> params = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (u.name LIKE ? OR u.register_number LIKE ? OR u.email LIKE ?) ");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q);
        }

        if ("ACTIVE".equalsIgnoreCase(filter)) {
            sql.append("AND u.active = TRUE ");
        } else if ("INACTIVE".equalsIgnoreCase(filter)) {
            sql.append("AND u.active = FALSE ");
        }

        sql.append("GROUP BY u.user_id, u.name, u.email, u.phone, u.register_number, u.department, u.year, u.active ");

        if ("HAS_OVERDUE".equalsIgnoreCase(filter)) {
            sql.append("HAVING overdue_loans > 0 ");
        } else if ("HAS_UNPAID_FINES".equalsIgnoreCase(filter)) {
            sql.append("HAVING unpaid_fines > 0.00 ");
        }

        sql.append("ORDER BY u.register_number ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", rs.getInt("user_id"));
                    m.put("name", rs.getString("name"));
                    m.put("email", rs.getString("email"));
                    m.put("phone", rs.getString("phone"));
                    m.put("registerNumber", rs.getString("register_number"));
                    m.put("department", rs.getString("department"));
                    m.put("year", rs.getInt("year"));
                    m.put("active", rs.getBoolean("active"));
                    m.put("activeLoans", rs.getInt("active_loans"));
                    m.put("overdueLoans", rs.getInt("overdue_loans"));
                    m.put("unpaidFines", rs.getBigDecimal("unpaid_fines"));
                    list.add(m);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query students", e);
        }
        return list;
    }

    public int countStudents(String query, String filter) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(DISTINCT u.user_id) " +
            "FROM users u " +
            "LEFT JOIN borrow_transactions t ON u.user_id = t.student_id " +
            "LEFT JOIN fines f ON u.user_id = f.student_id AND f.status IN ('UNPAID', 'PARTIALLY_PAID') " +
            "WHERE u.role = 'STUDENT' "
        );

        List<Object> params = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (u.name LIKE ? OR u.register_number LIKE ? OR u.email LIKE ?) ");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q);
        }
        if ("ACTIVE".equalsIgnoreCase(filter)) {
            sql.append("AND u.active = TRUE ");
        } else if ("INACTIVE".equalsIgnoreCase(filter)) {
            sql.append("AND u.active = FALSE ");
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
            LOGGER.log(Level.SEVERE, "Failed to count students", e);
        }
        return 0;
    }

    public NoDuesSlipDTO checkNoDuesClearance(int studentId, int staffUserId, String staffName) {
        String studentSql = "SELECT user_id, name, register_number, department, year, email, active FROM users WHERE user_id = ? AND role = 'STUDENT'";
        NoDuesSlipDTO slip = new NoDuesSlipDTO();

        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(studentSql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    slip.setStudentId(rs.getInt("user_id"));
                    slip.setStudentName(rs.getString("name"));
                    slip.setRegisterNumber(rs.getString("register_number"));
                    slip.setDepartment(rs.getString("department"));
                    slip.setYear(rs.getInt("year"));
                    slip.setEmail(rs.getString("email"));
                }
            }

            // 1. Active loans
            String loansSql = "SELECT COUNT(*) FROM borrow_transactions WHERE student_id = ? AND status = 'ACTIVE'";
            try (PreparedStatement ps = conn.prepareStatement(loansSql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) slip.setActiveLoansCount(rs.getInt(1));
                }
            }

            // 2. Unpaid fines
            String finesSql = "SELECT COALESCE(SUM(outstanding_amount), 0.00) FROM fines WHERE student_id = ? AND status IN ('UNPAID', 'PARTIALLY_PAID')";
            try (PreparedStatement ps = conn.prepareStatement(finesSql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) slip.setUnpaidFinesAmount(rs.getBigDecimal(1));
                    else slip.setUnpaidFinesAmount(BigDecimal.ZERO);
                }
            }

            // 3. Pending transfers
            String transferSql = "SELECT COUNT(*) FROM book_interchanges WHERE (from_student_id = ? OR to_student_id = ?) AND status = 'REQUESTED'";
            try (PreparedStatement ps = conn.prepareStatement(transferSql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) slip.setPendingTransfersCount(rs.getInt(1));
                }
            }

            boolean isClear = slip.getActiveLoansCount() == 0 &&
                    slip.getUnpaidFinesAmount().compareTo(BigDecimal.ZERO) == 0 &&
                    slip.getPendingTransfersCount() == 0;
            slip.setEligible(isClear);

            slip.setIssuedByStaffId(staffUserId);
            slip.setIssuedByStaffName(staffName);
            slip.setIssuedTimestamp(ZonedDateTime.now(KOLKATA_ZONE).format(TS_FMT));
            slip.setCertificateReference("PSG-ND-" + Calendar.getInstance().get(Calendar.YEAR) + "-" + slip.getStudentId() + "-" + (System.currentTimeMillis() % 10000));

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to compute No-Dues clearance for student #" + studentId, e);
        }
        return slip;
    }

    public boolean updateStudentStatus(int studentId, boolean active) {
        String sql = "UPDATE users SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ? AND role = 'STUDENT'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, studentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to toggle status for student #" + studentId, e);
            return false;
        }
    }
}
