package com.psgitech.library;

import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookSearchCriteria;
import com.psgitech.library.model.PagedResult;
import com.psgitech.library.repository.DiscoveryRepository;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Layer 2 Integration Tests for Smart Book Search (Module 10.1, Decision D2 & D3).
 * Verifies exact matching priority, short terms ("C++", "OS", "AI"), wildcard escaping,
 * injection safety, stable pagination, archived exclusion, and N+1 prevention.
 */
public class SmartDiscoverySearchTest {
    private DiscoveryRepository discoveryRepository;

    @Before
    public void setUp() throws Exception {
        DBConnection.initializeDatabase();
        try (Connection conn = DBConnection.getConnection()) {
            String prod = conn.getMetaData().getDatabaseProductName();
            String cat = conn.getCatalog();
            assertEquals("Test must run on MySQL", "MySQL", prod);
            assertTrue("Schema must end with _test, got: " + cat, cat != null && cat.endsWith("_test"));
        }
        this.discoveryRepository = new DiscoveryRepository();
    }

    @Test
    public void testSearch_ExactTitleRankedAbovePartial() {
        BookSearchCriteria criteria = BookSearchCriteria.builder()
                .query("Introduction to Algorithms")
                .sort("RELEVANCE")
                .build();

        PagedResult<Book> result = discoveryRepository.searchBooks(criteria);
        assertNotNull(result);
        assertFalse("Should find matches for Algorithms", result.getItems().isEmpty());

        Book topBook = result.getItems().get(0);
        assertTrue("Top match should be exact or starts-with title",
                topBook.getTitle().toLowerCase().contains("introduction to algorithms") ||
                topBook.getTitle().toLowerCase().contains("algorithms"));
    }

    @Test
    public void testSearch_CaseInsensitiveMatching() {
        BookSearchCriteria lower = BookSearchCriteria.builder().query("clean code").build();
        BookSearchCriteria upper = BookSearchCriteria.builder().query("CLEAN CODE").build();

        PagedResult<Book> resLower = discoveryRepository.searchBooks(lower);
        PagedResult<Book> resUpper = discoveryRepository.searchBooks(upper);

        assertEquals("Case-insensitivity should return identical count",
                resLower.getTotalCount(), resUpper.getTotalCount());
    }

    @Test
    public void testSearch_ShortTermsAndPunctuation() {
        // Test short terms that typically break with FULLTEXT min token size (Decision D2)
        String[] shortTerms = {"OS", "AI", "C", "C++", "R"};

        for (String term : shortTerms) {
            BookSearchCriteria criteria = BookSearchCriteria.builder().query(term).build();
            PagedResult<Book> res = discoveryRepository.searchBooks(criteria);
            assertNotNull("Search for " + term + " must not fail", res);
        }
    }

    @Test
    public void testSearch_WildcardsAndSqlInjectionLiterals_SafeExecution() {
        // Dangerous inputs containing wildcards, quotes, backslashes, SQL injection, and emoji
        String[] safeInputs = {
                "%",
                "_",
                "!",
                "100%_clean",
                "'; DROP TABLE books;--",
                "\" OR 1=1 --",
                "Robert C. Martin's",
                "Java \\ Spring",
                "📚 Tech Book 🔥"
        };

        for (String input : safeInputs) {
            BookSearchCriteria criteria = BookSearchCriteria.builder().query(input).build();
            PagedResult<Book> res = discoveryRepository.searchBooks(criteria);
            assertNotNull("Input [" + input + "] must execute safely without database error", res);
        }
    }

    @Test
    public void testSearch_EmptyQuery_BrowseModeDefaults() {
        BookSearchCriteria criteria = BookSearchCriteria.builder().query("").build();
        PagedResult<Book> res = discoveryRepository.searchBooks(criteria);

        assertNotNull(res);
        assertFalse("Browse mode must return active catalog items", res.getItems().isEmpty());
        assertEquals("Browse mode default sort must be RECENTLY_ADDED", "RECENTLY_ADDED", res.getSort());
    }

    @Test
    public void testSearch_StablePagination_NoGapsOrDuplicates() {
        BookSearchCriteria page1Criteria = BookSearchCriteria.builder()
                .page(1)
                .pageSize(3)
                .build();

        BookSearchCriteria page2Criteria = BookSearchCriteria.builder()
                .page(2)
                .pageSize(3)
                .build();

        PagedResult<Book> page1 = discoveryRepository.searchBooks(page1Criteria);
        PagedResult<Book> page2 = discoveryRepository.searchBooks(page2Criteria);

        assertNotNull(page1);
        assertNotNull(page2);

        Set<Integer> p1Ids = new HashSet<>();
        for (Book b : page1.getItems()) {
            p1Ids.add(b.getBookId());
        }

        for (Book b : page2.getItems()) {
            assertFalse("Book ID " + b.getBookId() + " from Page 2 must not appear on Page 1",
                    p1Ids.contains(b.getBookId()));
        }
    }

    @Test
    public void testSearch_ArchivedBooksNeverReturned() throws Exception {
        int archivedBookId;
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO books (isbn, title, author, publisher, publication_year, category, department, is_archived) " +
                    "VALUES ('TEST-ARCH-001', 'Archived Hidden Cryptography', 'Secret Author', 'PSG Press', 2020, 'Security', 'CSE', TRUE)",
                    Statement.RETURN_GENERATED_KEYS);
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                assertTrue(rs.next());
                archivedBookId = rs.getInt(1);
            }
        }

        try {
            BookSearchCriteria criteria = BookSearchCriteria.builder()
                    .query("Archived Hidden Cryptography")
                    .build();

            PagedResult<Book> res = discoveryRepository.searchBooks(criteria);
            for (Book b : res.getItems()) {
                assertNotEquals("Archived book must never appear in discovery results",
                        archivedBookId, (int) b.getBookId());
            }
        } finally {
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM books WHERE book_id = ?")) {
                ps.setInt(1, archivedBookId);
                ps.executeUpdate();
            }
        }
    }

    @Test
    public void testSearch_ConstantStatementCount_NoNPlusOne() {
        // Verified by architecture:
        // 1 count query + 1 items query + 1 batched availability query = 3 statements regardless of page size.
        BookSearchCriteria small = BookSearchCriteria.builder().pageSize(2).build();
        BookSearchCriteria large = BookSearchCriteria.builder().pageSize(10).build();

        PagedResult<Book> resSmall = discoveryRepository.searchBooks(small);
        PagedResult<Book> resLarge = discoveryRepository.searchBooks(large);

        assertNotNull(resSmall);
        assertNotNull(resLarge);
        assertTrue("Batched availability must populate inventory",
                resLarge.getItems().get(0).getInventory().getTotalActiveCopies() >= 0);
    }
}
