package com.psgitech.library;

import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookSearchCriteria;
import com.psgitech.library.model.DiscoveryCategory;
import com.psgitech.library.model.PagedResult;
import com.psgitech.library.repository.DiscoveryRepository;
import com.psgitech.library.service.BookService;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Layer 2 Integration Tests for Advanced Filters and Category Aggregations (Modules 10.3 & 10.7).
 * Verifies individual and combined filters, availability semantics, consistency with BookDetails,
 * and normalized category grouping.
 */
public class SmartDiscoveryFilterAndCategoryTest {
    private DiscoveryRepository discoveryRepository;
    private BookService bookService;

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
        this.bookService = new BookService();
    }

    @Test
    public void testFilter_CategoryAlone() {
        BookSearchCriteria criteria = BookSearchCriteria.builder()
                .category("Computer Science")
                .build();

        PagedResult<Book> res = discoveryRepository.searchBooks(criteria);
        assertNotNull(res);
        assertFalse("Should find Computer Science books", res.getItems().isEmpty());
        for (Book b : res.getItems()) {
            assertEquals("Computer Science", b.getCategory());
        }
    }

    @Test
    public void testFilter_PublisherAlone() {
        BookSearchCriteria criteria = BookSearchCriteria.builder()
                .publisher("MIT Press")
                .build();

        PagedResult<Book> res = discoveryRepository.searchBooks(criteria);
        assertNotNull(res);
        for (Book b : res.getItems()) {
            assertTrue(b.getPublisher().toLowerCase().contains("mit press"));
        }
    }

    @Test
    public void testFilter_YearRange() {
        BookSearchCriteria criteria = BookSearchCriteria.builder()
                .yearFrom(2000)
                .yearTo(2025)
                .build();

        PagedResult<Book> res = discoveryRepository.searchBooks(criteria);
        assertNotNull(res);
        for (Book b : res.getItems()) {
            assertTrue("Year must be >= 2000", b.getPublicationYear() >= 2000);
            assertTrue("Year must be <= 2025", b.getPublicationYear() <= 2025);
        }
    }

    @Test
    public void testFilter_CombinedAndWithQuery() {
        BookSearchCriteria criteria = BookSearchCriteria.builder()
                .query("Code")
                .category("Computer Science")
                .availability("ALL")
                .build();

        PagedResult<Book> res = discoveryRepository.searchBooks(criteria);
        assertNotNull(res);
        for (Book b : res.getItems()) {
            assertEquals("Computer Science", b.getCategory());
            assertTrue(b.getTitle().toLowerCase().contains("code") || b.getAuthor().toLowerCase().contains("code"));
        }
    }

    @Test
    public void testFilter_AvailabilitySemantics() {
        // AVAILABLE filter
        BookSearchCriteria availCriteria = BookSearchCriteria.builder()
                .availability("AVAILABLE")
                .build();
        PagedResult<Book> availRes = discoveryRepository.searchBooks(availCriteria);
        assertNotNull(availRes);
        for (Book b : availRes.getItems()) {
            assertTrue("Book with AVAILABLE filter must have availableCopies > 0",
                    b.getInventory().getAvailableCopies() > 0);
        }

        // UNAVAILABLE filter
        BookSearchCriteria unavailCriteria = BookSearchCriteria.builder()
                .availability("UNAVAILABLE")
                .build();
        PagedResult<Book> unavailRes = discoveryRepository.searchBooks(unavailCriteria);
        assertNotNull(unavailRes);
        for (Book b : unavailRes.getItems()) {
            assertEquals("Book with UNAVAILABLE filter must have availableCopies == 0",
                    0, b.getInventory().getAvailableCopies());
        }
    }

    @Test
    public void testAvailability_ConsistencyWithBookDetails() {
        // Compare availability computed by DiscoveryRepository vs existing BookService.getBookDetails
        BookSearchCriteria criteria = BookSearchCriteria.builder().pageSize(5).build();
        PagedResult<Book> paged = discoveryRepository.searchBooks(criteria);

        for (Book discoveryBook : paged.getItems()) {
            Book detailsBook = bookService.getBookDetails(discoveryBook.getBookId(), false);
            assertNotNull(detailsBook);

            assertEquals("Total active copies count must match BookDetails for book " + discoveryBook.getBookId(),
                    detailsBook.getInventory().getTotalActiveCopies(),
                    discoveryBook.getInventory().getTotalActiveCopies());

            assertEquals("Available copies count must match BookDetails for book " + discoveryBook.getBookId(),
                    detailsBook.getInventory().getAvailableCopies(),
                    discoveryBook.getInventory().getAvailableCopies());
        }
    }

    @Test
    public void testCategories_NormalizedGrouping() {
        List<DiscoveryCategory> categories = discoveryRepository.getNormalizedCategories();
        assertNotNull(categories);
        assertFalse("Categories list should not be empty", categories.isEmpty());

        for (DiscoveryCategory cat : categories) {
            assertNotNull(cat.getNormalizedName());
            assertNotNull(cat.getDisplayName());
            assertTrue("Book count must be positive", cat.getBookCount() > 0);
            assertTrue("Available copies must be >= 0", cat.getAvailableCopyCount() >= 0);
        }
    }
}

