package com.psgitech.library.repository;

import com.psgitech.library.enums.Role;
import com.psgitech.library.model.Admin;
import com.psgitech.library.model.Librarian;
import com.psgitech.library.model.Student;
import com.psgitech.library.model.User;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for database operations on User, Student, Librarian, Admin,
 * and Remember Me persistent tokens. Uses JDBC and PreparedStatements.
 */
public class UserRepository {
    private static final Logger LOGGER = Logger.getLogger(UserRepository.class.getName());

    public User mapRowToUser(ResultSet rs) throws SQLException {
        String roleStr = rs.getString("role");
        Role role = Role.fromString(roleStr);

        User user;
        if (role == Role.STUDENT) {
            Student student = new Student();
            student.setRegisterNumber(rs.getString("register_number"));
            student.setDepartment(rs.getString("department"));
            int yr = rs.getInt("year");
            if (!rs.wasNull()) {
                student.setYear(yr);
            }
            user = student;
        } else if (role == Role.LIBRARIAN) {
            Librarian librarian = new Librarian();
            librarian.setEmployeeId(rs.getString("employee_id"));
            librarian.setMustChangePassword(rs.getBoolean("must_change_password"));
            user = librarian;
        } else {
            user = new Admin();
        }

        user.setUserId(rs.getInt("user_id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setPhone(rs.getString("phone"));
        user.setRole(role);
        user.setActive(rs.getBoolean("active"));
        try {
            user.setEmailVerified(rs.getBoolean("email_verified"));
            user.setEmailVerifiedAt(rs.getTimestamp("email_verified_at"));
        } catch (SQLException ignored) {
            user.setEmailVerified(true);
        }
        user.setCreatedAt(rs.getTimestamp("created_at"));
        user.setUpdatedAt(rs.getTimestamp("updated_at"));

        return user;
    }

    public User findById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding user by id: " + userId, e);
        }
        return null;
    }

    public User findByEmail(String email) {
        if (email == null) return null;
        String sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding user by email: " + email, e);
        }
        return null;
    }

    public Student findByRegisterNumber(String registerNumber) {
        if (registerNumber == null) return null;
        String sql = "SELECT * FROM users WHERE LOWER(register_number) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, registerNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = mapRowToUser(rs);
                    return (u instanceof Student) ? (Student) u : null;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding student by register number: " + registerNumber, e);
        }
        return null;
    }

    public Librarian findByEmployeeId(String employeeId) {
        if (employeeId == null) return null;
        String sql = "SELECT * FROM users WHERE LOWER(employee_id) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, employeeId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = mapRowToUser(rs);
                    return (u instanceof Librarian) ? (Librarian) u : null;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding librarian by employee id: " + employeeId, e);
        }
        return null;
    }

    /**
     * Finds a user by email, register number, or employee ID.
     */
    public User findByEmailOrIdentifier(String loginKey) {
        if (loginKey == null || loginKey.trim().isEmpty()) return null;
        String cleaned = loginKey.trim();

        // 1. Try finding by email
        User user = findByEmail(cleaned);
        if (user != null) return user;

        // 2. Try finding by register number
        user = findByRegisterNumber(cleaned);
        if (user != null) return user;

        // 3. Try finding by employee ID
        return findByEmployeeId(cleaned);
    }

    public boolean saveStudent(Student student) {
        return saveStudentWithMaster(student, null);
    }

    /**
     * Atomically creates a student user account and binds it to their verified
     * master student directory record within a single database transaction.
     */
    public boolean saveStudentWithMaster(Student student, Integer masterStudentId) {
        String insertUserSql = "INSERT INTO users (name, email, password_hash, phone, role, register_number, department, `year`, active, must_change_password, email_verified, email_verified_at) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateMasterSql = "UPDATE students SET registration_status = 'REGISTERED', user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE student_id = ?";

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            int generatedUserId = -1;
            try (PreparedStatement psUser = conn.prepareStatement(insertUserSql, Statement.RETURN_GENERATED_KEYS)) {
                psUser.setString(1, student.getName());
                psUser.setString(2, student.getEmail().toLowerCase());
                psUser.setString(3, student.getPasswordHash());
                psUser.setString(4, student.getPhone());
                psUser.setString(5, Role.STUDENT.name());
                psUser.setString(6, student.getRegisterNumber().toUpperCase());
                psUser.setString(7, student.getDepartment());
                if (student.getYear() != null) {
                    psUser.setInt(8, student.getYear());
                } else {
                    psUser.setNull(8, java.sql.Types.INTEGER);
                }
                psUser.setBoolean(9, student.isActive());
                psUser.setBoolean(10, false);
                psUser.setBoolean(11, student.isEmailVerified());
                psUser.setTimestamp(12, student.getEmailVerifiedAt());

                int affected = psUser.executeUpdate();
                if (affected <= 0) {
                    conn.rollback();
                    return false;
                }
                try (ResultSet rs = psUser.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedUserId = rs.getInt(1);
                        student.setUserId(generatedUserId);
                    } else {
                        conn.rollback();
                        return false;
                    }
                }
            }

            // Link master student record if provided
            if (masterStudentId != null && masterStudentId > 0) {
                try (PreparedStatement psMaster = conn.prepareStatement(updateMasterSql)) {
                    psMaster.setInt(1, generatedUserId);
                    psMaster.setInt(2, masterStudentId);
                    int updatedMaster = psMaster.executeUpdate();
                    if (updatedMaster <= 0) {
                        conn.rollback();
                        return false;
                    }
                }
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error in atomic student registration with master link: " + student.getEmail(), e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Error rolling back student registration transaction", ex);
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.WARNING, "Error closing connection after student registration", ex);
                }
            }
        }
    }

    public boolean markEmailVerified(int userId) {
        String sql = "UPDATE users SET email_verified = true, email_verified_at = NOW() WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error marking email verified for user_id=" + userId, e);
        }
        return false;
    }

    public boolean saveLibrarian(Librarian librarian) {
        String sql = "INSERT INTO users (name, email, password_hash, phone, role, employee_id, active, must_change_password) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, librarian.getName());
            ps.setString(2, librarian.getEmail().toLowerCase());
            ps.setString(3, librarian.getPasswordHash());
            ps.setString(4, librarian.getPhone());
            ps.setString(5, Role.LIBRARIAN.name());
            ps.setString(6, librarian.getEmployeeId().toUpperCase());
            ps.setBoolean(7, librarian.isActive());
            ps.setBoolean(8, librarian.isMustChangePassword());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        librarian.setUserId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error saving librarian: " + librarian.getEmail(), e);
        }
        return false;
    }

    public boolean saveAdmin(Admin admin) {
        String sql = "INSERT INTO users (name, email, password_hash, phone, role, active, must_change_password) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, admin.getName());
            ps.setString(2, admin.getEmail().toLowerCase());
            ps.setString(3, admin.getPasswordHash());
            ps.setString(4, admin.getPhone());
            ps.setString(5, Role.ADMIN.name());
            ps.setBoolean(6, admin.isActive());
            ps.setBoolean(7, false);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        admin.setUserId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error saving admin: " + admin.getEmail(), e);
        }
        return false;
    }

    public boolean updateUser(User user) {
        String sql = "UPDATE users SET name = ?, email = ?, phone = ?, department = ?, `year` = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail().toLowerCase());
            ps.setString(3, user.getPhone());
            if (user instanceof Student) {
                Student s = (Student) user;
                ps.setString(4, s.getDepartment());
                if (s.getYear() != null) {
                    ps.setInt(5, s.getYear());
                } else {
                    ps.setNull(5, java.sql.Types.INTEGER);
                }
            } else {
                ps.setNull(4, java.sql.Types.VARCHAR);
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setInt(6, user.getUserId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating user: " + user.getUserId(), e);
        }
        return false;
    }

    public boolean updatePassword(int userId, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ?, must_change_password = FALSE WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating password for user: " + userId, e);
        }
        return false;
    }

    public boolean setActiveStatus(int userId, boolean active) {
        String sql = "UPDATE users SET active = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error setting active status for user: " + userId, e);
        }
        return false;
    }

    public List<User> findAllUsers() {
        return searchAndFilterUsers(null, null, null);
    }

    public List<User> searchAndFilterUsers(String query, String roleFilter, String statusFilter) {
        List<User> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            String q = "%" + query.trim().toLowerCase() + "%";
            sql.append("AND (LOWER(name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(register_number) LIKE ? OR LOWER(employee_id) LIKE ?) ");
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        if (roleFilter != null && !roleFilter.trim().isEmpty() && !roleFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND role = ? ");
            params.add(roleFilter.trim().toUpperCase());
        }

        if (statusFilter != null && !statusFilter.trim().isEmpty() && !statusFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND active = ? ");
            params.add(Boolean.parseBoolean(statusFilter));
        }

        sql.append("ORDER BY user_id DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToUser(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error searching/filtering users", e);
        }
        return list;
    }

    // ------------------------------------------------------------------------
    // Remember Me Token Operations
    // ------------------------------------------------------------------------

    public boolean saveRememberToken(int userId, String tokenHash, Timestamp expiresAt) {
        String sql = "INSERT INTO remember_tokens (user_id, token_hash, expires_at, revoked) VALUES (?, ?, ?, FALSE)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, expiresAt);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error saving remember token for user: " + userId, e);
        }
        return false;
    }

    public Integer findValidUserIdByTokenHash(String tokenHash) {
        String sql = "SELECT user_id FROM remember_tokens WHERE token_hash = ? AND revoked = FALSE AND expires_at > CURRENT_TIMESTAMP";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_id");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding valid token hash", e);
        }
        return null;
    }

    public boolean revokeRememberToken(String tokenHash) {
        String sql = "UPDATE remember_tokens SET revoked = TRUE WHERE token_hash = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error revoking remember token", e);
        }
        return false;
    }

    public boolean revokeAllUserTokens(int userId) {
        String sql = "UPDATE remember_tokens SET revoked = TRUE WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error revoking all tokens for user: " + userId, e);
        }
        return false;
    }
}
