package com.psgitech.library.repository;

import com.psgitech.library.model.Book;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for popularity aggregation, personalized recommendation candidates,
 * reading history extraction, and content-based similarity matching.
 */
public class RecommendationRepository {
    private static final Logger LOGGER = Logger.getLogger(RecommendationRepository.class.getName());
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    public static final int POPULARITY_MIN_DISTINCT_STUDENTS = 3;

    /**
     * Internal container for popularity calculation records.
     */
    public static class PopularityRecord {
        private final int bookId;
        private final double score;
        private final int distinctStudents;
        private final boolean isFallback;

        public PopularityRecord(int bookId, double score, int distinctStudents, boolean isFallback) {
            this.bookId = bookId;
            this.score = score;
            this.distinctStudents = distinctStudents;
            this.isFallback = isFallback;
        }

        public int getBookId() { return bookId; }
        public double getScore() { return score; }
        public int getDistinctStudents() { return distinctStudents; }
        public boolean isFallback() { return isFallback; }
    }

    /**
     * Calculates popular or trending books according to Decision D4.
     * Window: 90 days for popular; 30 days for trending.
     * Borrows: weight 5 x decay (0-30d: 1.0, 31-60d: 0.5, 61-90d: 0.25).
     * Reservations: weight 2 x decay (0-30d: 1.0, 31-60d: 0.5, 61-90d: 0.25).
     * Minimum distinct students threshold: 3.
     * Applies fallback chain if fewer than 5 qualify.
     */
    public List<PopularityRecord> getPopularityRanking(int daysWindow, int limit) {
        List<PopularityRecord> results = new ArrayList<>();
        Set<Integer> qualifiedBookIds = new HashSet<>();

        String sql = "SELECT " +
                "  b.book_id, " +
                "  COALESCE(b_calc.borrow_score, 0) + COALESCE(r_calc.res_score, 0) AS total_score, " +
                "  COALESCE(contrib.distinct_students, 0) AS distinct_students " +
                "FROM books b " +
                "LEFT JOIN (" +
                "  SELECT bt.book_id, " +
                "    SUM(5.0 * CASE " +
                "      WHEN DATEDIFF(CURRENT_DATE, bt.issue_date) <= 30 THEN 1.0 " +
                "      WHEN DATEDIFF(CURRENT_DATE, bt.issue_date) <= 60 THEN 0.5 " +
                "      ELSE 0.25 " +
                "    END) AS borrow_score " +
                "  FROM borrow_transactions bt " +
                "  WHERE bt.issue_date >= DATE_SUB(CURRENT_DATE, INTERVAL ? DAY) " +
                "    AND bt.status IN ('ACTIVE', 'RETURNED') " +
                "  GROUP BY bt.book_id " +
                ") b_calc ON b.book_id = b_calc.book_id " +
                "LEFT JOIN (" +
                "  SELECT r.book_id, " +
                "    SUM(2.0 * CASE " +
                "      WHEN DATEDIFF(CURRENT_DATE, r.reservation_date) <= 30 THEN 1.0 " +
                "      WHEN DATEDIFF(CURRENT_DATE, r.reservation_date) <= 60 THEN 0.5 " +
                "      ELSE 0.25 " +
                "    END) AS res_score " +
                "  FROM reservations r " +
                "  WHERE r.reservation_date >= DATE_SUB(CURRENT_DATE, INTERVAL ? DAY) " +
                "    AND r.status IN ('WAITING', 'ALLOCATED', 'FULFILLED') " +
                "  GROUP BY r.book_id " +
                ") r_calc ON b.book_id = r_calc.book_id " +
                "LEFT JOIN (" +
                "  SELECT book_id, COUNT(DISTINCT student_id) AS distinct_students FROM (" +
                "    SELECT book_id, student_id FROM borrow_transactions " +
                "    WHERE issue_date >= DATE_SUB(CURRENT_DATE, INTERVAL ? DAY) AND status IN ('ACTIVE', 'RETURNED') " +
                "    UNION " +
                "    SELECT book_id, student_id FROM reservations " +
                "    WHERE reservation_date >= DATE_SUB(CURRENT_DATE, INTERVAL ? DAY) AND status IN ('WAITING', 'ALLOCATED', 'FULFILLED') " +
                "  ) combined GROUP BY book_id " +
                ") contrib ON b.book_id = contrib.book_id " +
                "WHERE b.is_archived = FALSE " +
                "  AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status != 'ARCHIVED') " +
                "  AND contrib.distinct_students >= ? " +
                "  AND (COALESCE(b_calc.borrow_score, 0) + COALESCE(r_calc.res_score, 0)) > 0 " +
                "ORDER BY total_score DESC, b.title ASC, b.book_id ASC " +
                "LIMIT ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setReadOnly(true);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setInt(1, daysWindow);
            ps.setInt(2, daysWindow);
            ps.setInt(3, daysWindow);
            ps.setInt(4, daysWindow);
            ps.setInt(5, POPULARITY_MIN_DISTINCT_STUDENTS);
            ps.setInt(6, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int bookId = rs.getInt("book_id");
                    double score = rs.getDouble("total_score");
                    int students = rs.getInt("distinct_students");
                    results.add(new PopularityRecord(bookId, score, students, false));
                    qualifiedBookIds.add(bookId);
                }
            }

            // Fallback chain (D4): if fewer than 5 books qualify
            if (results.size() < 5) {
                // Fallback 1: All-time borrow counts
                int needed = limit - results.size();
                String fallbackBorrowSql = "SELECT b.book_id, COUNT(bt.transaction_id) AS total_borrows " +
                        "FROM books b " +
                        "JOIN borrow_transactions bt ON b.book_id = bt.book_id AND bt.status IN ('ACTIVE', 'RETURNED') " +
                        "WHERE b.is_archived = FALSE " +
                        "  AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status != 'ARCHIVED') ";
                if (!qualifiedBookIds.isEmpty()) {
                    fallbackBorrowSql += "AND b.book_id NOT IN (" + buildInPlaceholders(qualifiedBookIds.size()) + ") ";
                }
                fallbackBorrowSql += "GROUP BY b.book_id ORDER BY total_borrows DESC, b.title ASC, b.book_id ASC LIMIT ?";

                try (PreparedStatement psFb1 = conn.prepareStatement(fallbackBorrowSql)) {
                    psFb1.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    int pIdx = 1;
                    if (!qualifiedBookIds.isEmpty()) {
                        for (Integer id : qualifiedBookIds) {
                            psFb1.setInt(pIdx++, id);
                        }
                    }
                    psFb1.setInt(pIdx, needed);
                    try (ResultSet rsFb1 = psFb1.executeQuery()) {
                        while (rsFb1.next() && results.size() < limit) {
                            int bookId = rsFb1.getInt("book_id");
                            results.add(new PopularityRecord(bookId, 1.0, 1, true));
                            qualifiedBookIds.add(bookId);
                        }
                    }
                }
            }

            // Fallback 2: Recently added books with available copies
            if (results.size() < limit) {
                int needed = limit - results.size();
                String fallbackRecentSql = "SELECT b.book_id FROM books b " +
                        "WHERE b.is_archived = FALSE " +
                        "  AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status = 'AVAILABLE') ";
                if (!qualifiedBookIds.isEmpty()) {
                    fallbackRecentSql += "AND b.book_id NOT IN (" + buildInPlaceholders(qualifiedBookIds.size()) + ") ";
                }
                fallbackRecentSql += "ORDER BY b.created_at DESC, b.title ASC, b.book_id ASC LIMIT ?";

                try (PreparedStatement psFb2 = conn.prepareStatement(fallbackRecentSql)) {
                    psFb2.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    int pIdx = 1;
                    if (!qualifiedBookIds.isEmpty()) {
                        for (Integer id : qualifiedBookIds) {
                            psFb2.setInt(pIdx++, id);
                        }
                    }
                    psFb2.setInt(pIdx, needed);
                    try (ResultSet rsFb2 = psFb2.executeQuery()) {
                        while (rsFb2.next() && results.size() < limit) {
                            int bookId = rsFb2.getInt("book_id");
                            results.add(new PopularityRecord(bookId, 0.5, 0, true));
                            qualifiedBookIds.add(bookId);
                        }
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error computing popularity ranking", e);
            throw new RuntimeException("Database error computing popularity ranking.", e);
        }

        return results;
    }

    /**
     * Extracts student's reading history for personalized scoring (Decision D6).
     * Returns student's top categories and authors with recency-weighted scores.
     */
    public Map<String, Object> getStudentReadingProfile(int studentId) {
        Map<String, Object> profile = new HashMap<>();
        Map<String, Double> categoryWeights = new HashMap<>();
        Map<String, Double> authorWeights = new HashMap<>();
        int historyCount = 0;

        String historySql = "SELECT b.category, b.author, bt.issue_date " +
                "FROM borrow_transactions bt " +
                "JOIN books b ON bt.book_id = b.book_id " +
                "WHERE bt.student_id = ? " +
                "  AND bt.status IN ('ACTIVE', 'RETURNED') " +
                "UNION ALL " +
                "SELECT b.category, b.author, r.reservation_date AS issue_date " +
                "FROM reservations r " +
                "JOIN books b ON r.book_id = b.book_id " +
                "WHERE r.student_id = ? " +
                "  AND r.status IN ('WAITING', 'ALLOCATED')";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(historySql)) {
            conn.setReadOnly(true);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setInt(1, studentId);
            ps.setInt(2, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    historyCount++;
                    String cat = rs.getString("category");
                    String author = rs.getString("author");
                    java.sql.Date date = rs.getDate("issue_date");

                    double weight = 0.5;
                    if (date != null) {
                        long days = (System.currentTimeMillis() - date.getTime()) / (1000L * 60 * 60 * 24);
                        if (days <= 30) weight = 1.0;
                        else if (days <= 90) weight = 0.5;
                        else weight = 0.25;
                    }

                    if (cat != null && !cat.trim().isEmpty()) {
                        categoryWeights.put(cat.trim().toLowerCase(),
                                categoryWeights.getOrDefault(cat.trim().toLowerCase(), 0.0) + weight);
                    }
                    if (author != null && !author.trim().isEmpty()) {
                        authorWeights.put(author.trim().toLowerCase(),
                                authorWeights.getOrDefault(author.trim().toLowerCase(), 0.0) + weight);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error extracting reading profile for student " + studentId, e);
            throw new RuntimeException("Database error extracting reading profile.", e);
        }

        profile.put("historyCount", historyCount);
        profile.put("categoryWeights", categoryWeights);
        profile.put("authorWeights", authorWeights);
        return profile;
    }

    /**
     * Retrieves book IDs that must be EXCLUDED from student's personalized recommendations:
     * - Books currently borrowed
     * - Books currently reserved/allocated
     * - Books previously borrowed and returned
     */
    public Set<Integer> getExcludedBookIdsForStudent(int studentId) {
        Set<Integer> excluded = new HashSet<>();
        String sql = "SELECT DISTINCT book_id FROM borrow_transactions WHERE student_id = ? " +
                "UNION " +
                "SELECT DISTINCT book_id FROM reservations WHERE student_id = ? AND status IN ('WAITING', 'ALLOCATED')";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setReadOnly(true);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setInt(1, studentId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    excluded.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving excluded books for student " + studentId, e);
            throw new RuntimeException("Database error retrieving excluded books.", e);
        }
        return excluded;
    }

    /**
     * Queries non-archived candidate books in the student's top categories or authors,
     * excluding already borrowed/reserved books. Capped at 200 candidates (Decision D6).
     */
    public List<Book> getPersonalizedCandidates(Set<String> categories, Set<String> authors,
                                               Set<Integer> excludedBookIds, int cap) {
        if ((categories == null || categories.isEmpty()) && (authors == null || authors.isEmpty())) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder("SELECT b.* FROM books b WHERE b.is_archived = FALSE ");
        sql.append("AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status != 'ARCHIVED') ");

        if (excludedBookIds != null && !excludedBookIds.isEmpty()) {
            sql.append("AND b.book_id NOT IN (").append(buildInPlaceholders(excludedBookIds.size())).append(") ");
        }

        sql.append("AND (");
        boolean hasPrev = false;
        if (categories != null && !categories.isEmpty()) {
            sql.append("LOWER(TRIM(b.category)) IN (").append(buildInPlaceholders(categories.size())).append(")");
            hasPrev = true;
        }
        if (authors != null && !authors.isEmpty()) {
            if (hasPrev) sql.append(" OR ");
            sql.append("LOWER(TRIM(b.author)) IN (").append(buildInPlaceholders(authors.size())).append(")");
        }
        sql.append(") LIMIT ?");

        List<Book> candidates = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            conn.setReadOnly(true);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int pIdx = 1;

            if (excludedBookIds != null && !excludedBookIds.isEmpty()) {
                for (Integer id : excludedBookIds) {
                    ps.setInt(pIdx++, id);
                }
            }
            if (categories != null && !categories.isEmpty()) {
                for (String cat : categories) {
                    ps.setString(pIdx++, cat.toLowerCase());
                }
            }
            if (authors != null && !authors.isEmpty()) {
                for (String author : authors) {
                    ps.setString(pIdx++, author.toLowerCase());
                }
            }
            ps.setInt(pIdx, cap);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    candidates.add(mapBook(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving personalized candidate books", e);
            throw new RuntimeException("Database error retrieving personalized candidates.", e);
        }
        return candidates;
    }

    /**
     * Cold-start helper: retrieves popular or recently added books matching the student's department.
     */
    public List<Book> getDepartmentBooks(String department, Set<Integer> excludedBookIds, int limit) {
        if (department == null || department.trim().isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder("SELECT b.* FROM books b WHERE b.is_archived = FALSE ");
        sql.append("AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status != 'ARCHIVED') ");
        sql.append("AND LOWER(TRIM(b.department)) = LOWER(TRIM(?)) ");

        if (excludedBookIds != null && !excludedBookIds.isEmpty()) {
            sql.append("AND b.book_id NOT IN (").append(buildInPlaceholders(excludedBookIds.size())).append(") ");
        }
        sql.append("ORDER BY b.created_at DESC, b.title ASC, b.book_id ASC LIMIT ?");

        List<Book> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            conn.setReadOnly(true);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, department.trim());
            int pIdx = 2;
            if (excludedBookIds != null && !excludedBookIds.isEmpty()) {
                for (Integer id : excludedBookIds) {
                    ps.setInt(pIdx++, id);
                }
            }
            ps.setInt(pIdx, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapBook(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving department books for " + department, e);
            throw new RuntimeException("Database error retrieving department books.", e);
        }
        return list;
    }

    /**
     * Retrieves content-based similar books for a target book ID (Module 10.6).
     * Weights: Same category 50 pts, Same author 35 pts, Same department 15 pts.
     */
    public List<Book> getSimilarBooks(int targetBookId, int limit) {
        // First retrieve the target book
        Book target = findBookById(targetBookId);
        if (target == null || target.isArchived()) {
            return Collections.emptyList();
        }

        String sql = "SELECT b.*, (" +
                "  (CASE WHEN LOWER(TRIM(b.category)) = LOWER(TRIM(?)) THEN 50 ELSE 0 END) + " +
                "  (CASE WHEN LOWER(TRIM(b.author)) = LOWER(TRIM(?)) THEN 35 ELSE 0 END) + " +
                "  (CASE WHEN LOWER(TRIM(b.department)) = LOWER(TRIM(?)) THEN 15 ELSE 0 END)" +
                ") AS similarity_score " +
                "FROM books b " +
                "WHERE b.book_id != ? " +
                "  AND b.is_archived = FALSE " +
                "  AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status != 'ARCHIVED') " +
                "HAVING similarity_score > 0 " +
                "ORDER BY similarity_score DESC, b.title ASC, b.book_id ASC " +
                "LIMIT ?";

        List<Book> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setReadOnly(true);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, target.getCategory() != null ? target.getCategory().trim() : "");
            ps.setString(2, target.getAuthor() != null ? target.getAuthor().trim() : "");
            ps.setString(3, target.getDepartment() != null ? target.getDepartment().trim() : "");
            ps.setInt(4, targetBookId);
            ps.setInt(5, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapBook(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving similar books for ID " + targetBookId, e);
            throw new RuntimeException("Database error retrieving similar books.", e);
        }
        return list;
    }

    /**
     * Retrieves books by a collection of IDs preserving the requested order.
     */
    public List<Book> findBooksByIds(List<Integer> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyList();
        }

        String sql = "SELECT * FROM books WHERE book_id IN (" + buildInPlaceholders(bookIds.size()) + ") AND is_archived = FALSE";
        Map<Integer, Book> map = new HashMap<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setReadOnly(true);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            for (int i = 0; i < bookIds.size(); i++) {
                ps.setInt(i + 1, bookIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Book b = mapBook(rs);
                    map.put(b.getBookId(), b);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving books by IDs", e);
            throw new RuntimeException("Database error retrieving books by IDs.", e);
        }

        List<Book> ordered = new ArrayList<>();
        for (Integer id : bookIds) {
            Book b = map.get(id);
            if (b != null) {
                ordered.add(b);
            }
        }
        return ordered;
    }

    public Book findBookById(int bookId) {
        String sql = "SELECT * FROM books WHERE book_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setReadOnly(true);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapBook(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error finding book by ID " + bookId, e);
            throw new RuntimeException("Database error finding book by ID.", e);
        }
        return null;
    }

    private String buildInPlaceholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "?" : ", ?");
        }
        return sb.toString();
    }

    private Book mapBook(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setBookId(rs.getInt("book_id"));
        b.setIsbn(rs.getString("isbn"));
        b.setTitle(rs.getString("title"));
        b.setAuthor(rs.getString("author"));
        b.setPublisher(rs.getString("publisher"));
        b.setEdition(rs.getString("edition"));
        b.setPublicationYear(rs.getInt("publication_year"));
        b.setCategory(rs.getString("category"));
        b.setDepartment(rs.getString("department"));
        b.setDescription(rs.getString("description"));
        b.setCoverImageUrl(rs.getString("cover_image_url"));
        b.setArchived(rs.getBoolean("is_archived"));
        b.setCreatedAt(rs.getTimestamp("created_at"));
        b.setUpdatedAt(rs.getTimestamp("updated_at"));
        return b;
    }
}

