package com.psgitech.library.repository;

import com.psgitech.library.enums.*;
import com.psgitech.library.exception.InvalidCaseStateException;
import com.psgitech.library.model.CaseEvent;
import com.psgitech.library.model.LossDamageCase;
import com.psgitech.library.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for Loss & Damage Cases and Audit Events.
 * Guarantees PreparedStatement usage and provides Connection-accepting overloads
 * for strict transaction participation.
 */
public class LossDamageCaseRepository {
    private static final Logger LOGGER = Logger.getLogger(LossDamageCaseRepository.class.getName());

    private static final String BASE_JOINED_SELECT =
            "SELECT c.*, " +
            "u.name AS student_name, u.register_number, " +
            "b.title AS book_title, b.isbn AS book_isbn, " +
            "bc.copy_code " +
            "FROM loss_damage_cases c " +
            "JOIN users u ON c.student_id = u.user_id " +
            "JOIN books b ON c.book_id = b.book_id " +
            "JOIN book_copies bc ON c.copy_id = bc.copy_id ";

    public LossDamageCase insertCase(Connection conn, LossDamageCase c) throws SQLException {
        String sql = "INSERT INTO loss_damage_cases (" +
                "case_type, status, borrow_transaction_id, student_id, book_id, copy_id, " +
                "reported_by, reported_at, report_note, severity, copy_disposition, " +
                "assessed_by, assessed_at, price_used, age_days, age_flag, " +
                "depreciation_applied, processing_fee, severity_fraction, computed_amount, final_amount, " +
                "override_by, override_reason, resolution_choice, fine_id, replacement_deadline, " +
                "replacement_copy_id, rejection_reason, resolved_at, resolved_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getCaseType().name());
            ps.setString(2, c.getStatus().name());
            ps.setInt(3, c.getBorrowTransactionId());
            ps.setInt(4, c.getStudentId());
            ps.setInt(5, c.getBookId());
            ps.setInt(6, c.getCopyId());
            ps.setInt(7, c.getReportedBy());
            ps.setTimestamp(8, c.getReportedAt() != null ? c.getReportedAt() : new Timestamp(System.currentTimeMillis()));
            ps.setString(9, c.getReportNote());
            ps.setString(10, c.getSeverity() != null ? c.getSeverity().name() : null);
            ps.setString(11, c.getCopyDisposition() != null ? c.getCopyDisposition().name() : null);

            if (c.getAssessedBy() != null) ps.setInt(12, c.getAssessedBy()); else ps.setNull(12, Types.INTEGER);
            ps.setTimestamp(13, c.getAssessedAt());
            ps.setBigDecimal(14, c.getPriceUsed());
            if (c.getAgeDays() != null) ps.setInt(15, c.getAgeDays()); else ps.setNull(15, Types.INTEGER);
            ps.setBoolean(16, c.isAgeFlag());
            ps.setBigDecimal(17, c.getDepreciationApplied());
            ps.setBigDecimal(18, c.getProcessingFee());
            ps.setBigDecimal(19, c.getSeverityFraction());
            ps.setBigDecimal(20, c.getComputedAmount());
            ps.setBigDecimal(21, c.getFinalAmount());

            if (c.getOverrideBy() != null) ps.setInt(22, c.getOverrideBy()); else ps.setNull(22, Types.INTEGER);
            ps.setString(23, c.getOverrideReason());
            ps.setString(24, c.getResolutionChoice() != null ? c.getResolutionChoice().name() : null);
            if (c.getFineId() != null) ps.setInt(25, c.getFineId()); else ps.setNull(25, Types.INTEGER);
            ps.setDate(26, c.getReplacementDeadline());
            if (c.getReplacementCopyId() != null) ps.setInt(27, c.getReplacementCopyId()); else ps.setNull(27, Types.INTEGER);
            ps.setString(28, c.getRejectionReason());
            ps.setTimestamp(29, c.getResolvedAt());
            if (c.getResolvedBy() != null) ps.setInt(30, c.getResolvedBy()); else ps.setNull(30, Types.INTEGER);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Failed to insert loss_damage_case record.");
            }

            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    c.setCaseId(gk.getInt(1));
                }
            }
        }
        return c;
    }

    public void updateCase(Connection conn, LossDamageCase c, CaseStatus expectedCurrentStatus) throws SQLException {
        String sql = "UPDATE loss_damage_cases SET " +
                "status = ?, severity = ?, copy_disposition = ?, assessed_by = ?, assessed_at = ?, " +
                "price_used = ?, age_days = ?, age_flag = ?, depreciation_applied = ?, processing_fee = ?, " +
                "severity_fraction = ?, computed_amount = ?, final_amount = ?, override_by = ?, override_reason = ?, " +
                "resolution_choice = ?, fine_id = ?, replacement_deadline = ?, replacement_copy_id = ?, " +
                "rejection_reason = ?, resolved_at = ?, resolved_by = ?, updated_at = CURRENT_TIMESTAMP(6) " +
                "WHERE case_id = ? " +
                (expectedCurrentStatus != null ? "AND status = ?" : "");

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getStatus().name());
            ps.setString(2, c.getSeverity() != null ? c.getSeverity().name() : null);
            ps.setString(3, c.getCopyDisposition() != null ? c.getCopyDisposition().name() : null);
            if (c.getAssessedBy() != null) ps.setInt(4, c.getAssessedBy()); else ps.setNull(4, Types.INTEGER);
            ps.setTimestamp(5, c.getAssessedAt());
            ps.setBigDecimal(6, c.getPriceUsed());
            if (c.getAgeDays() != null) ps.setInt(7, c.getAgeDays()); else ps.setNull(7, Types.INTEGER);
            ps.setBoolean(8, c.isAgeFlag());
            ps.setBigDecimal(9, c.getDepreciationApplied());
            ps.setBigDecimal(10, c.getProcessingFee());
            ps.setBigDecimal(11, c.getSeverityFraction());
            ps.setBigDecimal(12, c.getComputedAmount());
            ps.setBigDecimal(13, c.getFinalAmount());
            if (c.getOverrideBy() != null) ps.setInt(14, c.getOverrideBy()); else ps.setNull(14, Types.INTEGER);
            ps.setString(15, c.getOverrideReason());
            ps.setString(16, c.getResolutionChoice() != null ? c.getResolutionChoice().name() : null);
            if (c.getFineId() != null) ps.setInt(17, c.getFineId()); else ps.setNull(17, Types.INTEGER);
            ps.setDate(18, c.getReplacementDeadline());
            if (c.getReplacementCopyId() != null) ps.setInt(19, c.getReplacementCopyId()); else ps.setNull(19, Types.INTEGER);
            ps.setString(20, c.getRejectionReason());
            ps.setTimestamp(21, c.getResolvedAt());
            if (c.getResolvedBy() != null) ps.setInt(22, c.getResolvedBy()); else ps.setNull(22, Types.INTEGER);
            ps.setInt(23, c.getCaseId());
            if (expectedCurrentStatus != null) {
                ps.setString(24, expectedCurrentStatus.name());
            }

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new InvalidCaseStateException("Case #" + c.getCaseId() + " was modified concurrently or not in status " + expectedCurrentStatus);
            }
        }
    }

    public void recordEvent(Connection conn, CaseEvent event) throws SQLException {
        String sql = "INSERT INTO loss_damage_case_events (case_id, event_type, old_status, new_status, actor_user_id, actor_role, details) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, event.getCaseId());
            ps.setString(2, event.getEventType());
            ps.setString(3, event.getOldStatus());
            ps.setString(4, event.getNewStatus());
            ps.setInt(5, event.getActorUserId());
            ps.setString(6, event.getActorRole());
            ps.setString(7, event.getDetails());
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    event.setEventId(gk.getInt(1));
                }
            }
        }
    }

    public LossDamageCase findCaseById(int caseId) {
        String sql = BASE_JOINED_SELECT + " WHERE c.case_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding case #" + caseId, e);
            throw new RuntimeException("Database error finding case #" + caseId, e);
        }
        return null;
    }

    public LossDamageCase lockCaseById(Connection conn, int caseId) throws SQLException {
        String sql = BASE_JOINED_SELECT + " WHERE c.case_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public LossDamageCase findOpenCaseByTransactionId(int transactionId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findOpenCaseByTransactionId(conn, transactionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding open case for tx #" + transactionId, e);
            throw new RuntimeException("Database error finding open case for tx #" + transactionId, e);
        }
    }

    public LossDamageCase findOpenCaseByTransactionId(Connection conn, int transactionId) throws SQLException {
        String sql = BASE_JOINED_SELECT + " WHERE c.borrow_transaction_id = ? " +
                "AND c.status IN ('REPORTED','ASSESSED','PAYMENT_PENDING','REPLACEMENT_PENDING','REPLACEMENT_SUBMITTED')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public boolean hasUnresolvedCase(int studentId, Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM loss_damage_cases WHERE student_id = ? " +
                "AND status IN ('ASSESSED','PAYMENT_PENDING','REPLACEMENT_PENDING','REPLACEMENT_SUBMITTED')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public boolean hasOpenCaseForTransaction(int transactionId, Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM loss_damage_cases WHERE borrow_transaction_id = ? " +
                "AND status IN ('REPORTED','ASSESSED','PAYMENT_PENDING','REPLACEMENT_PENDING','REPLACEMENT_SUBMITTED')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public int cancelReportedCaseForTransaction(Connection conn, int transactionId, int actorId, String reason) throws SQLException {
        String sql = "UPDATE loss_damage_cases SET status = 'CANCELLED', rejection_reason = ?, resolved_at = CURRENT_TIMESTAMP, resolved_by = ? " +
                "WHERE borrow_transaction_id = ? AND status = 'REPORTED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setInt(2, actorId);
            ps.setInt(3, transactionId);
            return ps.executeUpdate();
        }
    }

    public List<LossDamageCase> findCasesByStudentId(int studentId) {
        String sql = BASE_JOINED_SELECT + " WHERE c.student_id = ? ORDER BY c.created_at DESC";
        List<LossDamageCase> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error listing cases for student #" + studentId, e);
            throw new RuntimeException("Database error listing cases for student #" + studentId, e);
        }
        return list;
    }

    public List<LossDamageCase> findCases(String status, String type, String registerNumber,
                                          LocalDate fromDate, LocalDate toDate, int limit, int offset) {
        StringBuilder sql = new StringBuilder(BASE_JOINED_SELECT).append(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            sql.append(" AND c.status = ? ");
            params.add(status.trim().toUpperCase());
        }
        if (type != null && !type.trim().isEmpty() && !type.equalsIgnoreCase("ALL")) {
            sql.append(" AND c.case_type = ? ");
            params.add(type.trim().toUpperCase());
        }
        if (registerNumber != null && !registerNumber.trim().isEmpty()) {
            sql.append(" AND LOWER(u.register_number) LIKE LOWER(?) ");
            params.add("%" + registerNumber.trim() + "%");
        }
        if (fromDate != null) {
            sql.append(" AND DATE(c.reported_at) >= ? ");
            params.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND DATE(c.reported_at) <= ? ");
            params.add(Date.valueOf(toDate));
        }

        sql.append(" ORDER BY c.created_at DESC LIMIT ? OFFSET ? ");
        params.add(limit > 0 ? limit : 50);
        params.add(offset >= 0 ? offset : 0);

        List<LossDamageCase> list = new ArrayList<>();
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
            LOGGER.log(Level.SEVERE, "Error querying cases", e);
            throw new RuntimeException("Database error querying cases", e);
        }
        return list;
    }

    public int countCases(String status, String type, String registerNumber, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM loss_damage_cases c ")
                .append("JOIN users u ON c.student_id = u.user_id ")
                .append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            sql.append(" AND c.status = ? ");
            params.add(status.trim().toUpperCase());
        }
        if (type != null && !type.trim().isEmpty() && !type.equalsIgnoreCase("ALL")) {
            sql.append(" AND c.case_type = ? ");
            params.add(type.trim().toUpperCase());
        }
        if (registerNumber != null && !registerNumber.trim().isEmpty()) {
            sql.append(" AND LOWER(u.register_number) LIKE LOWER(?) ");
            params.add("%" + registerNumber.trim() + "%");
        }
        if (fromDate != null) {
            sql.append(" AND DATE(c.reported_at) >= ? ");
            params.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND DATE(c.reported_at) <= ? ");
            params.add(Date.valueOf(toDate));
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
            LOGGER.log(Level.SEVERE, "Error counting cases", e);
            throw new RuntimeException("Database error counting cases", e);
        }
        return 0;
    }

    public List<CaseEvent> findEventsByCaseId(int caseId) {
        String sql = "SELECT * FROM loss_damage_case_events WHERE case_id = ? ORDER BY created_at ASC";
        List<CaseEvent> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CaseEvent ev = new CaseEvent();
                    ev.setEventId(rs.getInt("event_id"));
                    ev.setCaseId(rs.getInt("case_id"));
                    ev.setEventType(rs.getString("event_type"));
                    ev.setOldStatus(rs.getString("old_status"));
                    ev.setNewStatus(rs.getString("new_status"));
                    ev.setActorUserId(rs.getInt("actor_user_id"));
                    ev.setActorRole(rs.getString("actor_role"));
                    ev.setDetails(rs.getString("details"));
                    ev.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(ev);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error listing events for case #" + caseId, e);
            throw new RuntimeException("Database error listing events for case #" + caseId, e);
        }
        return list;
    }

    private LossDamageCase mapRow(ResultSet rs) throws SQLException {
        LossDamageCase c = new LossDamageCase();
        c.setCaseId(rs.getInt("case_id"));
        c.setCaseType(CaseType.fromString(rs.getString("case_type")));
        c.setStatusDirectly(CaseStatus.fromString(rs.getString("status")));
        c.setBorrowTransactionId(rs.getInt("borrow_transaction_id"));
        c.setStudentId(rs.getInt("student_id"));
        c.setBookId(rs.getInt("book_id"));
        c.setCopyId(rs.getInt("copy_id"));
        c.setReportedBy(rs.getInt("reported_by"));
        c.setReportedAt(rs.getTimestamp("reported_at"));
        c.setReportNote(rs.getString("report_note"));
        c.setSeverity(DamageSeverity.fromString(rs.getString("severity")));
        c.setCopyDisposition(CopyDisposition.fromString(rs.getString("copy_disposition")));

        int assessedBy = rs.getInt("assessed_by");
        if (!rs.wasNull()) c.setAssessedBy(assessedBy);
        c.setAssessedAt(rs.getTimestamp("assessed_at"));
        c.setPriceUsed(rs.getBigDecimal("price_used"));

        int ageDays = rs.getInt("age_days");
        if (!rs.wasNull()) c.setAgeDays(ageDays);
        c.setAgeFlag(rs.getBoolean("age_flag"));
        c.setDepreciationApplied(rs.getBigDecimal("depreciation_applied"));
        c.setProcessingFee(rs.getBigDecimal("processing_fee"));
        c.setSeverityFraction(rs.getBigDecimal("severity_fraction"));
        c.setComputedAmount(rs.getBigDecimal("computed_amount"));
        c.setFinalAmount(rs.getBigDecimal("final_amount"));

        int overrideBy = rs.getInt("override_by");
        if (!rs.wasNull()) c.setOverrideBy(overrideBy);
        c.setOverrideReason(rs.getString("override_reason"));
        c.setResolutionChoice(ResolutionChoice.fromString(rs.getString("resolution_choice")));

        int fineId = rs.getInt("fine_id");
        if (!rs.wasNull()) c.setFineId(fineId);
        c.setReplacementDeadline(rs.getDate("replacement_deadline"));

        int repCopyId = rs.getInt("replacement_copy_id");
        if (!rs.wasNull()) c.setReplacementCopyId(repCopyId);
        c.setRejectionReason(rs.getString("rejection_reason"));
        c.setResolvedAt(rs.getTimestamp("resolved_at"));

        int resolvedBy = rs.getInt("resolved_by");
        if (!rs.wasNull()) c.setResolvedBy(resolvedBy);

        c.setCreatedAt(rs.getTimestamp("created_at"));
        c.setUpdatedAt(rs.getTimestamp("updated_at"));

        // Presentation
        c.setStudentName(rs.getString("student_name"));
        c.setRegisterNumber(rs.getString("register_number"));
        c.setBookTitle(rs.getString("book_title"));
        c.setBookIsbn(rs.getString("book_isbn"));
        c.setCopyCode(rs.getString("copy_code"));

        return c;
    }
}
