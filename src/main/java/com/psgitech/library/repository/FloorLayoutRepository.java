package com.psgitech.library.repository;

import com.psgitech.library.model.HighDemandAlert;
import com.psgitech.library.model.LibraryLayout;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for 2D floor grid layout, shelf heatmaps, and high demand / copy shortage analytics.
 */
public class FloorLayoutRepository {
    private static final Logger LOGGER = Logger.getLogger(FloorLayoutRepository.class.getName());

    /**
     * Lists distinct floors configured in the library layout.
     */
    public List<String> findFloors() {
        List<String> floors = new ArrayList<>();
        String sql = "SELECT DISTINCT floor FROM library_layout ORDER BY floor";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                floors.add(rs.getString("floor"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error listing library floors", e);
        }
        if (floors.isEmpty()) {
            floors.add("Floor 1");
        }
        return floors;
    }

    /**
     * Retrieves the 2D layout grid cells for a given floor with real-time circulation activity and density.
     */
    public List<LibraryLayout> findLayoutWithHeatmap(String floor) {
        List<LibraryLayout> cells = new ArrayList<>();
        String sql = "SELECT ll.*, " +
                "COUNT(DISTINCT bc.copy_id) AS total_copies, " +
                "COUNT(DISTINCT CASE WHEN bt.status = 'ACTIVE' THEN bt.transaction_id END) AS active_borrowings " +
                "FROM library_layout ll " +
                "LEFT JOIN book_locations bl ON (ll.rack = bl.rack AND ll.floor = bl.floor) " +
                "LEFT JOIN book_copies bc ON (bl.book_id = bc.book_id AND bc.status != 'RETIRED') " +
                "LEFT JOIN borrow_transactions bt ON (bc.copy_id = bt.copy_id AND bt.status = 'ACTIVE') " +
                "WHERE ll.floor = ? " +
                "GROUP BY ll.layout_id, ll.id, ll.floor, ll.block, ll.section, ll.rack, " +
                "ll.grid_row, ll.grid_col, ll.row_span, ll.col_span, ll.label, ll.category_hint, ll.created_at, ll.updated_at " +
                "ORDER BY ll.grid_row ASC, ll.grid_col ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, floor != null && !floor.trim().isEmpty() ? floor.trim() : "Floor 1");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LibraryLayout cell = new LibraryLayout();
                    try {
                        cell.setId(rs.getInt("id"));
                    } catch (SQLException e) {
                        try {
                            cell.setId(rs.getInt("layout_id"));
                        } catch (SQLException ignored) {}
                    }
                    cell.setFloor(rs.getString("floor"));
                    cell.setBlock(rs.getString("block"));
                    cell.setSection(rs.getString("section"));
                    cell.setRack(rs.getString("rack"));
                    cell.setGridRow(rs.getInt("grid_row"));
                    cell.setGridCol(rs.getInt("grid_col"));
                    cell.setRowSpan(rs.getInt("row_span"));
                    cell.setColSpan(rs.getInt("col_span"));
                    cell.setLabel(rs.getString("label"));
                    cell.setCategoryHint(rs.getString("category_hint"));
                    cell.setUpdatedAt(rs.getTimestamp("updated_at"));

                    int totalCopies = rs.getInt("total_copies");
                    int activeBorrows = rs.getInt("active_borrowings");
                    cell.setTotalCopies(totalCopies);
                    cell.setActiveBorrowCount(activeBorrows);

                    double fillPct = totalCopies > 0 ? (activeBorrows * 100.0 / totalCopies) : 0.0;
                    cell.setFillPercentage(Math.round(fillPct * 10.0) / 10.0);

                    // Classify density level
                    if (activeBorrows >= 4 || fillPct >= 65.0) {
                        cell.setDensityLevel("HIGH");
                    } else if (activeBorrows >= 2 || fillPct >= 30.0) {
                        cell.setDensityLevel("MEDIUM");
                    } else {
                        cell.setDensityLevel("LOW");
                    }

                    cells.add(cell);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding layout with heatmap for floor: " + floor, e);
        }
        return cells;
    }

    /**
     * Identifies books exceeding the high demand waitlist queue threshold or copy shortage ratio.
     */
    public List<HighDemandAlert> findHighDemandAlerts(int queueThreshold, double shortageRatio) {
        List<HighDemandAlert> alerts = new ArrayList<>();
        String sql = "SELECT b.book_id, b.title, b.author, b.isbn, b.category, b.department, " +
                "COUNT(DISTINCT r.reservation_id) AS waiting_count, " +
                "COUNT(DISTINCT bc.copy_id) AS total_copies, " +
                "COUNT(DISTINCT CASE WHEN bc.status = 'AVAILABLE' THEN bc.copy_id END) AS available_copies " +
                "FROM books b " +
                "LEFT JOIN reservations r ON (b.book_id = r.book_id AND r.status = 'WAITING') " +
                "LEFT JOIN book_copies bc ON (b.book_id = bc.book_id AND bc.status != 'RETIRED') " +
                "GROUP BY b.book_id, b.title, b.author, b.isbn, b.category, b.department " +
                "HAVING (waiting_count >= ? OR " +
                "(total_copies > 0 AND (waiting_count / total_copies) >= ?) OR " +
                "(total_copies = 0 AND waiting_count > 0)) " +
                "ORDER BY waiting_count DESC, available_copies ASC LIMIT 50";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, queueThreshold > 0 ? queueThreshold : 4);
            ps.setDouble(2, shortageRatio > 0 ? shortageRatio : 2.0);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HighDemandAlert alert = new HighDemandAlert();
                    alert.setBookId(rs.getInt("book_id"));
                    alert.setTitle(rs.getString("title"));
                    alert.setAuthor(rs.getString("author"));
                    alert.setIsbn(rs.getString("isbn"));
                    alert.setCategory(rs.getString("category"));
                    alert.setDepartment(rs.getString("department"));

                    int waiting = rs.getInt("waiting_count");
                    int total = rs.getInt("total_copies");
                    int avail = rs.getInt("available_copies");
                    alert.setWaitingCount(waiting);
                    alert.setTotalCopies(total);
                    alert.setAvailableCopies(avail);

                    double ratio = total > 0 ? (double) waiting / total : (waiting > 0 ? 99.0 : 0.0);
                    alert.setShortageRatio(Math.round(ratio * 100.0) / 100.0);

                    if (avail == 0 && waiting > 0) {
                        alert.setRecommendation("Critical: Zero available copies with " + waiting + " students queued. Fast-track returns or submit urgent acquisition requisition.");
                    } else if (waiting >= queueThreshold) {
                        alert.setRecommendation("High Queue: " + waiting + " students waiting in queue. Consider acquiring " + Math.max(2, waiting / 2) + " additional copies.");
                    } else {
                        alert.setRecommendation("Shortage Alert: Demand ratio is " + alert.getShortageRatio() + "x of total circulation inventory.");
                    }

                    alerts.add(alert);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding high demand alerts", e);
        }
        return alerts;
    }
}
