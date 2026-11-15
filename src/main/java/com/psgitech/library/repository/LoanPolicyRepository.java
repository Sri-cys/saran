package com.psgitech.library.repository;

import com.psgitech.library.exception.StaleVersionConflictException;
import com.psgitech.library.model.BookPolicyState;
import com.psgitech.library.model.LoanPolicyChangeRecord;
import com.psgitech.library.model.LoanPolicySettings;
import com.psgitech.library.model.LoanPolicyWindow;
import com.psgitech.library.policy.HighDemandLoanPolicy;
import com.psgitech.library.policy.PolicyTransitionRule;
import com.psgitech.library.policy.StandardLoanPolicy;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for dynamic loan policy configuration, persistent book state, and change audit.
 * Provides Connection-accepting overloads to participate safely in outer business transactions.
 */
public class LoanPolicyRepository {

    private static final Logger LOGGER = Logger.getLogger(LoanPolicyRepository.class.getName());

    public LoanPolicySettings getSettings() {
        try (Connection conn = DBConnection.getConnection()) {
            return getSettings(conn);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load loan policy settings, returning defaults", e);
            return LoanPolicySettings.defaultSettings();
        }
    }

    public LoanPolicySettings getSettings(Connection conn) throws SQLException {
        String sql = "SELECT id, enabled, enter_threshold, exit_threshold, high_demand_days, version, updated_by, updated_at " +
                "FROM loan_policy_settings WHERE id = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new LoanPolicySettings(
                        rs.getBoolean("enabled"),
                        rs.getInt("enter_threshold"),
                        rs.getInt("exit_threshold"),
                        rs.getInt("high_demand_days"),
                        rs.getInt("version"),
                        (Integer) rs.getObject("updated_by"),
                        rs.getTimestamp("updated_at")
                );
            }
        }
        return LoanPolicySettings.defaultSettings();
    }

    public boolean updateSettings(Connection conn, LoanPolicySettings settings, int expectedVersion) throws SQLException {
        String sql = "UPDATE loan_policy_settings SET enabled = ?, enter_threshold = ?, exit_threshold = ?, " +
                "high_demand_days = ?, version = version + 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id = 1 AND version = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, settings.isEnabled());
            ps.setInt(2, settings.getEnterThreshold());
            ps.setInt(3, settings.getExitThreshold());
            ps.setInt(4, settings.getHighDemandDays());
            if (settings.getUpdatedBy() != null) {
                ps.setInt(5, settings.getUpdatedBy());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setInt(6, expectedVersion);

            int rows = ps.executeUpdate();
            return rows == 1;
        }
    }

    public void insertChangeHistory(Connection conn, LoanPolicyChangeRecord change) throws SQLException {
        String sql = "INSERT INTO loan_policy_changes (changed_by, changed_by_name, changed_at, " +
                "old_enabled, new_enabled, old_enter_threshold, new_enter_threshold, " +
                "old_exit_threshold, new_exit_threshold, old_high_demand_days, new_high_demand_days, version) " +
                "VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (change.getChangedBy() != null) {
                ps.setInt(1, change.getChangedBy());
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setString(2, change.getChangedByName());
            ps.setBoolean(3, change.isOldEnabled());
            ps.setBoolean(4, change.isNewEnabled());
            ps.setInt(5, change.getOldEnterThreshold());
            ps.setInt(6, change.getNewEnterThreshold());
            ps.setInt(7, change.getOldExitThreshold());
            ps.setInt(8, change.getNewExitThreshold());
            ps.setInt(9, change.getOldHighDemandDays());
            ps.setInt(10, change.getNewHighDemandDays());
            ps.setInt(11, change.getVersion());
            ps.executeUpdate();
        }
    }

    public BookPolicyState getBookPolicyState(Connection conn, int bookId, boolean forUpdate) throws SQLException {
        String sql = "SELECT book_id, policy_code, entered_at, last_evaluated_at, last_demand " +
                "FROM book_loan_policy_state WHERE book_id = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BookPolicyState(
                            rs.getInt("book_id"),
                            rs.getString("policy_code"),
                            rs.getTimestamp("entered_at"),
                            rs.getTimestamp("last_evaluated_at"),
                            rs.getInt("last_demand")
                    );
                }
            }
        }
        return null;
    }

    public void upsertBookPolicyState(Connection conn, int bookId, String policyCode, int demand, boolean stateChanged) throws SQLException {
        if (stateChanged) {
            String sql = "INSERT INTO book_loan_policy_state (book_id, policy_code, entered_at, last_evaluated_at, last_demand) " +
                    "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?) " +
                    "ON DUPLICATE KEY UPDATE policy_code = VALUES(policy_code), entered_at = CURRENT_TIMESTAMP, " +
                    "last_evaluated_at = CURRENT_TIMESTAMP, last_demand = VALUES(last_demand)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, bookId);
                ps.setString(2, policyCode);
                ps.setInt(3, demand);
                ps.executeUpdate();
            }
        } else {
            String sql = "INSERT INTO book_loan_policy_state (book_id, policy_code, entered_at, last_evaluated_at, last_demand) " +
                    "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?) " +
                    "ON DUPLICATE KEY UPDATE last_evaluated_at = CURRENT_TIMESTAMP, last_demand = VALUES(last_demand)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, bookId);
                ps.setString(2, policyCode);
                ps.setInt(3, demand);
                ps.executeUpdate();
            }
        }
    }

    public int countDemandForBook(Connection conn, int bookId, int excludedStudentId, boolean forShare) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reservations " +
                "WHERE book_id = ? AND status IN ('WAITING', 'ALLOCATED') AND student_id != ?" +
                (forShare ? " FOR SHARE" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            ps.setInt(2, excludedStudentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public int countDemandForBookReadOnly(Connection conn, int bookId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reservations " +
                "WHERE book_id = ? AND status IN ('WAITING', 'ALLOCATED')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public Map<Integer, Integer> countDemandBatched(Connection conn, List<Integer> bookIds) throws SQLException {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }

        StringBuilder sql = new StringBuilder("SELECT book_id, COUNT(*) AS demand_count FROM reservations " +
                "WHERE status IN ('WAITING', 'ALLOCATED') AND book_id IN (");
        for (int i = 0; i < bookIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(") GROUP BY book_id");

        Map<Integer, Integer> map = new HashMap<>();
        for (Integer id : bookIds) {
            map.put(id, 0);
        }

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < bookIds.size(); i++) {
                ps.setInt(i + 1, bookIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("book_id"), rs.getInt("demand_count"));
                }
            }
        }
        return map;
    }

    public Map<Integer, BookPolicyState> getBookPolicyStatesBatched(Connection conn, List<Integer> bookIds) throws SQLException {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }

        StringBuilder sql = new StringBuilder("SELECT book_id, policy_code, entered_at, last_evaluated_at, last_demand " +
                "FROM book_loan_policy_state WHERE book_id IN (");
        for (int i = 0; i < bookIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");

        Map<Integer, BookPolicyState> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < bookIds.size(); i++) {
                ps.setInt(i + 1, bookIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BookPolicyState s = new BookPolicyState(
                            rs.getInt("book_id"),
                            rs.getString("policy_code"),
                            rs.getTimestamp("entered_at"),
                            rs.getTimestamp("last_evaluated_at"),
                            rs.getInt("last_demand")
                    );
                    map.put(s.getBookId(), s);
                }
            }
        }
        return map;
    }

    public List<Map<String, Object>> listHighDemandBooks(int offset, int limit) {
        String sql = "SELECT b.book_id, b.title, b.author, b.isbn, b.category, b.department, " +
                "s.policy_code, s.entered_at, s.last_evaluated_at, s.last_demand, " +
                "(SELECT COUNT(*) FROM reservations r WHERE r.book_id = b.book_id AND r.status IN ('WAITING', 'ALLOCATED')) AS live_demand " +
                "FROM book_loan_policy_state s " +
                "JOIN books b ON s.book_id = b.book_id " +
                "WHERE s.policy_code = 'HIGH_DEMAND' AND b.is_archived = FALSE " +
                "ORDER BY s.entered_at DESC, b.title ASC " +
                "LIMIT ? OFFSET ?";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("bookId", rs.getInt("book_id"));
                    map.put("title", rs.getString("title"));
                    map.put("author", rs.getString("author"));
                    map.put("isbn", rs.getString("isbn"));
                    map.put("category", rs.getString("category"));
                    map.put("department", rs.getString("department"));
                    map.put("policyCode", rs.getString("policy_code"));
                    map.put("enteredAt", rs.getTimestamp("entered_at") != null ? rs.getTimestamp("entered_at").toString() : null);
                    map.put("lastEvaluatedAt", rs.getTimestamp("last_evaluated_at") != null ? rs.getTimestamp("last_evaluated_at").toString() : null);
                    map.put("demand", rs.getInt("live_demand"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to list high-demand books", e);
        }
        return list;
    }

    public int countHighDemandBooks() {
        String sql = "SELECT COUNT(*) FROM book_loan_policy_state s " +
                "JOIN books b ON s.book_id = b.book_id " +
                "WHERE s.policy_code = 'HIGH_DEMAND' AND b.is_archived = FALSE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to count high-demand books", e);
        }
        return 0;
    }

    public List<LoanPolicyChangeRecord> getChangeHistory(int offset, int limit) {
        String sql = "SELECT change_id, changed_by, changed_by_name, changed_at, " +
                "old_enabled, new_enabled, old_enter_threshold, new_enter_threshold, " +
                "old_exit_threshold, new_exit_threshold, old_high_demand_days, new_high_demand_days, version " +
                "FROM loan_policy_changes ORDER BY changed_at DESC LIMIT ? OFFSET ?";
        List<LoanPolicyChangeRecord> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LoanPolicyChangeRecord r = new LoanPolicyChangeRecord();
                    r.setChangeId(rs.getInt("change_id"));
                    r.setChangedBy((Integer) rs.getObject("changed_by"));
                    r.setChangedByName(rs.getString("changed_by_name"));
                    r.setChangedAt(rs.getTimestamp("changed_at"));
                    r.setOldEnabled(rs.getBoolean("old_enabled"));
                    r.setNewEnabled(rs.getBoolean("new_enabled"));
                    r.setOldEnterThreshold(rs.getInt("old_enter_threshold"));
                    r.setNewEnterThreshold(rs.getInt("new_enter_threshold"));
                    r.setOldExitThreshold(rs.getInt("old_exit_threshold"));
                    r.setNewExitThreshold(rs.getInt("new_exit_threshold"));
                    r.setOldHighDemandDays(rs.getInt("old_high_demand_days"));
                    r.setNewHighDemandDays(rs.getInt("new_high_demand_days"));
                    r.setVersion(rs.getInt("version"));
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch loan policy change history", e);
        }
        return list;
    }

    public int countChangeHistory() {
        String sql = "SELECT COUNT(*) FROM loan_policy_changes";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to count change history", e);
        }
        return 0;
    }

    public int previewProposedSettings(int enterThreshold, int exitThreshold, int highDemandDays) {
        String demandSql = "SELECT r.book_id, COUNT(*) AS demand_count, s.policy_code " +
                "FROM reservations r " +
                "LEFT JOIN book_loan_policy_state s ON r.book_id = s.book_id " +
                "WHERE r.status IN ('WAITING', 'ALLOCATED') " +
                "GROUP BY r.book_id, s.policy_code";

        LoanPolicySettings proposed = new LoanPolicySettings(
                true, enterThreshold, exitThreshold, highDemandDays, 1, null, null
        );

        int highDemandCount = 0;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(demandSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int demand = rs.getInt("demand_count");
                String prevState = rs.getString("policy_code");
                String nextState = PolicyTransitionRule.next(prevState, demand, proposed);
                if (HighDemandLoanPolicy.CODE.equalsIgnoreCase(nextState)) {
                    highDemandCount++;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to compute settings preview", e);
        }
        return highDemandCount;
    }

    public Integer getActiveWindowOverride(Connection conn, LocalDate date) throws SQLException {
        String sql = "SELECT enter_threshold_override FROM loan_policy_windows " +
                "WHERE active = TRUE AND start_date <= ? AND end_date >= ? " +
                "ORDER BY enter_threshold_override ASC LIMIT 1";
        Date d = Date.valueOf(date != null ? date : LocalDate.now());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, d);
            ps.setDate(2, d);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("enter_threshold_override");
                }
            }
        }
        return null;
    }

    public List<LoanPolicyWindow> listWindows() {
        String sql = "SELECT window_id, name, start_date, end_date, enter_threshold_override, active, created_by, created_at " +
                "FROM loan_policy_windows ORDER BY start_date DESC, window_id DESC";
        List<LoanPolicyWindow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LoanPolicyWindow w = new LoanPolicyWindow(
                        rs.getInt("window_id"),
                        rs.getString("name"),
                        rs.getDate("start_date") != null ? rs.getDate("start_date").toLocalDate() : null,
                        rs.getDate("end_date") != null ? rs.getDate("end_date").toLocalDate() : null,
                        rs.getInt("enter_threshold_override"),
                        rs.getBoolean("active"),
                        (Integer) rs.getObject("created_by"),
                        rs.getTimestamp("created_at")
                );
                list.add(w);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to list loan policy windows", e);
        }
        return list;
    }

    public boolean createWindow(LoanPolicyWindow window) {
        String sql = "INSERT INTO loan_policy_windows (name, start_date, end_date, enter_threshold_override, active, created_by, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, window.getName());
            ps.setDate(2, Date.valueOf(window.getStartDate()));
            ps.setDate(3, Date.valueOf(window.getEndDate()));
            ps.setInt(4, window.getEnterThresholdOverride());
            ps.setBoolean(5, window.isActive());
            if (window.getCreatedBy() != null) {
                ps.setInt(6, window.getCreatedBy());
            } else {
                ps.setNull(6, java.sql.Types.INTEGER);
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create loan policy window", e);
            return false;
        }
    }

    public boolean deactivateWindow(int windowId) {
        String sql = "UPDATE loan_policy_windows SET active = FALSE WHERE window_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, windowId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to deactivate loan policy window #" + windowId, e);
            return false;
        }
    }
}
