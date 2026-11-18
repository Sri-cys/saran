package com.psgitech.library.repository;

import com.psgitech.library.model.AcquisitionRequest;
import com.psgitech.library.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AcquisitionRepository {
    private static final Logger LOGGER = Logger.getLogger(AcquisitionRepository.class.getName());

    public List<AcquisitionRequest> findRequests(String status, String department, int offset, int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT ar.*, u.name AS requester_name, u.email AS requester_reg_no, " +
            "app.name AS approver_name " +
            "FROM acquisition_requests ar " +
            "LEFT JOIN users u ON ar.requester_id = u.user_id " +
            "LEFT JOIN users app ON ar.approved_by = app.user_id " +
            "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append("AND ar.status = ? ");
            params.add(status.trim().toUpperCase());
        }
        if (department != null && !department.trim().isEmpty() && !"ALL".equalsIgnoreCase(department)) {
            sql.append("AND ar.department = ? ");
            params.add(department.trim());
        }

        sql.append("ORDER BY ar.demand_count DESC, ar.created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<AcquisitionRequest> list = new ArrayList<>();
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
            LOGGER.log(Level.SEVERE, "Failed to find acquisition requests", e);
        }
        return list;
    }

    public AcquisitionRequest findById(int requestId) {
        String sql = "SELECT ar.*, u.name AS requester_name, u.email AS requester_reg_no, " +
                     "app.name AS approver_name " +
                     "FROM acquisition_requests ar " +
                     "LEFT JOIN users u ON ar.requester_id = u.user_id " +
                     "LEFT JOIN users app ON ar.approved_by = app.user_id " +
                     "WHERE ar.request_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find acquisition request #" + requestId, e);
        }
        return null;
    }

    public boolean updateStatus(int requestId, String newStatus, String remarks, Integer approvedBy) {
        String sql = "UPDATE acquisition_requests SET status = ?, remarks = ?, " +
                     "approved_by = COALESCE(?, approved_by), " +
                     "approved_at = CASE WHEN ? = 'APPROVED' THEN CURRENT_TIMESTAMP ELSE approved_at END " +
                     "WHERE request_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, remarks);
            if (approvedBy != null) {
                ps.setInt(3, approvedBy);
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setString(4, newStatus);
            ps.setInt(5, requestId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update status for acquisition request #" + requestId, e);
            return false;
        }
    }

    public int createRequest(AcquisitionRequest req) {
        String sql = "INSERT INTO acquisition_requests (title, author, isbn, publisher, edition, department, " +
                     "requester_id, status, demand_count, remarks, estimated_price, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, 'REQUESTED', 1, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, req.getTitle());
            ps.setString(2, req.getAuthor());
            ps.setString(3, req.getIsbn());
            ps.setString(4, req.getPublisher());
            ps.setString(5, req.getEdition());
            ps.setString(6, req.getDepartment());
            ps.setInt(7, req.getRequesterId());
            ps.setString(8, req.getRemarks());
            ps.setBigDecimal(9, req.getEstimatedPrice() != null ? req.getEstimatedPrice() : BigDecimal.ZERO);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create acquisition request", e);
        }
        return -1;
    }

    private AcquisitionRequest mapRow(ResultSet rs) throws SQLException {
        AcquisitionRequest req = new AcquisitionRequest();
        req.setRequestId(rs.getInt("request_id"));
        req.setTitle(rs.getString("title"));
        req.setAuthor(rs.getString("author"));
        req.setIsbn(rs.getString("isbn"));
        req.setPublisher(rs.getString("publisher"));
        req.setEdition(rs.getString("edition"));
        req.setDepartment(rs.getString("department"));
        req.setRequesterId(rs.getInt("requester_id"));
        req.setRequesterName(rs.getString("requester_name"));
        req.setRequesterRegNo(rs.getString("requester_reg_no"));
        req.setStatus(rs.getString("status"));
        req.setDemandCount(rs.getInt("demand_count"));
        req.setRemarks(rs.getString("remarks"));
        req.setEstimatedPrice(rs.getBigDecimal("estimated_price"));
        int appBy = rs.getInt("approved_by");
        if (!rs.wasNull()) {
            req.setApprovedBy(appBy);
        }
        req.setApproverName(rs.getString("approver_name"));
        req.setApprovedAt(rs.getTimestamp("approved_at"));
        req.setCreatedAt(rs.getTimestamp("created_at"));
        req.setUpdatedAt(rs.getTimestamp("updated_at"));
        return req;
    }
}
