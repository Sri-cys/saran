package com.psgitech.library.repository;

import com.psgitech.library.enums.InterchangeStatus;
import com.psgitech.library.exception.InvalidInterchangeException;
import com.psgitech.library.model.BookInterchange;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository / Data Access Object for the 'book_interchanges' table.
 * All write methods support Connection-accepting overloads for strict JDBC transaction orchestration.
 */
public class BookInterchangeRepository {
    private static final Logger LOGGER = Logger.getLogger(BookInterchangeRepository.class.getName());

    private static final String BASE_JOINED_SELECT =
            "SELECT bi.*, " +
            "uf.name AS from_student_name, uf.register_number AS from_register_number, " +
            "ut.name AS to_student_name, ut.register_number AS to_register_number, " +
            "b.title AS book_title, b.author AS book_author, b.cover_image_url, " +
            "bc.copy_code " +
            "FROM book_interchanges bi " +
            "JOIN users uf ON bi.from_student_id = uf.user_id " +
            "JOIN users ut ON bi.to_student_id = ut.user_id " +
            "JOIN books b ON bi.book_id = b.book_id " +
            "JOIN book_copies bc ON bi.copy_id = bc.copy_id ";

    /**
     * Inserts a new interchange request inside a connection transaction.
     */
    public BookInterchange insert(Connection conn, BookInterchange interchange) throws SQLException {
        String sql = "INSERT INTO book_interchanges (transaction_id, book_id, copy_id, " +
                "from_student_id, to_student_id, initiated_by, status, " +
                "original_issue_date, original_due_date, request_note) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'REQUESTED', ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, interchange.getTransactionId());
            ps.setInt(2, interchange.getBookId());
            ps.setInt(3, interchange.getCopyId());
            ps.setInt(4, interchange.getFromStudentId());
            ps.setInt(5, interchange.getToStudentId());
            ps.setInt(6, interchange.getInitiatedBy());
            ps.setDate(7, Date.valueOf(interchange.getOriginalIssueDate()));
            ps.setDate(8, Date.valueOf(interchange.getOriginalDueDate()));
            ps.setString(9, interchange.getRequestNote());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Failed to insert book interchange request.");
            }

            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    interchange.setInterchangeId(gk.getInt(1));
                } else {
                    throw new SQLException("Failed to retrieve generated interchange ID.");
                }
            }
        }
        return interchange;
    }

    public BookInterchange insert(BookInterchange interchange) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, interchange);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting book interchange", e);
            throw new RuntimeException("Database error creating interchange request: " + e.getMessage(), e);
        }
    }

    /**
     * Finds an interchange by ID with full metadata.
     */
    public BookInterchange findById(Connection conn, int interchangeId) throws SQLException {
        String sql = BASE_JOINED_SELECT + "WHERE bi.interchange_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interchangeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public BookInterchange findById(int interchangeId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, interchangeId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding interchange #" + interchangeId, e);
            throw new RuntimeException("Database error retrieving interchange: " + e.getMessage(), e);
        }
    }

    /**
     * Finds active REQUESTED interchange for a borrowing transaction.
     */
    public BookInterchange findActiveByTransactionId(Connection conn, int transactionId) throws SQLException {
        String sql = BASE_JOINED_SELECT + "WHERE bi.transaction_id = ? AND bi.status = 'REQUESTED'";
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

    public BookInterchange findActiveByTransactionId(int transactionId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findActiveByTransactionId(conn, transactionId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding active interchange for Tx #" + transactionId, e);
            throw new RuntimeException("Database error finding active interchange: " + e.getMessage(), e);
        }
    }

    /**
     * Locks an interchange record FOR UPDATE at canonical level 11.
     */
    public BookInterchange lockById(Connection conn, int interchangeId) throws SQLException {
        String sql = BASE_JOINED_SELECT + "WHERE bi.interchange_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interchangeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all incoming REQUESTED requests for a recipient student.
     */
    public List<BookInterchange> findIncomingForStudent(int studentId) {
        String sql = BASE_JOINED_SELECT + "WHERE bi.to_student_id = ? AND bi.status = 'REQUESTED' ORDER BY bi.requested_at DESC";
        List<BookInterchange> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving incoming interchanges for student #" + studentId, e);
            throw new RuntimeException("Database error retrieving incoming requests.", e);
        }
        return list;
    }

    /**
     * Counts pending incoming requests for a recipient student.
     */
    public int countPendingIncomingForStudent(int studentId) {
        String sql = "SELECT COUNT(*) FROM book_interchanges WHERE to_student_id = ? AND status = 'REQUESTED'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting pending interchanges for student #" + studentId, e);
            throw new RuntimeException("Database error counting pending requests.", e);
        }
        return 0;
    }

    /**
     * Retrieves all outgoing interchange requests initiated by a student.
     */
    public List<BookInterchange> findOutgoingForStudent(int studentId) {
        String sql = BASE_JOINED_SELECT + "WHERE bi.from_student_id = ? ORDER BY bi.requested_at DESC";
        List<BookInterchange> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving outgoing interchanges for student #" + studentId, e);
            throw new RuntimeException("Database error retrieving outgoing requests.", e);
        }
        return list;
    }

    /**
     * Retrieves all historical interchange records involving a student (as sender or recipient).
     */
    public List<BookInterchange> findHistoryForStudent(int studentId) {
        String sql = BASE_JOINED_SELECT + "WHERE (bi.from_student_id = ? OR bi.to_student_id = ?) " +
                "ORDER BY bi.updated_at DESC, bi.interchange_id DESC";
        List<BookInterchange> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving interchange history for student #" + studentId, e);
            throw new RuntimeException("Database error retrieving interchange history.", e);
        }
        return list;
    }

    /**
     * Staff view: retrieves all interchanges with pagination and optional status filter.
     */
    public List<BookInterchange> findAll(int limit, int offset, String statusFilter) {
        StringBuilder sql = new StringBuilder(BASE_JOINED_SELECT);
        boolean hasFilter = statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter.trim());
        if (hasFilter) {
            sql.append("WHERE bi.status = ? ");
        }
        sql.append("ORDER BY bi.interchange_id DESC LIMIT ? OFFSET ?");

        List<BookInterchange> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (hasFilter) {
                ps.setString(idx++, statusFilter.trim().toUpperCase());
            }
            ps.setInt(idx++, Math.max(1, limit));
            ps.setInt(idx, Math.max(0, offset));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving staff interchange list", e);
            throw new RuntimeException("Database error listing interchanges.", e);
        }
        return list;
    }

    /**
     * Conditionally updates interchange to COMPLETED. Verifies affected rows == 1.
     */
    public void completeInterchange(Connection conn, int interchangeId, int acceptedBy) throws SQLException {
        String sql = "UPDATE book_interchanges SET status = 'COMPLETED', accepted_by = ?, " +
                "responded_at = CURRENT_TIMESTAMP, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                "WHERE interchange_id = ? AND status = 'REQUESTED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, acceptedBy);
            ps.setInt(2, interchangeId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new InvalidInterchangeException("Failed to complete interchange #" + interchangeId +
                        ". Record is no longer in REQUESTED status.");
            }
        }
    }

    /**
     * Conditionally updates interchange to REJECTED. Verifies affected rows == 1.
     */
    public void rejectInterchange(Connection conn, int interchangeId, int rejectedBy, String reason) throws SQLException {
        String sql = "UPDATE book_interchanges SET status = 'REJECTED', rejected_by = ?, rejection_reason = ?, " +
                "responded_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                "WHERE interchange_id = ? AND status = 'REQUESTED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rejectedBy);
            ps.setString(2, (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Rejected by recipient");
            ps.setInt(3, interchangeId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new InvalidInterchangeException("Failed to reject interchange #" + interchangeId +
                        ". Record is no longer in REQUESTED status.");
            }
        }
    }

    /**
     * Conditionally updates interchange to CANCELLED by sender. Verifies affected rows == 1.
     */
    public void cancelInterchange(Connection conn, int interchangeId, int cancelledBy, String reason) throws SQLException {
        String sql = "UPDATE book_interchanges SET status = 'CANCELLED', cancelled_by = ?, cancellation_reason = ?, " +
                "cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                "WHERE interchange_id = ? AND status = 'REQUESTED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cancelledBy);
            ps.setString(2, (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Cancelled by sender");
            ps.setInt(3, interchangeId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new InvalidInterchangeException("Failed to cancel interchange #" + interchangeId +
                        ". Record is no longer in REQUESTED status.");
            }
        }
    }

    /**
     * Automatically cancels any active REQUESTED interchange for a borrowing transaction (used on desk return).
     * Level 11 in canonical lock order.
     */
    public int cancelPendingForTransaction(Connection conn, int transactionId, int cancelledBy, String reason) throws SQLException {
        String sql = "UPDATE book_interchanges SET status = 'CANCELLED', cancelled_by = ?, cancellation_reason = ?, " +
                "cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                "WHERE transaction_id = ? AND status = 'REQUESTED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cancelledBy);
            ps.setString(2, reason);
            ps.setInt(3, transactionId);
            return ps.executeUpdate();
        }
    }

    private BookInterchange mapRow(ResultSet rs) throws SQLException {
        BookInterchange bi = new BookInterchange();
        bi.setInterchangeId(rs.getInt("interchange_id"));
        bi.setTransactionId(rs.getInt("transaction_id"));
        bi.setBookId(rs.getInt("book_id"));
        bi.setCopyId(rs.getInt("copy_id"));
        bi.setFromStudentId(rs.getInt("from_student_id"));
        bi.setToStudentId(rs.getInt("to_student_id"));
        bi.setInitiatedBy(rs.getInt("initiated_by"));

        int acceptedBy = rs.getInt("accepted_by");
        if (!rs.wasNull()) bi.setAcceptedBy(acceptedBy);

        int rejectedBy = rs.getInt("rejected_by");
        if (!rs.wasNull()) bi.setRejectedBy(rejectedBy);

        int cancelledBy = rs.getInt("cancelled_by");
        if (!rs.wasNull()) bi.setCancelledBy(cancelledBy);

        bi.setStatus(InterchangeStatus.fromString(rs.getString("status")));

        Date origIssue = rs.getDate("original_issue_date");
        if (origIssue != null) bi.setOriginalIssueDate(origIssue.toLocalDate());

        Date origDue = rs.getDate("original_due_date");
        if (origDue != null) bi.setOriginalDueDate(origDue.toLocalDate());

        bi.setRequestNote(rs.getString("request_note"));
        bi.setRejectionReason(rs.getString("rejection_reason"));
        bi.setCancellationReason(rs.getString("cancellation_reason"));

        bi.setRequestedAt(rs.getTimestamp("requested_at"));
        bi.setRespondedAt(rs.getTimestamp("responded_at"));
        bi.setCompletedAt(rs.getTimestamp("completed_at"));
        bi.setCancelledAt(rs.getTimestamp("cancelled_at"));
        bi.setCreatedAt(rs.getTimestamp("created_at"));
        bi.setUpdatedAt(rs.getTimestamp("updated_at"));

        bi.setFromStudentName(rs.getString("from_student_name"));
        bi.setFromStudentRegNo(rs.getString("from_register_number"));
        bi.setToStudentName(rs.getString("to_student_name"));
        bi.setToStudentRegNo(rs.getString("to_register_number"));
        bi.setBookTitle(rs.getString("book_title"));
        bi.setBookAuthor(rs.getString("book_author"));
        bi.setCoverImageUrl(rs.getString("cover_image_url"));
        bi.setCopyCode(rs.getString("copy_code"));

        return bi;
    }
}

