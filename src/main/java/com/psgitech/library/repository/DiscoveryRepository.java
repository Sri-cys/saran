package com.psgitech.library.repository;

import com.psgitech.library.model.AvailabilityInfo;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookSearchCriteria;
import com.psgitech.library.model.DiscoveryCategory;
import com.psgitech.library.model.PagedResult;
import com.psgitech.library.model.SearchSuggestion;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for high-performance catalog discovery, multi-parameter search,
 * autocomplete suggestions, category aggregation, and batched availability queries.
 */
public class DiscoveryRepository {
    private static final Logger LOGGER = Logger.getLogger(DiscoveryRepository.class.getName());
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    /**
     * Escapes LIKE special characters ('!', '%', '_') using '!' as the explicit escape character.
     */
    public static String escapeLike(String input) {
        if (input == null) return "";
        return input.replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
    }

    /**
     * Executes parameterized smart book search and filtering.
     * Guaranteed constant SQL statements (1 count query + 1 page query + 1 batched availability query).
     */
    public PagedResult<Book> searchBooks(BookSearchCriteria criteria) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setReadOnly(true);

            // 1. Build WHERE clause and parameter list
            List<Object> countParams = new ArrayList<>();
            String whereClause = buildWhereClause(criteria, countParams);

            // 2. Count Query
            String countSql = "SELECT COUNT(*) FROM books b WHERE " + whereClause;
            int totalCount = 0;
            try (PreparedStatement psCount = conn.prepareStatement(countSql)) {
                psCount.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                bindParameters(psCount, countParams);
                try (ResultSet rs = psCount.executeQuery()) {
                    if (rs.next()) {
                        totalCount = rs.getInt(1);
                    }
                }
            }

            if (totalCount == 0 || criteria.getOffset() >= totalCount) {
                Map<String, Object> appliedFilters = extractAppliedFilters(criteria);
                return new PagedResult<>(Collections.emptyList(), criteria.getPage(), criteria.getPageSize(),
                        totalCount, appliedFilters, criteria.getSort(), criteria.isExtraTermsIgnored());
            }

            // 3. Page Items Query with Deterministic Scoring
            List<Object> queryParams = new ArrayList<>();
            String scoreSelect = buildScoreSelect(criteria, queryParams);
            String orderByClause = buildOrderByClause(criteria);

            // Add the WHERE parameters
            queryParams.addAll(countParams);

            String itemsSql = "SELECT b.*, " + scoreSelect + " FROM books b WHERE " + whereClause
                    + " ORDER BY " + orderByClause + " LIMIT ? OFFSET ?";
            queryParams.add(criteria.getPageSize());
            queryParams.add(criteria.getOffset());

            List<Book> books = new ArrayList<>();
            List<Integer> bookIds = new ArrayList<>();

            try (PreparedStatement psItems = conn.prepareStatement(itemsSql)) {
                psItems.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                bindParameters(psItems, queryParams);
                try (ResultSet rs = psItems.executeQuery()) {
                    while (rs.next()) {
                        Book book = mapBookBasic(rs);
                        books.add(book);
                        bookIds.add(book.getBookId());
                    }
                }
            }

            // 4. Batch Physical Copy Availability
            Map<Integer, AvailabilityInfo> availabilityMap = getBatchedAvailability(conn, bookIds);
            for (Book b : books) {
                AvailabilityInfo avail = availabilityMap.get(b.getBookId());
                if (avail != null) {
                    b.getInventory().setAvailableCopies(avail.getAvailableCopies());
                    b.getInventory().setTotalActiveCopies(avail.getTotalActiveCopies());
                    b.getInventory().setBorrowedCopies(avail.getBorrowedCopies());
                    b.getInventory().setAllocatedCopies(avail.getAllocatedCopies());
                    b.getInventory().setTransferPendingCopies(avail.getTransferPendingCopies());
                }
            }

            Map<String, Object> appliedFilters = extractAppliedFilters(criteria);
            return new PagedResult<>(books, criteria.getPage(), criteria.getPageSize(),
                    totalCount, appliedFilters, criteria.getSort(), criteria.isExtraTermsIgnored());

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during book discovery search", e);
            throw new RuntimeException("Database error during book discovery search.", e);
        }
    }

    /**
     * Retrieves autocomplete search suggestions.
     * Capped at 8 items; titles first (prefix before contains), then authors, then categories.
     * Deduplicated; non-archived books with active copies only.
     */
    public List<SearchSuggestion> getAutocompleteSuggestions(String queryText) {
        if (queryText == null || queryText.trim().length() < 2) {
            return Collections.emptyList();
        }

        String cleaned = queryText.replaceAll("\\s+", " ").trim();
        String escaped = escapeLike(cleaned);
        String prefix = escaped + "%";
        String contains = "%" + escaped + "%";

        List<SearchSuggestion> suggestions = new ArrayList<>();
        Map<String, SearchSuggestion> seen = new LinkedHashMap<>();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setReadOnly(true);

            // 1. Title Suggestions (Prefix first, then Contains)
            String titleSql = "SELECT b.book_id, b.title FROM books b " +
                    "WHERE b.is_archived = FALSE " +
                    "  AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status != 'ARCHIVED') " +
                    "  AND b.title LIKE ? ESCAPE '!' " +
                    "ORDER BY CASE WHEN b.title LIKE ? ESCAPE '!' THEN 1 ELSE 2 END, b.title ASC " +
                    "LIMIT 8";

            try (PreparedStatement ps = conn.prepareStatement(titleSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                ps.setString(1, contains);
                ps.setString(2, prefix);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next() && seen.size() < 8) {
                        int id = rs.getInt("book_id");
                        String title = rs.getString("title");
                        String key = "TITLE:" + title.toLowerCase();
                        if (!seen.containsKey(key)) {
                            seen.put(key, SearchSuggestion.forTitle(title, id));
                        }
                    }
                }
            }

            // 2. Author Suggestions if capacity remains
            if (seen.size() < 8) {
                String authorSql = "SELECT DISTINCT b.author FROM books b " +
                        "WHERE b.is_archived = FALSE " +
                        "  AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status != 'ARCHIVED') " +
                        "  AND b.author LIKE ? ESCAPE '!' " +
                        "ORDER BY CASE WHEN b.author LIKE ? ESCAPE '!' THEN 1 ELSE 2 END, b.author ASC " +
                        "LIMIT 8";

                try (PreparedStatement ps = conn.prepareStatement(authorSql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, contains);
                    ps.setString(2, prefix);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next() && seen.size() < 8) {
                            String author = rs.getString("author");
                            String key = "AUTHOR:" + author.toLowerCase();
                            if (!seen.containsKey(key)) {
                                seen.put(key, SearchSuggestion.forAuthor(author));
                            }
                        }
                    }
                }
            }

            // 3. Category Suggestions if capacity remains
            if (seen.size() < 8) {
                String catSql = "SELECT DISTINCT b.category FROM books b " +
                        "WHERE b.is_archived = FALSE " +
                        "  AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status != 'ARCHIVED') " +
                        "  AND b.category LIKE ? ESCAPE '!' " +
                        "ORDER BY CASE WHEN b.category LIKE ? ESCAPE '!' THEN 1 ELSE 2 END, b.category ASC " +
                        "LIMIT 8";

                try (PreparedStatement ps = conn.prepareStatement(catSql)) {
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    ps.setString(1, contains);
                    ps.setString(2, prefix);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next() && seen.size() < 8) {
                            String cat = rs.getString("category");
                            String key = "CATEGORY:" + cat.toLowerCase();
                            if (!seen.containsKey(key)) {
                                seen.put(key, SearchSuggestion.forCategory(cat));
                            }
                        }
                    }
                }
            }

            suggestions.addAll(seen.values());
            return suggestions;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving autocomplete suggestions", e);
            throw new RuntimeException("Database error retrieving autocomplete suggestions.", e);
        }
    }

    /**
     * Retrieves normalized categories with book counts and available copy counts.
     * Groups by LOWER(TRIM(category)) with the most frequent original spelling as display name (D13).
     */
    public List<DiscoveryCategory> getNormalizedCategories() {
        String sql = "SELECT " +
                "  LOWER(TRIM(b.category)) AS norm_category, " +
                "  MAX(b.category) AS display_category, " +
                "  COUNT(DISTINCT b.book_id) AS book_count, " +
                "  COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) AS available_copies " +
                "FROM books b " +
                "LEFT JOIN book_copies c ON b.book_id = c.book_id AND c.status != 'ARCHIVED' " +
                "WHERE b.is_archived = FALSE " +
                "  AND EXISTS (SELECT 1 FROM book_copies bc WHERE bc.book_id = b.book_id AND bc.status != 'ARCHIVED') " +
                "GROUP BY LOWER(TRIM(b.category)) " +
                "ORDER BY display_category ASC " +
                "LIMIT 200";

        List<DiscoveryCategory> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setReadOnly(true);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String norm = rs.getString("norm_category");
                    String display = rs.getString("display_category");
                    int books = rs.getInt("book_count");
                    int avail = rs.getInt("available_copies");
                    list.add(new DiscoveryCategory(norm, display, books, avail));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving discovery categories", e);
            throw new RuntimeException("Database error retrieving discovery categories.", e);
        }
        return list;
    }

    /**
     * Batches physical copy availability and waiting reservation counts for a list of book IDs.
     * Executes in 1 single SQL query to prevent N+1 queries.
     */
    public Map<Integer, AvailabilityInfo> getBatchedAvailability(List<Integer> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try (Connection conn = DBConnection.getConnection()) {
            conn.setReadOnly(true);
            return getBatchedAvailability(conn, bookIds);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error fetching batched availability", e);
            throw new RuntimeException("Database error fetching batched availability.", e);
        }
    }

    /**
     * Internal overload taking an existing Connection to support read-only transaction reuse (Decision D10).
     */
    public Map<Integer, AvailabilityInfo> getBatchedAvailability(Connection conn, List<Integer> bookIds) throws SQLException {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }

        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < bookIds.size(); i++) {
            inClause.append(i == 0 ? "?" : ", ?");
        }

        String sql = "SELECT " +
                "  b.book_id, " +
                "  COUNT(CASE WHEN c.status != 'ARCHIVED' THEN 1 END) AS total_active_copies, " +
                "  COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) AS available_copies, " +
                "  COUNT(CASE WHEN c.status = 'BORROWED' THEN 1 END) AS borrowed_copies, " +
                "  COUNT(CASE WHEN c.status = 'ALLOCATED' THEN 1 END) AS allocated_copies, " +
                "  COUNT(CASE WHEN c.status = 'TRANSFER_PENDING' THEN 1 END) AS transfer_pending_copies, " +
                "  (SELECT COUNT(*) FROM reservations r WHERE r.book_id = b.book_id AND r.status = 'WAITING') AS waiting_reservations " +
                "FROM books b " +
                "LEFT JOIN book_copies c ON b.book_id = c.book_id " +
                "WHERE b.book_id IN (" + inClause + ") " +
                "GROUP BY b.book_id";

        Map<Integer, AvailabilityInfo> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            for (int i = 0; i < bookIds.size(); i++) {
                ps.setInt(i + 1, bookIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("book_id");
                    int active = rs.getInt("total_active_copies");
                    int avail = rs.getInt("available_copies");
                    int borrowed = rs.getInt("borrowed_copies");
                    int allocated = rs.getInt("allocated_copies");
                    int transfer = rs.getInt("transfer_pending_copies");
                    int waiting = rs.getInt("waiting_reservations");
                    map.put(id, new AvailabilityInfo(active, avail, borrowed, allocated, transfer, waiting));
                }
            }
        }
        return map;
    }

    /**
     * Builds dynamic WHERE clause and populates parameter list.
     */
    private String buildWhereClause(BookSearchCriteria criteria, List<Object> params) {
        StringBuilder where = new StringBuilder("b.is_archived = FALSE ");

        // Guardrail: Book must have at least one non-archived physical copy
        where.append("AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status != 'ARCHIVED') ");

        // 1. Search Query (AND across all terms)
        if (criteria.hasQuery()) {
            for (String term : criteria.getSearchTerms()) {
                where.append("AND (");
                where.append("b.title LIKE ? ESCAPE '!' OR ");
                where.append("b.author LIKE ? ESCAPE '!' OR ");
                where.append("b.isbn LIKE ? ESCAPE '!' OR ");
                where.append("b.category LIKE ? ESCAPE '!' OR ");
                where.append("b.department LIKE ? ESCAPE '!' OR ");
                where.append("b.publisher LIKE ? ESCAPE '!' OR ");
                where.append("b.description LIKE ? ESCAPE '!'");
                where.append(") ");

                String escapedContains = "%" + escapeLike(term) + "%";
                params.add(escapedContains);
                params.add(escapedContains);
                params.add(escapedContains);
                params.add(escapedContains);
                params.add(escapedContains);
                params.add(escapedContains);
                params.add(escapedContains);
            }
        }

        // 2. Category Filter (Case-insensitive normalized)
        if (criteria.getCategory() != null) {
            where.append("AND LOWER(TRIM(b.category)) = LOWER(TRIM(?)) ");
            params.add(criteria.getCategory());
        }

        // 3. Author Filter
        if (criteria.getAuthor() != null) {
            where.append("AND b.author LIKE ? ESCAPE '!' ");
            params.add("%" + escapeLike(criteria.getAuthor()) + "%");
        }

        // 4. Publisher Filter
        if (criteria.getPublisher() != null) {
            where.append("AND b.publisher LIKE ? ESCAPE '!' ");
            params.add("%" + escapeLike(criteria.getPublisher()) + "%");
        }

        // 5. Department Filter
        if (criteria.getDepartment() != null) {
            where.append("AND LOWER(TRIM(b.department)) = LOWER(TRIM(?)) ");
            params.add(criteria.getDepartment());
        }

        // 6. Year range filter
        if (criteria.getYearFrom() != null) {
            where.append("AND b.publication_year >= ? ");
            params.add(criteria.getYearFrom());
        }
        if (criteria.getYearTo() != null) {
            where.append("AND b.publication_year <= ? ");
            params.add(criteria.getYearTo());
        }

        // 7. Availability Filter (ALL, AVAILABLE, UNAVAILABLE, WAITING)
        String avail = criteria.getAvailability();
        if ("AVAILABLE".equalsIgnoreCase(avail)) {
            where.append("AND EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status = 'AVAILABLE') ");
        } else if ("UNAVAILABLE".equalsIgnoreCase(avail)) {
            where.append("AND NOT EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.book_id AND c.status = 'AVAILABLE') ");
        } else if ("WAITING".equalsIgnoreCase(avail)) {
            where.append("AND EXISTS (SELECT 1 FROM reservations r WHERE r.book_id = b.book_id AND r.status = 'WAITING') ");
        }

        return where.toString();
    }

    /**
     * Builds deterministic SQL relevance score expression.
     */
    private String buildScoreSelect(BookSearchCriteria criteria, List<Object> params) {
        if (!criteria.hasQuery()) {
            return "0 AS relevance_score";
        }

        StringBuilder scoreExpr = new StringBuilder("(");

        // Term scores
        for (int i = 0; i < criteria.getSearchTerms().size(); i++) {
            if (i > 0) scoreExpr.append(" + ");
            String term = criteria.getSearchTerms().get(i);
            String escaped = escapeLike(term);

            scoreExpr.append("(");
            // Title exact
            scoreExpr.append("CASE WHEN LOWER(b.title) = LOWER(?) THEN 100 ");
            params.add(term);
            // Title starts-with
            scoreExpr.append("WHEN b.title LIKE ? ESCAPE '!' THEN 80 ");
            params.add(escaped + "%");
            // Title contains
            scoreExpr.append("WHEN b.title LIKE ? ESCAPE '!' THEN 60 ");
            params.add("%" + escaped + "%");
            // Author exact
            scoreExpr.append("WHEN LOWER(b.author) = LOWER(?) THEN 55 ");
            params.add(term);
            // Author contains
            scoreExpr.append("WHEN b.author LIKE ? ESCAPE '!' THEN 40 ");
            params.add("%" + escaped + "%");
            // Category match
            scoreExpr.append("WHEN b.category LIKE ? ESCAPE '!' THEN 35 ");
            params.add("%" + escaped + "%");
            // Department match
            scoreExpr.append("WHEN b.department LIKE ? ESCAPE '!' THEN 30 ");
            params.add("%" + escaped + "%");
            // Publisher match
            scoreExpr.append("WHEN b.publisher LIKE ? ESCAPE '!' THEN 15 ");
            params.add("%" + escaped + "%");
            // Exact ISBN
            scoreExpr.append("WHEN LOWER(b.isbn) = LOWER(?) THEN 100 ");
            params.add(term);
            scoreExpr.append("ELSE 0 END)");
        }

        // Full phrase bonus (+20) if phrase appears in title
        if (criteria.getSearchTerms().size() > 1 && !criteria.getNormalizedQuery().isEmpty()) {
            scoreExpr.append(" + (CASE WHEN b.title LIKE ? ESCAPE '!' THEN 20 ELSE 0 END)");
            params.add("%" + escapeLike(criteria.getNormalizedQuery()) + "%");
        }

        scoreExpr.append(") AS relevance_score");
        return scoreExpr.toString();
    }

    /**
     * Builds SQL ORDER BY clause with deterministic ties.
     */
    private String buildOrderByClause(BookSearchCriteria criteria) {
        String sort = criteria.getSort();
        if ("RELEVANCE".equalsIgnoreCase(sort) && criteria.hasQuery()) {
            return "relevance_score DESC, b.title ASC, b.book_id ASC";
        } else if ("RECENTLY_ADDED".equalsIgnoreCase(sort)) {
            return "b.created_at DESC, b.title ASC, b.book_id ASC";
        } else if ("YEAR_DESC".equalsIgnoreCase(sort)) {
            return "b.publication_year DESC, b.title ASC, b.book_id ASC";
        } else if ("YEAR_ASC".equalsIgnoreCase(sort)) {
            return "b.publication_year ASC, b.title ASC, b.book_id ASC";
        } else if ("TITLE_ASC".equalsIgnoreCase(sort)) {
            return "b.title ASC, b.book_id ASC";
        } else {
            // Default
            return criteria.hasQuery() ? "relevance_score DESC, b.title ASC, b.book_id ASC" : "b.created_at DESC, b.title ASC, b.book_id ASC";
        }
    }

    private void bindParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof Integer) {
                ps.setInt(i + 1, (Integer) p);
            } else if (p instanceof String) {
                ps.setString(i + 1, (String) p);
            } else {
                ps.setObject(i + 1, p);
            }
        }
    }

    private Map<String, Object> extractAppliedFilters(BookSearchCriteria criteria) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (criteria.hasQuery()) map.put("q", criteria.getRawQuery());
        if (criteria.getCategory() != null) map.put("category", criteria.getCategory());
        if (criteria.getAuthor() != null) map.put("author", criteria.getAuthor());
        if (criteria.getPublisher() != null) map.put("publisher", criteria.getPublisher());
        if (criteria.getDepartment() != null) map.put("department", criteria.getDepartment());
        if (criteria.getYearFrom() != null) map.put("yearFrom", criteria.getYearFrom());
        if (criteria.getYearTo() != null) map.put("yearTo", criteria.getYearTo());
        map.put("availability", criteria.getAvailability());
        map.put("sort", criteria.getSort());
        return map;
    }

    private Book mapBookBasic(ResultSet rs) throws SQLException {
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

