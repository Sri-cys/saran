package com.psgitech.library.repository;

import com.psgitech.library.dto.librarian.DashboardStatsDTO;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for high-performance Librarian Dashboard aggregated queries, charts, collections, and search.
 */
public class LibrarianDashboardRepository {
    private static final Logger LOGGER = Logger.getLogger(LibrarianDashboardRepository.class.getName());
    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public DashboardStatsDTO getStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();

        // 1. Single aggregated query over book_copies
        String copyStatsSql =
            "SELECT " +
            "COUNT(CASE WHEN status != 'RETIRED' THEN 1 END) AS total_copies, " +
            "COUNT(CASE WHEN status = 'AVAILABLE' THEN 1 END) AS available_copies, " +
            "COUNT(CASE WHEN status = 'BORROWED' THEN 1 END) AS borrowed_copies, " +
            "COUNT(CASE WHEN status = 'ALLOCATED' THEN 1 END) AS allocated_copies, " +
            "COUNT(CASE WHEN status = 'TRANSFER_PENDING' THEN 1 END) AS transfer_pending_copies, " +
            "COUNT(CASE WHEN status = 'LOST' THEN 1 END) AS lost_copies, " +
            "COUNT(CASE WHEN status = 'DAMAGED' THEN 1 END) AS damaged_copies, " +
            "COUNT(CASE WHEN status = 'RETIRED' THEN 1 END) AS retired_copies " +
            "FROM book_copies";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(copyStatsSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                stats.setTotalCopies(rs.getInt("total_copies"));
                stats.setAvailableCopies(rs.getInt("available_copies"));
                stats.setBorrowedCopies(rs.getInt("borrowed_copies"));
                stats.setAllocatedCopies(rs.getInt("allocated_copies"));
                stats.setTransferPendingCopies(rs.getInt("transfer_pending_copies"));
                stats.setLostCopies(rs.getInt("lost_copies"));
                stats.setDamagedCopies(rs.getInt("damaged_copies"));
                stats.setRetiredCopies(rs.getInt("retired_copies"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to aggregate copy stats", e);
        }

        // 2. Overdue loans count
        String overdueSql = "SELECT COUNT(*) FROM borrow_transactions WHERE status = 'ACTIVE' AND due_date < CURRENT_DATE()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(overdueSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                stats.setOverdueCopies(rs.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to count overdue loans", e);
        }

        // 3. Pending waiting reservations count
        String pendingResSql = "SELECT COUNT(*) FROM reservations WHERE status = 'WAITING'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(pendingResSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                stats.setPendingReservations(rs.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to count pending reservations", e);
        }

        // 4. Invariant verification: Available + Borrowed + Allocated + TransferPending + Lost + Damaged == Total
        int sum = stats.getAvailableCopies() + stats.getBorrowedCopies() + stats.getAllocatedCopies()
                + stats.getTransferPendingCopies() + stats.getLostCopies() + stats.getDamagedCopies();
        if (sum == stats.getTotalCopies()) {
            stats.setInvariantSatisfied(true);
            stats.setInconsistencyNotice(null);
        } else {
            stats.setInvariantSatisfied(false);
            String notice = "Notice: Copy count sum (" + sum + ") differs from active total (" + stats.getTotalCopies() + ").";
            stats.setInconsistencyNotice(notice);
            LOGGER.log(Level.WARNING, "Inventory invariant check failed: " + notice);
        }

        stats.setLastUpdated(ZonedDateTime.now(KOLKATA_ZONE).format(TIME_FMT));
        return stats;
    }

    public List<Map<String, Object>> getBorrowingByBookDepartment(int daysWindow) {
        String sql =
            "SELECT COALESCE(b.department, 'General') AS department, COUNT(t.transaction_id) AS borrow_count " +
            "FROM borrow_transactions t " +
            "JOIN books b ON t.book_id = b.book_id " +
            (daysWindow > 0 ? "WHERE t.issue_date >= DATE_SUB(CURRENT_DATE(), INTERVAL ? DAY) " : "") +
            "GROUP BY COALESCE(b.department, 'General') " +
            "ORDER BY borrow_count DESC LIMIT 10";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (daysWindow > 0) ps.setInt(1, daysWindow);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("department", rs.getString("department"));
                    map.put("borrowCount", rs.getInt("borrow_count"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query borrowing by book department", e);
        }
        return list;
    }

    public List<Map<String, Object>> getBorrowingByStudentDepartment(int daysWindow) {
        String sql =
            "SELECT COALESCE(u.department, 'Other') AS department, " +
            "COUNT(DISTINCT t.student_id) AS unique_students, " +
            "COUNT(t.transaction_id) AS total_books_taken " +
            "FROM borrow_transactions t " +
            "JOIN users u ON t.student_id = u.user_id " +
            (daysWindow > 0 ? "WHERE t.issue_date >= DATE_SUB(CURRENT_DATE(), INTERVAL ? DAY) " : "") +
            "GROUP BY COALESCE(u.department, 'Other') " +
            "ORDER BY total_books_taken DESC LIMIT 10";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (daysWindow > 0) ps.setInt(1, daysWindow);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("department", rs.getString("department"));
                    map.put("uniqueStudents", rs.getInt("unique_students"));
                    map.put("totalBooksTaken", rs.getInt("total_books_taken"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query borrowing by student department", e);
        }
        return list;
    }

    public List<Map<String, Object>> getCategoryCounts() {
        String sql =
            "SELECT b.category, COUNT(b.book_id) AS book_count, " +
            "COUNT(c.copy_id) AS copy_count " +
            "FROM books b " +
            "LEFT JOIN book_copies c ON b.book_id = c.book_id AND c.status != 'RETIRED' " +
            "WHERE b.is_archived = FALSE " +
            "GROUP BY b.category " +
            "ORDER BY book_count DESC";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("category", rs.getString("category"));
                map.put("bookCount", rs.getInt("book_count"));
                map.put("copyCount", rs.getInt("copy_count"));
                list.add(map);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query category counts", e);
        }
        return list;
    }

    public List<Map<String, Object>> getRecentlyAddedBooks(int limit) {
        String sql =
            "SELECT b.book_id, b.isbn, b.title, b.author, b.publisher, b.category, b.department, " +
            "b.publication_year, b.cover_image_url, " +
            "COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) AS available_copies, " +
            "COUNT(c.copy_id) AS total_copies " +
            "FROM books b " +
            "LEFT JOIN book_copies c ON b.book_id = c.book_id AND c.status != 'RETIRED' " +
            "WHERE b.is_archived = FALSE " +
            "GROUP BY b.book_id, b.isbn, b.title, b.author, b.publisher, b.category, b.department, b.publication_year, b.cover_image_url " +
            "ORDER BY b.created_at DESC, b.book_id DESC LIMIT ?";

        return queryBookCards(sql, limit);
    }

    public List<Map<String, Object>> getMostBorrowedBooks(int limit) {
        String sql =
            "SELECT b.book_id, b.isbn, b.title, b.author, b.publisher, b.category, b.department, " +
            "b.publication_year, b.cover_image_url, " +
            "COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) AS available_copies, " +
            "COUNT(c.copy_id) AS total_copies, " +
            "COUNT(t.transaction_id) AS borrow_count " +
            "FROM books b " +
            "LEFT JOIN book_copies c ON b.book_id = c.book_id AND c.status != 'RETIRED' " +
            "LEFT JOIN borrow_transactions t ON b.book_id = t.book_id " +
            "WHERE b.is_archived = FALSE " +
            "GROUP BY b.book_id, b.isbn, b.title, b.author, b.publisher, b.category, b.department, b.publication_year, b.cover_image_url " +
            "ORDER BY borrow_count DESC, b.book_id DESC LIMIT ?";

        return queryBookCards(sql, limit);
    }

    public List<Map<String, Object>> getHighDemandBooks(int threshold, int limit) {
        String sql =
            "SELECT b.book_id, b.isbn, b.title, b.author, b.publisher, b.category, b.department, " +
            "b.publication_year, b.cover_image_url, " +
            "COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) AS available_copies, " +
            "COUNT(c.copy_id) AS total_copies, " +
            "COUNT(r.reservation_id) AS waiting_count " +
            "FROM books b " +
            "LEFT JOIN book_copies c ON b.book_id = c.book_id AND c.status != 'RETIRED' " +
            "LEFT JOIN reservations r ON b.book_id = r.book_id AND r.status = 'WAITING' " +
            "WHERE b.is_archived = FALSE " +
            "GROUP BY b.book_id, b.isbn, b.title, b.author, b.publisher, b.category, b.department, b.publication_year, b.cover_image_url " +
            "HAVING waiting_count >= ? " +
            "ORDER BY waiting_count DESC, b.book_id DESC LIMIT ?";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapBookCard(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query high-demand books", e);
        }
        return list;
    }

    public int getHighDemandThreshold() {
        String sql = "SELECT enter_threshold FROM loan_policy_settings WHERE id = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to read loan policy enter_threshold, defaulting to 4", e);
        }
        return 4;
    }

    public Map<String, List<Map<String, Object>>> globalSearch(String query) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        String q = "%" + (query != null ? query.trim() : "") + "%";

        // Group 1: Books & Copies (max 5)
        String bookSql =
            "SELECT b.book_id, b.title, b.author, b.isbn, c.copy_code, c.status AS copy_status " +
            "FROM books b " +
            "LEFT JOIN book_copies c ON b.book_id = c.book_id " +
            "WHERE b.title LIKE ? OR b.author LIKE ? OR b.isbn LIKE ? OR c.copy_code LIKE ? " +
            "LIMIT 5";
        List<Map<String, Object>> books = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(bookSql)) {
            ps.setString(1, q); ps.setString(2, q); ps.setString(3, q); ps.setString(4, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("bookId", rs.getInt("book_id"));
                    m.put("title", rs.getString("title"));
                    m.put("author", rs.getString("author"));
                    m.put("isbn", rs.getString("isbn"));
                    m.put("copyCode", rs.getString("copy_code"));
                    m.put("copyStatus", rs.getString("copy_status"));
                    books.add(m);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Global search book query error", e);
        }
        grouped.put("books", books);

        // Group 2: Students (max 5)
        String studentSql =
            "SELECT user_id, name, email, register_number, department, active " +
            "FROM users WHERE role = 'STUDENT' AND (name LIKE ? OR register_number LIKE ? OR email LIKE ?) " +
            "LIMIT 5";
        List<Map<String, Object>> students = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(studentSql)) {
            ps.setString(1, q); ps.setString(2, q); ps.setString(3, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", rs.getInt("user_id"));
                    m.put("name", rs.getString("name"));
                    m.put("email", rs.getString("email"));
                    m.put("registerNumber", rs.getString("register_number"));
                    m.put("department", rs.getString("department"));
                    m.put("active", rs.getBoolean("active"));
                    students.add(m);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Global search student query error", e);
        }
        grouped.put("students", students);

        // Group 3: Transactions (max 5)
        String txSql =
            "SELECT t.transaction_id, t.copy_id, t.due_date, t.status, b.title, u.name AS student_name, u.register_number " +
            "FROM borrow_transactions t " +
            "JOIN books b ON t.book_id = b.book_id " +
            "JOIN users u ON t.student_id = u.user_id " +
            "WHERE b.title LIKE ? OR u.name LIKE ? OR u.register_number LIKE ? OR CAST(t.transaction_id AS CHAR) LIKE ? " +
            "ORDER BY t.transaction_id DESC LIMIT 5";
        List<Map<String, Object>> txs = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(txSql)) {
            ps.setString(1, q); ps.setString(2, q); ps.setString(3, q); ps.setString(4, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("transactionId", rs.getInt("transaction_id"));
                    m.put("title", rs.getString("title"));
                    m.put("studentName", rs.getString("student_name"));
                    m.put("registerNumber", rs.getString("register_number"));
                    m.put("dueDate", rs.getDate("due_date") != null ? rs.getDate("due_date").toString() : "");
                    m.put("status", rs.getString("status"));
                    txs.add(m);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Global search transaction query error", e);
        }
        grouped.put("transactions", txs);

        return grouped;
    }

    private List<Map<String, Object>> queryBookCards(String sql, int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapBookCard(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Query book cards error", e);
        }
        return list;
    }

    private Map<String, Object> mapBookCard(ResultSet rs) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("bookId", rs.getInt("book_id"));
        m.put("isbn", rs.getString("isbn"));
        m.put("title", rs.getString("title"));
        m.put("author", rs.getString("author"));
        m.put("publisher", rs.getString("publisher"));
        m.put("category", rs.getString("category"));
        m.put("department", rs.getString("department"));
        m.put("publicationYear", rs.getInt("publication_year"));
        m.put("coverImageUrl", rs.getString("cover_image_url"));
        m.put("availableCopies", rs.getInt("available_copies"));
        m.put("totalCopies", rs.getInt("total_copies"));
        return m;
    }
}
