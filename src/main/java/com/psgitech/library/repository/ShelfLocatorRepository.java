package com.psgitech.library.repository;

import com.psgitech.library.dto.librarian.ShelfLocationDTO;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for book and physical copy shelf locations.
 */
public class ShelfLocatorRepository {
    private static final Logger LOGGER = Logger.getLogger(ShelfLocatorRepository.class.getName());

    public ShelfLocationDTO findByBookId(int bookId) {
        String sql = "SELECT l.location_id, l.book_id, l.copy_id, l.floor, l.block, l.section, l.rack, l.shelf, " +
                     "b.title AS book_title, b.isbn, c.copy_code " +
                     "FROM book_locations l " +
                     "JOIN books b ON l.book_id = b.book_id " +
                     "LEFT JOIN book_copies c ON l.copy_id = c.copy_id " +
                     "WHERE l.book_id = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get location for book #" + bookId, e);
        }
        return null;
    }

    public ShelfLocationDTO findByCopyId(int copyId) {
        String sql = "SELECT l.location_id, l.book_id, l.copy_id, l.floor, l.block, l.section, l.rack, l.shelf, " +
                     "b.title AS book_title, b.isbn, c.copy_code " +
                     "FROM book_locations l " +
                     "JOIN books b ON l.book_id = b.book_id " +
                     "JOIN book_copies c ON l.copy_id = c.copy_id " +
                     "WHERE l.copy_id = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, copyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get location for copy #" + copyId, e);
        }
        return null;
    }

    public boolean upsertLocation(int bookId, Integer copyId, String floor, String block,
                                  String section, String rack, String shelf) {
        // Check if location exists for book/copy
        String checkSql = (copyId != null) ?
                "SELECT location_id FROM book_locations WHERE book_id = ? AND copy_id = ?" :
                "SELECT location_id FROM book_locations WHERE book_id = ? AND copy_id IS NULL";

        try (Connection conn = DBConnection.getConnection()) {
            Integer existingId = null;
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setInt(1, bookId);
                if (copyId != null) checkPs.setInt(2, copyId);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) existingId = rs.getInt(1);
                }
            }

            if (existingId != null) {
                String updateSql = "UPDATE book_locations SET floor = ?, block = ?, section = ?, " +
                                   "rack = ?, shelf = ?, updated_at = CURRENT_TIMESTAMP WHERE location_id = ?";
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setString(1, floor);
                    updatePs.setString(2, block);
                    updatePs.setString(3, section);
                    updatePs.setString(4, rack);
                    updatePs.setString(5, shelf);
                    updatePs.setInt(6, existingId);
                    return updatePs.executeUpdate() > 0;
                }
            } else {
                String insertSql = "INSERT INTO book_locations (book_id, copy_id, floor, block, section, rack, shelf) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setInt(1, bookId);
                    if (copyId != null) insertPs.setInt(2, copyId);
                    else insertPs.setNull(2, Types.INTEGER);
                    insertPs.setString(3, floor);
                    insertPs.setString(4, block);
                    insertPs.setString(5, section);
                    insertPs.setString(6, rack);
                    insertPs.setString(7, shelf);
                    return insertPs.executeUpdate() > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to upsert location for book #" + bookId, e);
            return false;
        }
    }

    public List<ShelfLocationDTO> search(String query, int offset, int limit) {
        String sql = "SELECT l.location_id, l.book_id, l.copy_id, l.floor, l.block, l.section, l.rack, l.shelf, " +
                     "b.title AS book_title, b.isbn, c.copy_code " +
                     "FROM book_locations l " +
                     "JOIN books b ON l.book_id = b.book_id " +
                     "LEFT JOIN book_copies c ON l.copy_id = c.copy_id " +
                     "WHERE b.title LIKE ? OR b.isbn LIKE ? OR l.section LIKE ? OR l.rack LIKE ? " +
                     "ORDER BY l.location_id DESC LIMIT ? OFFSET ?";
        List<ShelfLocationDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String q = "%" + (query != null ? query.trim() : "") + "%";
            ps.setString(1, q);
            ps.setString(2, q);
            ps.setString(3, q);
            ps.setString(4, q);
            ps.setInt(5, limit);
            ps.setInt(6, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to search locations", e);
        }
        return list;
    }

    private ShelfLocationDTO mapRow(ResultSet rs) throws SQLException {
        ShelfLocationDTO dto = new ShelfLocationDTO();
        dto.setLocationId(rs.getInt("location_id"));
        dto.setBookId(rs.getInt("book_id"));
        int cId = rs.getInt("copy_id");
        if (!rs.wasNull()) dto.setCopyId(cId);
        dto.setFloor(rs.getString("floor"));
        dto.setBlock(rs.getString("block"));
        dto.setSection(rs.getString("section"));
        dto.setRack(rs.getString("rack"));
        dto.setShelf(rs.getString("shelf"));
        dto.setBookTitle(rs.getString("book_title"));
        dto.setIsbn(rs.getString("isbn"));
        dto.setCopyCode(rs.getString("copy_code"));
        return dto;
    }
}
