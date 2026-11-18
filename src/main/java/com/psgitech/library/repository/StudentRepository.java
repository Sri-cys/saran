package com.psgitech.library.repository;

import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.StudentMaster;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository managing data access for the Institutional Student Master Directory.
 * Enforces single source of truth for all PSG iTech students.
 */
public class StudentRepository {
    private static final Logger LOGGER = Logger.getLogger(StudentRepository.class.getName());

    public StudentMaster mapRowToStudentMaster(ResultSet rs) throws SQLException {
        StudentMaster student = new StudentMaster();
        student.setStudentId(rs.getInt("student_id"));
        student.setPermanentRollNumber(rs.getString("permanent_roll_number"));
        student.setFullName(rs.getString("full_name"));
        student.setAccountStatus(rs.getString("account_status"));
        student.setRegistrationStatus(rs.getString("registration_status"));

        int userId = rs.getInt("user_id");
        if (!rs.wasNull()) {
            student.setUserId(userId);
        } else {
            student.setUserId(null);
        }

        try {
            student.setCreatedAt(rs.getTimestamp("created_at"));
            student.setUpdatedAt(rs.getTimestamp("updated_at"));
        } catch (SQLException ignored) {}

        // Map enriched User account details if present in projection
        try {
            student.setEmail(rs.getString("email"));
            student.setDepartment(rs.getString("department"));
            int yr = rs.getInt("year");
            if (!rs.wasNull()) {
                student.setYear(yr);
            }
            student.setPhone(rs.getString("phone"));
        } catch (SQLException ignored) {}

        try {
            student.setActiveBorrowingsCount(rs.getInt("active_borrowings"));
        } catch (SQLException ignored) {}

        return student;
    }

    /**
     * Looks up a master student record by permanent roll number.
     */
    public StudentMaster findByRollNumber(String rollNumber) {
        if (rollNumber == null || rollNumber.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT s.*, u.email, u.department, u.`year`, u.phone, " +
                     "  (SELECT COUNT(*) FROM borrow_transactions bt WHERE bt.student_id = s.user_id AND bt.status = 'ACTIVE') AS active_borrowings " +
                     "FROM students s " +
                     "LEFT JOIN users u ON s.user_id = u.user_id " +
                     "WHERE LOWER(TRIM(s.permanent_roll_number)) = LOWER(TRIM(?))";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToStudentMaster(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding master student by roll number: " + rollNumber, e);
        }
        return null;
    }

    /**
     * Looks up a master student record by primary key ID.
     */
    public StudentMaster findById(int studentId) {
        String sql = "SELECT s.*, u.email, u.department, u.`year`, u.phone, " +
                     "  (SELECT COUNT(*) FROM borrow_transactions bt WHERE bt.student_id = s.user_id AND bt.status = 'ACTIVE') AS active_borrowings " +
                     "FROM students s " +
                     "LEFT JOIN users u ON s.user_id = u.user_id " +
                     "WHERE s.student_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToStudentMaster(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding master student by student_id: " + studentId, e);
        }
        return null;
    }

    /**
     * Looks up a master student record by linked user ID.
     */
    public StudentMaster findByUserId(int userId) {
        String sql = "SELECT s.*, u.email, u.department, u.`year`, u.phone, " +
                     "  (SELECT COUNT(*) FROM borrow_transactions bt WHERE bt.student_id = s.user_id AND bt.status = 'ACTIVE') AS active_borrowings " +
                     "FROM students s " +
                     "LEFT JOIN users u ON s.user_id = u.user_id " +
                     "WHERE s.user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToStudentMaster(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding master student by user_id: " + userId, e);
        }
        return null;
    }

    /**
     * Counts students matching search filter criteria.
     */
    public int count(String query, String registrationStatus, String accountStatus) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM students s WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (LOWER(s.permanent_roll_number) LIKE LOWER(?) OR LOWER(s.full_name) LIKE LOWER(?)) ");
            String q = "%" + query.trim() + "%";
            params.add(q);
            params.add(q);
        }
        if (registrationStatus != null && !registrationStatus.trim().isEmpty() && !"ALL".equalsIgnoreCase(registrationStatus)) {
            sql.append("AND s.registration_status = ? ");
            params.add(registrationStatus.trim().toUpperCase());
        }
        if (accountStatus != null && !accountStatus.trim().isEmpty() && !"ALL".equalsIgnoreCase(accountStatus)) {
            sql.append("AND s.account_status = ? ");
            params.add(accountStatus.trim().toUpperCase());
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
            LOGGER.log(Level.SEVERE, "Error counting students", e);
        }
        return 0;
    }

    /**
     * Retrieves a paginated list of students matching search filter criteria.
     */
    public List<StudentMaster> search(String query, String registrationStatus, String accountStatus, int page, int pageSize) {
        List<StudentMaster> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT s.*, u.email, u.department, u.`year`, u.phone, " +
                "  (SELECT COUNT(*) FROM borrow_transactions bt WHERE bt.student_id = s.user_id AND bt.status = 'ACTIVE') AS active_borrowings " +
                "FROM students s " +
                "LEFT JOIN users u ON s.user_id = u.user_id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (LOWER(s.permanent_roll_number) LIKE LOWER(?) OR LOWER(s.full_name) LIKE LOWER(?)) ");
            String q = "%" + query.trim() + "%";
            params.add(q);
            params.add(q);
        }
        if (registrationStatus != null && !registrationStatus.trim().isEmpty() && !"ALL".equalsIgnoreCase(registrationStatus)) {
            sql.append("AND s.registration_status = ? ");
            params.add(registrationStatus.trim().toUpperCase());
        }
        if (accountStatus != null && !accountStatus.trim().isEmpty() && !"ALL".equalsIgnoreCase(accountStatus)) {
            sql.append("AND s.account_status = ? ");
            params.add(accountStatus.trim().toUpperCase());
        }

        sql.append("ORDER BY s.permanent_roll_number ASC ");

        if (pageSize > 0) {
            int offset = Math.max(0, (page - 1) * pageSize);
            sql.append("LIMIT ? OFFSET ?");
            params.add(pageSize);
            params.add(offset);
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToStudentMaster(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error searching students", e);
        }
        return list;
    }

    /**
     * Computes summary metrics for the Student Master Directory.
     */
    public Map<String, Integer> getMetrics() {
        Map<String, Integer> metrics = new HashMap<>();
        metrics.put("total", 0);
        metrics.put("registered", 0);
        metrics.put("unregistered", 0);
        metrics.put("active", 0);
        metrics.put("inactive", 0);

        String sql = "SELECT " +
                     "  COUNT(*) AS total, " +
                     "  SUM(CASE WHEN registration_status = 'REGISTERED' THEN 1 ELSE 0 END) AS registered, " +
                     "  SUM(CASE WHEN registration_status = 'NOT_REGISTERED' THEN 1 ELSE 0 END) AS unregistered, " +
                     "  SUM(CASE WHEN account_status = 'ACTIVE' THEN 1 ELSE 0 END) AS active, " +
                     "  SUM(CASE WHEN account_status = 'INACTIVE' THEN 1 ELSE 0 END) AS inactive " +
                     "FROM students";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                metrics.put("total", rs.getInt("total"));
                metrics.put("registered", rs.getInt("registered"));
                metrics.put("unregistered", rs.getInt("unregistered"));
                metrics.put("active", rs.getInt("active"));
                metrics.put("inactive", rs.getInt("inactive"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error calculating student metrics", e);
        }
        return metrics;
    }

    /**
     * Binds a newly registered user account to their verified master student record
     * within an existing database transaction connection.
     */
    public boolean bindUserRegistration(Connection conn, int studentId, int userId) throws SQLException {
        String sql = "UPDATE students SET registration_status = 'REGISTERED', user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, studentId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Retrieves currently active borrowed books for a student.
     */
    public List<BorrowTransaction> getActiveBorrowings(int userId) {
        List<BorrowTransaction> list = new ArrayList<>();
        if (userId <= 0) return list;

        String sql = "SELECT bt.*, b.title, b.author, b.isbn, c.copy_code " +
                     "FROM borrow_transactions bt " +
                     "JOIN books b ON bt.book_id = b.book_id " +
                     "JOIN book_copies c ON bt.copy_id = c.copy_id " +
                     "WHERE bt.student_id = ? AND bt.status = 'ACTIVE' " +
                     "ORDER BY bt.due_date ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BorrowTransaction tx = new BorrowTransaction();
                    tx.setTransactionId(rs.getInt("transaction_id"));
                    tx.setStudentId(rs.getInt("student_id"));
                    tx.setBookId(rs.getInt("book_id"));
                    tx.setCopyId(rs.getInt("copy_id"));
                    tx.setIssueDate(rs.getDate("issue_date").toLocalDate());
                    tx.setDueDate(rs.getDate("due_date").toLocalDate());
                    tx.setStatus(BorrowStatus.fromString(rs.getString("status")));

                    tx.setBookTitle(rs.getString("title"));
                    tx.setBookAuthor(rs.getString("author"));
                    tx.setCopyCode(rs.getString("copy_code"));
                    list.add(tx);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving active borrowings for user_id=" + userId, e);
        }
        return list;
    }
}
