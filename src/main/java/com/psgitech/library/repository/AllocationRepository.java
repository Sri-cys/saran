package com.psgitech.library.repository;

import com.psgitech.library.enums.AllocationStatus;
import com.psgitech.library.exception.AllocationFailedException;
import com.psgitech.library.model.AllocationRecord;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC Repository for the 'allocations' table.
 * All write methods support Connection-accepting overloads to participate in
 * external transactions (e.g. return-with-allocation, issue-collection).
 */
public class AllocationRepository {
    private static final Logger LOGGER = Logger.getLogger(AllocationRepository.class.getName());

    /**
     * Inserts an allocation record within an existing database transaction.
     */
    public AllocationRecord insert(Connection conn, AllocationRecord record) throws SQLException {
        String sql = "INSERT INTO allocations (reservation_id, student_id, book_id, copy_id, " +
                "return_transaction_id, status, allocated_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, record.getReservationId());
            ps.setInt(2, record.getStudentId());
            ps.setInt(3, record.getBookId());
            ps.setInt(4, record.getCopyId());
            if (record.getReturnTransactionId() != null) {
                ps.setInt(5, record.getReturnTransactionId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setString(6, record.getStatus().name());
            ps.setTimestamp(7, record.getAllocatedAt());
            ps.setTimestamp(8, record.getExpiresAt());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new AllocationFailedException("Failed to insert allocation record.");
            }

            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    record.setAllocationId(gk.getInt(1));
                }
            }
        }
        return record;
    }

    /**
     * Finds an allocation record by its primary key with joined metadata.
     */
    public AllocationRecord findById(Connection conn, int allocationId) throws SQLException {
        String sql = "SELECT a.*, b.title AS book_title, b.isbn AS book_isbn, b.cover_image_url, " +
                "c.copy_code, u.name AS student_name, u.register_number " +
                "FROM allocations a " +
                "JOIN books b ON a.book_id = b.book_id " +
                "JOIN book_copies c ON a.copy_id = c.copy_id " +
                "JOIN users u ON a.student_id = u.user_id " +
                "WHERE a.allocation_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, allocationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public AllocationRecord findById(int allocationId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, allocationId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding allocation #" + allocationId, e);
            throw new RuntimeException("Database error retrieving allocation: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the active ALLOCATED record for a physical copy.
     */
    public AllocationRecord findActiveByCopy(Connection conn, int copyId) throws SQLException {
        String sql = "SELECT a.*, b.title AS book_title, b.isbn AS book_isbn, b.cover_image_url, " +
                "c.copy_code, u.name AS student_name, u.register_number " +
                "FROM allocations a " +
                "JOIN books b ON a.book_id = b.book_id " +
                "JOIN book_copies c ON a.copy_id = c.copy_id " +
                "JOIN users u ON a.student_id = u.user_id " +
                "WHERE a.copy_id = ? AND a.status = 'ALLOCATED'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, copyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Locks the active ALLOCATED record for a physical copy FOR UPDATE.
     */
    public AllocationRecord lockActiveByCopy(Connection conn, int copyId) throws SQLException {
        String sql = "SELECT a.*, b.title AS book_title, b.isbn AS book_isbn, b.cover_image_url, " +
                "c.copy_code, u.name AS student_name, u.register_number " +
                "FROM allocations a " +
                "JOIN books b ON a.book_id = b.book_id " +
                "JOIN book_copies c ON a.copy_id = c.copy_id " +
                "JOIN users u ON a.student_id = u.user_id " +
                "WHERE a.copy_id = ? AND a.status = 'ALLOCATED' FOR UPDATE";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, copyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public AllocationRecord findActiveByCopy(int copyId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findActiveByCopy(conn, copyId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding active allocation for copy #" + copyId, e);
            throw new RuntimeException("Database error retrieving active copy allocation: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the active ALLOCATED record for a reservation.
     */
    public AllocationRecord findActiveByReservation(Connection conn, int reservationId) throws SQLException {
        String sql = "SELECT a.*, b.title AS book_title, b.isbn AS book_isbn, b.cover_image_url, " +
                "c.copy_code, u.name AS student_name, u.register_number " +
                "FROM allocations a " +
                "JOIN books b ON a.book_id = b.book_id " +
                "JOIN book_copies c ON a.copy_id = c.copy_id " +
                "JOIN users u ON a.student_id = u.user_id " +
                "WHERE a.reservation_id = ? AND a.status = 'ALLOCATED'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public AllocationRecord findActiveByReservation(int reservationId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findActiveByReservation(conn, reservationId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding active allocation for reservation #" + reservationId, e);
            throw new RuntimeException("Database error retrieving active reservation allocation: " + e.getMessage(), e);
        }
    }

    /**
     * Conditionally marks an allocation record as COLLECTED upon book issuance.
     * Enforces status = 'ALLOCATED' and verifies affected rows == 1.
     */
    public boolean markCollected(Connection conn, int allocationId, int borrowTxId, Timestamp collectedAt) throws SQLException {
        String sql = "UPDATE allocations SET status = 'COLLECTED', borrow_transaction_id = ?, " +
                "collected_at = ? WHERE allocation_id = ? AND status = 'ALLOCATED'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, borrowTxId);
            ps.setTimestamp(2, collectedAt != null ? collectedAt : new Timestamp(System.currentTimeMillis()));
            ps.setInt(3, allocationId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new AllocationFailedException("Allocation #" + allocationId + " could not be marked COLLECTED (status conflict).");
            }
            return true;
        }
    }

    /**
     * Conditionally marks an allocation record as CANCELLED upon release.
     * Enforces status = 'ALLOCATED' and verifies affected rows == 1.
     */
    public boolean markCancelled(Connection conn, int allocationId, int actingUserId, Timestamp cancelledAt) throws SQLException {
        String sql = "UPDATE allocations SET status = 'CANCELLED', cancelled_by = ?, " +
                "cancelled_at = ? WHERE allocation_id = ? AND status = 'ALLOCATED'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, actingUserId);
            ps.setTimestamp(2, cancelledAt != null ? cancelledAt : new Timestamp(System.currentTimeMillis()));
            ps.setInt(3, allocationId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new AllocationFailedException("Allocation #" + allocationId + " could not be marked CANCELLED (status conflict).");
            }
            return true;
        }
    }

    /**
     * Counts the number of active ALLOCATED records currently held for a student.
     */
    public int countActiveByStudent(Connection conn, int studentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM allocations WHERE student_id = ? AND status = 'ALLOCATED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public int countActiveByStudent(int studentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return countActiveByStudent(conn, studentId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting active allocations for student #" + studentId, e);
            throw new RuntimeException("Database error counting active allocations: " + e.getMessage(), e);
        }
    }

    /**
     * Finds active ALLOCATED records for a student.
     */
    public List<AllocationRecord> findAllocatedByStudent(int studentId) {
        List<AllocationRecord> list = new ArrayList<>();
        String sql = "SELECT a.*, b.title AS book_title, b.isbn AS book_isbn, b.cover_image_url, " +
                "c.copy_code, u.name AS student_name, u.register_number " +
                "FROM allocations a " +
                "JOIN books b ON a.book_id = b.book_id " +
                "JOIN book_copies c ON a.copy_id = c.copy_id " +
                "JOIN users u ON a.student_id = u.user_id " +
                "WHERE a.student_id = ? AND a.status = 'ALLOCATED' " +
                "ORDER BY a.allocated_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding allocations for student #" + studentId, e);
            throw new RuntimeException("Database error retrieving student allocations: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Finds allocations by status with optional filtering (for staff Hold Shelf view).
     */
    public List<AllocationRecord> findByStatus(AllocationStatus status, Integer bookId, int limit, int offset) {
        List<AllocationRecord> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT a.*, b.title AS book_title, b.isbn AS book_isbn, b.cover_image_url, " +
                "c.copy_code, u.name AS student_name, u.register_number " +
                "FROM allocations a " +
                "JOIN books b ON a.book_id = b.book_id " +
                "JOIN book_copies c ON a.copy_id = c.copy_id " +
                "JOIN users u ON a.student_id = u.user_id " +
                "WHERE 1=1 "
        );

        if (status != null) {
            sql.append("AND a.status = ? ");
        }
        if (bookId != null && bookId > 0) {
            sql.append("AND a.book_id = ? ");
        }
        sql.append("ORDER BY a.allocated_at DESC LIMIT ? OFFSET ?");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int paramIdx = 1;
            if (status != null) {
                ps.setString(paramIdx++, status.name());
            }
            if (bookId != null && bookId > 0) {
                ps.setInt(paramIdx++, bookId);
            }
            ps.setInt(paramIdx++, limit > 0 ? limit : 50);
            ps.setInt(paramIdx, Math.max(offset, 0));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error querying allocations by status", e);
            throw new RuntimeException("Database error listing allocations: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Counts allocations by status (for staff pagination).
     */
    public int countByStatus(AllocationStatus status, Integer bookId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM allocations WHERE 1=1 ");
        if (status != null) {
            sql.append("AND status = ? ");
        }
        if (bookId != null && bookId > 0) {
            sql.append("AND book_id = ? ");
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int paramIdx = 1;
            if (status != null) {
                ps.setString(paramIdx++, status.name());
            }
            if (bookId != null && bookId > 0) {
                ps.setInt(paramIdx, bookId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting allocations by status", e);
            throw new RuntimeException("Database error counting allocations: " + e.getMessage(), e);
        }
        return 0;
    }

    private AllocationRecord mapRow(ResultSet rs) throws SQLException {
        AllocationRecord a = new AllocationRecord();
        a.setAllocationId(rs.getInt("allocation_id"));
        a.setReservationId(rs.getInt("reservation_id"));
        a.setStudentId(rs.getInt("student_id"));
        a.setBookId(rs.getInt("book_id"));
        a.setCopyId(rs.getInt("copy_id"));

        int returnTx = rs.getInt("return_transaction_id");
        if (!rs.wasNull()) {
            a.setReturnTransactionId(returnTx);
        }
        int borrowTx = rs.getInt("borrow_transaction_id");
        if (!rs.wasNull()) {
            a.setBorrowTransactionId(borrowTx);
        }

        a.setStatusDirectlyForHydration(AllocationStatus.fromString(rs.getString("status")));
        a.setAllocatedAt(rs.getTimestamp("allocated_at"));
        a.setExpiresAt(rs.getTimestamp("expires_at"));
        a.setCollectedAt(rs.getTimestamp("collected_at"));
        a.setCancelledAt(rs.getTimestamp("cancelled_at"));

        int cancelledBy = rs.getInt("cancelled_by");
        if (!rs.wasNull()) {
            a.setCancelledBy(cancelledBy);
        }

        a.setCreatedAt(rs.getTimestamp("created_at"));
        a.setUpdatedAt(rs.getTimestamp("updated_at"));

        // Join display fields if present in result set
        try { a.setBookTitle(rs.getString("book_title")); } catch (SQLException ignored) {}
        try { a.setBookIsbn(rs.getString("book_isbn")); } catch (SQLException ignored) {}
        try { a.setCopyCode(rs.getString("copy_code")); } catch (SQLException ignored) {}
        try { a.setStudentName(rs.getString("student_name")); } catch (SQLException ignored) {}
        try { a.setRegisterNumber(rs.getString("register_number")); } catch (SQLException ignored) {}
        try { a.setBookCoverUrl(rs.getString("cover_image_url")); } catch (SQLException ignored) {}

        return a;
    }
}

