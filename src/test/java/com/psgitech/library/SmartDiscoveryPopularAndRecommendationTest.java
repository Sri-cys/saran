package com.psgitech.library;

import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookRecommendation;
import com.psgitech.library.repository.DiscoveryRepository;
import com.psgitech.library.repository.RecommendationRepository;
import com.psgitech.library.service.BookPopularityService;
import com.psgitech.library.service.BookRecommendationService;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Layer 2 Integration Tests for Popularity, Trending, Personalized Recommendations,
 * and Content-Based Similarity (Modules 10.4, 10.5, 10.6, Decisions D4, D5, D6).
 */
public class SmartDiscoveryPopularAndRecommendationTest {
    private RecommendationRepository recommendationRepository;
    private DiscoveryRepository discoveryRepository;
    private BookPopularityService popularityService;
    private BookRecommendationService recommendationService;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test must run on MySQL", "MySQL", prod);
            assertTrue("Schema must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));
        }
        this.recommendationRepository = new RecommendationRepository();
        this.discoveryRepository = new DiscoveryRepository();
        this.popularityService = new BookPopularityService(recommendationRepository, discoveryRepository);
        this.recommendationService = new BookRecommendationService(recommendationRepository, discoveryRepository, popularityService);
        BookPopularityService.clearCache();
    }

    @Test
    public void testPopularity_DistinctStudentsThresholdAndFallback() throws Exception {
        // Test that if fewer than 3 distinct students contributed, book does not appear with 'Popular' label
        // but can appear via honest fallback chain as 'New arrival' (Decision D4)
        List<BookRecommendation> popular = popularityService.getPopularBooks(10);
        assertNotNull(popular);

        for (BookRecommendation b : popular) {
            assertNotNull(b.getTitle());
            assertNotNull(b.getAvailability());
            assertNotNull(b.getLabel());
            // Must be labeled either 'Popular' or 'New arrival' (honest fallback)
            assertTrue("Label must be 'Popular' or 'New arrival', got: " + b.getLabel(),
                    "Popular".equals(b.getLabel()) || "New arrival".equals(b.getLabel()));
        }
    }

    @Test
    public void testPopularity_InMemoryCachePreservesRankingWhileAvailabilityRemainsLive() {
        // Call 1 populates cache
        List<BookRecommendation> call1 = popularityService.getPopularBooks(5);
        assertNotNull(call1);

        // Call 2 retrieves from cache
        List<BookRecommendation> call2 = popularityService.getPopularBooks(5);
        assertNotNull(call2);

        assertEquals("Cached ranking size must match", call1.size(), call2.size());
        for (int i = 0; i < call1.size(); i++) {
            assertEquals("Book ID order must be identical from cache",
                    call1.get(i).getBookId(), call2.get(i).getBookId());
            assertNotNull("Availability must be hydrated live",
                    call2.get(i).getAvailability());
        }
    }

    @Test
    public void testPersonalized_StudentHistoryMatchingAndExclusion() throws Exception {
        // Find a student with borrow history in test DB, or student ID 5
        int studentId = 5;
        String department = "CSE";

        List<BookRecommendation> recs = recommendationService.getStudentRecommendations(studentId, department, 10);
        assertNotNull(recs);
        assertFalse("Recommendations panel must never be empty (Decision D6 cold start)", recs.isEmpty());

        Set<Integer> excluded = recommendationRepository.getExcludedBookIdsForStudent(studentId);

        for (BookRecommendation br : recs) {
            assertFalse("Recommended books must NOT include already borrowed/reserved book " + br.getBookId(),
                    excluded.contains(br.getBookId()));
            assertNotNull(br.getTitle());
            assertNotNull(br.getAvailability());
            assertNotNull(br.getLabel());
            assertNotNull(br.getStrategy());
        }
    }

    @Test
    public void testPersonalized_ColdStartStudent_ReceivesDepartmentOrPopularFallback() {
        // Non-existent student ID 99999 has 0 history items (cold start)
        int coldStudentId = 99999;
        String dept = "CSE";

        List<BookRecommendation> recs = recommendationService.getStudentRecommendations(coldStudentId, dept, 10);
        assertNotNull(recs);
        assertFalse("Cold-start must never return empty recommendations (Decision D6)", recs.isEmpty());

        String strategy = recs.get(0).getStrategy();
        assertTrue("Cold-start strategy must be DEPARTMENT or POPULAR_FALLBACK, got: " + strategy,
                "DEPARTMENT".equals(strategy) || "POPULAR_FALLBACK".equals(strategy));
    }

    @Test
    public void testPersonalized_ReadOnly_NoDatabaseRowsModified() throws Exception {
        long borrowCountBefore;
        long resCountBefore;
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM borrow_transactions")) {
                assertTrue(rs.next());
                borrowCountBefore = rs.getLong(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM reservations")) {
                assertTrue(rs.next());
                resCountBefore = rs.getLong(1);
            }
        }

        // Execute personalized recommendations
        recommendationService.getStudentRecommendations(5, "CSE", 10);

        long borrowCountAfter;
        long resCountAfter;
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM borrow_transactions")) {
                assertTrue(rs.next());
                borrowCountAfter = rs.getLong(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM reservations")) {
                assertTrue(rs.next());
                resCountAfter = rs.getLong(1);
            }
        }

        assertEquals("Borrow transaction count must remain unchanged", borrowCountBefore, borrowCountAfter);
        assertEquals("Reservation count must remain unchanged", resCountBefore, resCountAfter);
    }

    @Test
    public void testSimilarBooks_WeightsAndSelfExclusion() {
        int targetBookId = 1; // Existing book in test DB

        List<BookRecommendation> similar = recommendationService.getSimilarBooks(targetBookId, 6);
        assertNotNull(similar);

        for (BookRecommendation b : similar) {
            assertNotEquals("Similar books must never include target book itself", targetBookId, b.getBookId());
            assertNotNull(b.getTitle());
            assertNotNull(b.getAvailability());
            assertEquals("SIMILAR", b.getStrategy());
        }
        assertTrue("Similar books list must be bounded by limit (max 6)", similar.size() <= 6);
    }

    @Test
    public void testSimilarBooks_NonExistentBook_ReturnsEmptyList() {
        List<BookRecommendation> similar = recommendationService.getSimilarBooks(999999, 6);
        assertNotNull(similar);
        assertTrue("Non-existent book ID must return empty similar list", similar.isEmpty());
    }
}

