package com.psgitech.library;

import com.psgitech.library.dto.librarian.DashboardStatsDTO;
import com.psgitech.library.dto.librarian.ShelfLocationDTO;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookCopy;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.service.BookService;
import com.psgitech.library.service.LibrarianDashboardService;
import com.psgitech.library.service.ShelfLocatorService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class LibrarianDashboardAndInventoryTest {

    private LibrarianDashboardService dashboardService;
    private ShelfLocatorService shelfLocatorService;
    private BookService bookService;
    private BookRepository bookRepository;

    @Before
    public void setUp() {
        this.dashboardService = new LibrarianDashboardService();
        this.shelfLocatorService = new ShelfLocatorService();
        this.bookService = new BookService();
        this.bookRepository = new BookRepository();
    }

    @Test
    public void testDashboardStatsAndInvariantCalculation() {
        DashboardStatsDTO stats = dashboardService.getDashboardStats();
        assertNotNull("Dashboard stats DTO must not be null", stats);
        assertTrue("Total copies must be >= 0", stats.getTotalCopies() >= 0);
        assertTrue("Available copies must be >= 0", stats.getAvailableCopies() >= 0);
        assertTrue("Borrowed copies must be >= 0", stats.getBorrowedCopies() >= 0);
        assertNotNull("Last updated timestamp must be present", stats.getLastUpdated());
        assertNotNull("Book department stats must be populated", stats.getBookDepartmentStats());
        assertNotNull("Student department stats must be populated", stats.getStudentDepartmentStats());
    }

    @Test
    public void testDepartmentChartsAggregation() {
        Map<String, Object> charts30 = dashboardService.getChartsData(30);
        assertNotNull(charts30);
        assertTrue(charts30.containsKey("bookDepartmentStats"));
        assertTrue(charts30.containsKey("studentDepartmentStats"));
        assertEquals(30, charts30.get("daysWindow"));

        Map<String, Object> charts7 = dashboardService.getChartsData(7);
        assertEquals(7, charts7.get("daysWindow"));
    }

    @Test
    public void testHomeCollectionsAndCategoryCounts() {
        Map<String, Object> collections = dashboardService.getHomeCollections();
        assertNotNull(collections);
        assertTrue("Must have recentlyAdded list", collections.containsKey("recentlyAdded"));
        assertTrue("Must have mostBorrowed list", collections.containsKey("mostBorrowed"));
        assertTrue("Must have highDemand list", collections.containsKey("highDemand"));
        assertTrue("Must have categories list", collections.containsKey("categories"));
    }

    @Test
    public void testGlobalSearchGroupedResults() {
        Map<String, List<Map<String, Object>>> emptyResults = dashboardService.performGlobalSearch("a");
        assertEquals("Single char search must return empty list for safety", 0, emptyResults.get("books").size());

        Map<String, List<Map<String, Object>>> results = dashboardService.performGlobalSearch("Java");
        assertNotNull("Search results must not be null", results);
        assertTrue(results.containsKey("books"));
        assertTrue(results.containsKey("students"));
        assertTrue(results.containsKey("transactions"));
    }

    @Test
    public void testShelfLocatorUpsertAndRetrieval() {
        List<Book> existing = bookRepository.searchBooks("", "all", "", "", "AVAILABLE", false);
        if (!existing.isEmpty()) {
            Book testBook = existing.get(0);
            int bookId = testBook.getBookId();

            boolean updated = shelfLocatorService.updateLocation(bookId, null, "First Floor", "Block B", "Computer Science", "R-12", "S-3");
            assertTrue("Shelf location upsert must succeed", updated);

            ShelfLocationDTO loc = shelfLocatorService.getLocationForBook(bookId);
            assertNotNull("Location DTO must be retrieved", loc);
            assertEquals("First Floor", loc.getFloor());
            assertEquals("Block B", loc.getBlock());
            assertEquals("R-12", loc.getRack());
            assertEquals("S-3", loc.getShelf());
            assertTrue(loc.getFullLocationString().contains("Block B"));
        }
    }

    @Test
    public void testInventoryFiltersAndPagination() {
        List<Book> books = bookRepository.findBooksWithFilters("", "", "", "", 0, 10);
        assertNotNull("Books list must not be null", books);
        int total = bookRepository.countBooksWithFilters("", "", "", "");
        assertTrue("Total count must be >= books returned", total >= books.size());

        List<Book> cseBooks = bookRepository.findBooksWithFilters("", "", "CSE", "", 0, 10);
        for (Book b : cseBooks) {
            assertEquals("CSE", b.getDepartment());
        }
    }
}
