package com.psgitech.library.repository;

import com.psgitech.library.model.OtpCode;
import com.psgitech.library.model.OtpPurpose;
import com.psgitech.library.model.OtpStatus;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OtpRepository {
    private static final Logger LOGGER = Logger.getLogger(OtpRepository.class.getName());

    public OtpCode findActive(Connection conn, OtpPurpose purpose, int subjectId) throws SQLException {
        String sql = "SELECT * FROM otp_codes WHERE purpose = ? AND subject_id = ? AND status = 'ACTIVE' LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, purpose.name());
            ps.setInt(2, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public OtpCode findActive(OtpPurpose purpose, int subjectId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findActive(conn, purpose, subjectId);
        }
    }

    public void supersedeExisting(Connection conn, OtpPurpose purpose, int subjectId) throws SQLException {
        String sql = "UPDATE otp_codes SET status = 'SUPERSEDED' WHERE purpose = ? AND subject_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, purpose.name());
            ps.setInt(2, subjectId);
            ps.executeUpdate();
        }
    }

    public OtpCode createOtp(Connection conn, OtpCode otp) throws SQLException {
        String sql = "INSERT INTO otp_codes (purpose, subject_id, destination_email, code_hash, status, " +
                "attempts, max_attempts, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, otp.getPurpose().name());
            ps.setInt(2, otp.getSubjectId());
            ps.setString(3, otp.getDestinationEmail());
            ps.setString(4, otp.getCodeHash());
            ps.setString(5, OtpStatus.ACTIVE.name());
            ps.setInt(6, 0);
            ps.setInt(7, otp.getMaxAttempts() > 0 ? otp.getMaxAttempts() : 5);
            ps.setTimestamp(8, otp.getExpiresAt());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    otp.setOtpId(rs.getInt(1));
                }
            }
        }
        return otp;
    }

    /**
     * Atomically increments the attempt counter in an independent auto-commit connection
     * so that failed attempts are preserved even if the outer transaction rolls back.
     */
    public int incrementAttempts(int otpId) {
        String sql = "UPDATE otp_codes SET attempts = attempts + 1 WHERE otp_id = ?";
        String selectSql = "SELECT attempts, max_attempts FROM otp_codes WHERE otp_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(true);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, otpId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, otpId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int attempts = rs.getInt("attempts");
                        int maxAttempts = rs.getInt("max_attempts");
                        if (attempts >= maxAttempts) {
                            markExpired(conn, otpId);
                        }
                        return attempts;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to increment OTP attempts for otp_id=" + otpId, e);
        }
        return 0;
    }

    public void markConsumed(Connection conn, int otpId) throws SQLException {
        String sql = "UPDATE otp_codes SET status = 'CONSUMED', consumed_at = NOW() WHERE otp_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, otpId);
            ps.executeUpdate();
        }
    }

    public void markExpired(Connection conn, int otpId) throws SQLException {
        String sql = "UPDATE otp_codes SET status = 'EXPIRED' WHERE otp_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, otpId);
            ps.executeUpdate();
        }
    }

    public int countCodesInLastHour(String destinationEmail) throws SQLException {
        String sql = "SELECT COUNT(*) FROM otp_codes WHERE destination_email = ? AND created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, destinationEmail);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public Timestamp findMostRecentCreatedAt(OtpPurpose purpose, int subjectId) throws SQLException {
        String sql = "SELECT created_at FROM otp_codes WHERE purpose = ? AND subject_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, purpose.name());
            ps.setInt(2, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("created_at");
                }
            }
        }
        return null;
    }

    private OtpCode mapResultSet(ResultSet rs) throws SQLException {
        OtpCode code = new OtpCode();
        code.setOtpId(rs.getInt("otp_id"));
        code.setPurpose(OtpPurpose.valueOf(rs.getString("purpose")));
        code.setSubjectId(rs.getInt("subject_id"));
        code.setDestinationEmail(rs.getString("destination_email"));
        code.setCodeHash(rs.getString("code_hash"));
        code.setStatus(OtpStatus.valueOf(rs.getString("status")));
        code.setAttempts(rs.getInt("attempts"));
        code.setMaxAttempts(rs.getInt("max_attempts"));
        code.setCreatedAt(rs.getTimestamp("created_at"));
        code.setExpiresAt(rs.getTimestamp("expires_at"));
        code.setConsumedAt(rs.getTimestamp("consumed_at"));
        return code;
    }
}
