package com.psgitech.library.repository;

import com.psgitech.library.model.LibraryQuery;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueryRepository {
    private static final Logger LOGGER = Logger.getLogger(QueryRepository.class.getName());

    public List<LibraryQuery> findQueries(String status, String category, int offset, int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT lq.*, u.name AS student_name, u.email AS student_reg_no, " +
            "res.name AS resolver_name " +
            "FROM library_queries lq " +
            "LEFT JOIN users u ON lq.student_id = u.user_id " +
            "LEFT JOIN users res ON lq.resolved_by = res.user_id " +
            "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append("AND lq.status = ? ");
            params.add(status.trim().toUpperCase());
        }
        if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            sql.append("AND lq.category = ? ");
            params.add(category.trim());
        }

        sql.append("ORDER BY (lq.status = 'OPEN') DESC, lq.created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<LibraryQuery> list = new ArrayList<>();
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
            LOGGER.log(Level.SEVERE, "Failed to find library queries", e);
        }
        return list;
    }

    public LibraryQuery findById(int queryId) {
        String sql = "SELECT lq.*, u.name AS student_name, u.email AS student_reg_no, " +
                     "res.name AS resolver_name " +
                     "FROM library_queries lq " +
                     "LEFT JOIN users u ON lq.student_id = u.user_id " +
                     "LEFT JOIN users res ON lq.resolved_by = res.user_id " +
                     "WHERE lq.query_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, queryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find query #" + queryId, e);
        }
        return null;
    }

    public boolean replyQuery(int queryId, String responseText, int staffId) {
        String sql = "UPDATE library_queries SET response_text = ?, status = 'RESOLVED', " +
                     "resolved_by = ?, resolved_at = CURRENT_TIMESTAMP WHERE query_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, responseText);
            ps.setInt(2, staffId);
            ps.setInt(3, queryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to reply to query #" + queryId, e);
            return false;
        }
    }

    public int createQuery(LibraryQuery q) {
        String sql = "INSERT INTO library_queries (student_id, category, subject, message, status, created_at) " +
                     "VALUES (?, ?, ?, ?, 'OPEN', CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, q.getStudentId());
            ps.setString(2, q.getCategory());
            ps.setString(3, q.getSubject());
            ps.setString(4, q.getMessage());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create library query", e);
        }
        return -1;
    }

    private LibraryQuery mapRow(ResultSet rs) throws SQLException {
        LibraryQuery q = new LibraryQuery();
        q.setQueryId(rs.getInt("query_id"));
        q.setStudentId(rs.getInt("student_id"));
        q.setStudentName(rs.getString("student_name"));
        q.setStudentRegNo(rs.getString("student_reg_no"));
        q.setCategory(rs.getString("category"));
        q.setSubject(rs.getString("subject"));
        q.setMessage(rs.getString("message"));
        q.setStatus(rs.getString("status"));
        q.setResponseText(rs.getString("response_text"));
        int resBy = rs.getInt("resolved_by");
        if (!rs.wasNull()) {
            q.setResolvedBy(resBy);
        }
        q.setResolverName(rs.getString("resolver_name"));
        q.setResolvedAt(rs.getTimestamp("resolved_at"));
        q.setCreatedAt(rs.getTimestamp("created_at"));
        q.setUpdatedAt(rs.getTimestamp("updated_at"));
        return q;
    }
}
