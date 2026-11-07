package com.psgitech.library;

import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.exception.BookNotFoundException;
import com.psgitech.library.exception.DuplicateBookException;
import com.psgitech.library.exception.InvalidBookOperationException;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookCopy;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.service.BookSearchService;
import com.psgitech.library.service.BookService;
import com.psgitech.library.util.DBConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit & Integration test suite for Feature 2: Book Management.
 * Verifies book creation, copy generation, ISBN uniqueness, validation,
 * search filters, and strict archival guardrails.
 */
public class BookServiceTest {
    private BookService bookService;
    private BookSearchService bookSearchService;
    private BookRepository bookRepository;
    private List<Integer> createdBookIds = new ArrayList<>();
    private List<Integer> createdCopyIds = new ArrayList<>();

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        this.bookRepository = new BookRepository();
        this.bookService = new BookService(bookRepository);
        this.bookSearchService = new BookSearchService(bookRepository);
        this.createdBookIds = new ArrayList<>();
        this.createdCopyIds = new ArrayList<>();
        cleanUpLegacyTestBook("978-0987654321");
    }

    @After
    public void tearDown() {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String product = meta.getDatabaseProductName();
            boolean isTestDb = "H2".equalsIgnoreCase(product) || (catalog != null && catalog.toLowerCase().endsWith("_test"));
            if (isTestDb) {
                for (int copyId : createdCopyIds) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM book_copies WHERE copy_id = ?")) {
                        ps.setInt(1, copyId);
                        ps.executeUpdate();
                    }
                }
                for (int bookId : createdBookIds) {
                    try (PreparedStatement psCopies = conn.prepareStatement("DELETE FROM book_copies WHERE book_id = ?")) {
                        psCopies.setInt(1, bookId);
                        psCopies.executeUpdate();
                    }
                    try (PreparedStatement psBook = conn.prepareStatement("DELETE FROM books WHERE book_id = ?")) {
                        psBook.setInt(1, bookId);
                        psBook.executeUpdate();
                    }
                }
            }
        } catch (SQLException ignored) {
        } finally {
            createdCopyIds.clear();
            createdBookIds.clear();
        }
    }

    private void cleanUpLegacyTestBook(String isbn) {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String product = meta.getDatabaseProductName();
            boolean isTestDb = "H2".equalsIgnoreCase(product) || (catalog != null && catalog.toLowerCase().endsWith("_test"));
            if (isTestDb) {
                try (PreparedStatement psCopies = conn.prepareStatement(
                        "DELETE FROM book_copies WHERE book_id IN (SELECT book_id FROM books WHERE isbn = ?)")) {
                    psCopies.setString(1, isbn);
                    psCopies.executeUpdate();
                }
                try (PreparedStatement psBook = conn.prepareStatement("DELETE FROM books WHERE isbn = ?")) {
                    psBook.setString(1, isbn);
                    psBook.executeUpdate();
                }
            }
        } catch (SQLException ignored) {
        }
    }

    @Test
    public void testAddBookWithCopies_Success() {
        String uniqueIsbn = "978-013" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L));

        Book book = new Book();
        book.setTitle("The C Programming Language");
        book.setAuthor("Brian W. Kernighan, Dennis M. Ritchie");
        book.setIsbn(uniqueIsbn);
        book.setPublisher("Prentice Hall");
        book.setEdition("2nd Edition");
        book.setPublicationYear(1988);
        book.setCategory("Programming");
        book.setDepartment("CSE");
        book.setDescription("Authoritative reference for C programming.");

        Book created = bookService.addBook(book, 3);
        assertNotNull(created.getBookId());
        createdBookIds.add(created.getBookId());
        assertEquals("The C Programming Language", created.getTitle());
        assertEquals(3, created.getInventory().getTotalActiveCopies());
        assertEquals(3, created.getInventory().getAvailableCopies());

        // Verify copies
        List<BookCopy> copies = bookRepository.findCopiesByBookId(created.getBookId());
        assertEquals(3, copies.size());
        assertEquals("PSG-BK" + created.getBookId() + "-C01", copies.get(0).getCopyCode());
        assertEquals(CopyStatus.AVAILABLE, copies.get(0).getStatus());
    }

    @Test(expected = DuplicateBookException.class)
    public void testAddBook_DuplicateIsbnThrowsException() {
        // Book 1 already exists with ISBN 978-0262033848 in seed data
        Book duplicate = new Book();
        duplicate.setTitle("Another Algorithm Book");
        duplicate.setAuthor("Duplicate Author");
        duplicate.setIsbn("978-0262033848");
        duplicate.setPublisher("MIT Press");
        duplicate.setPublicationYear(2022);
        duplicate.setCategory("Data Structures");
        duplicate.setDepartment("CSE");

        bookService.addBook(duplicate, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddBook_MissingMandatoryTitle() {
        Book invalid = new Book();
        invalid.setAuthor("Some Author");
        invalid.setIsbn("978-1234567890");
        invalid.setPublisher("Publisher");
        invalid.setPublicationYear(2020);
        invalid.setCategory("Programming");
        invalid.setDepartment("CSE");

        bookService.addBook(invalid, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddBook_FuturePublicationYear() {
        Book invalid = new Book();
        invalid.setTitle("Future Tech");
        invalid.setAuthor("Future Author");
        invalid.setIsbn("978-1234567890");
        invalid.setPublisher("Future Press");
        invalid.setPublicationYear(2099); // Invalid
        invalid.setCategory("Computer Science");
        invalid.setDepartment("CSE");

        bookService.addBook(invalid, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddBook_InvalidZeroCopies() {
        Book book = new Book();
        book.setTitle("Test Title");
        book.setAuthor("Test Author");
        book.setIsbn("978-1234567890");
        book.setPublisher("Publisher");
        book.setPublicationYear(2021);
        book.setCategory("Programming");
        book.setDepartment("CSE");

        bookService.addBook(book, 0); // Invalid initial copies
    }

    @Test
    public void testAddCopies_Success() {
        Book book = bookRepository.findById(1, false);
        assertNotNull(book);

        int initialCopies = book.getTotalActiveCopies();
        List<BookCopy> added = bookService.addCopies(1, 2);
        for (BookCopy c : added) {
            createdCopyIds.add(c.getCopyId());
        }
        assertEquals(2, added.size());

        Book reloaded = bookRepository.findById(1, false);
        assertEquals(initialCopies + 2, reloaded.getTotalActiveCopies());
    }

    @Test
    public void testArchiveIndividualAvailableCopy_Success() {
        // Add a temporary copy to test copy archival
        List<BookCopy> added = bookService.addCopies(2, 1);
        BookCopy copy = added.get(0);
        createdCopyIds.add(copy.getCopyId());

        boolean archived = bookService.archiveCopy(copy.getCopyId());
        assertTrue(archived);

        // Verify copy status is now ARCHIVED
        List<BookCopy> copies = bookRepository.findCopiesByBookId(2);
        boolean foundArchived = false;
        for (BookCopy c : copies) {
            if (c.getCopyId().equals(copy.getCopyId())) {
                assertEquals(CopyStatus.ARCHIVED, c.getStatus());
                foundArchived = true;
                break;
            }
        }
        assertTrue(foundArchived);
    }

    @Test(expected = InvalidBookOperationException.class)
    public void testArchiveCopy_AlreadyArchivedThrowsException() {
        // Archiving an already archived copy must fail
        List<BookCopy> added = bookService.addCopies(3, 1);
        BookCopy copy = added.get(0);
        createdCopyIds.add(copy.getCopyId());
        bookService.archiveCopy(copy.getCopyId());

        // Attempt second archival on same copy
        bookService.archiveCopy(copy.getCopyId());
    }

    @Test
    public void testSearchBooks_ByKeywordAndFilters() {
        List<Book> algorithms = bookSearchService.search("Algorithms", "title", null, null, null, false);
        assertFalse(algorithms.isEmpty());
        assertTrue(algorithms.get(0).getTitle().contains("Algorithms"));

        List<Book> cseBooks = bookSearchService.search(null, "all", null, "CSE", null, false);
        assertFalse(cseBooks.isEmpty());
        for (Book b : cseBooks) {
            assertEquals("CSE", b.getDepartment());
        }

        List<Book> availableOnly = bookSearchService.search(null, "all", null, null, "AVAILABLE", false);
        for (Book b : availableOnly) {
            assertTrue(b.getAvailableCopies() > 0);
        }
    }

    @Test
    public void testArchiveBook_GuardrailSuccess() {
        String testIsbn = "978-0987" + String.format("%06d", Math.abs(System.nanoTime() % 1000000L));

        // Create an isolated book with 2 copies
        Book book = new Book();
        book.setTitle("Temporary Book for Archival");
        book.setAuthor("Archive Tester");
        book.setIsbn(testIsbn);
        book.setPublisher("Test Pub");
        book.setPublicationYear(2021);
        book.setCategory("General Knowledge");
        book.setDepartment("GENERAL");

        Book created = bookService.addBook(book, 2);
        createdBookIds.add(created.getBookId());
        assertNotNull(created.getBookId());
        assertFalse(created.isArchived());

        // Archive the book (all copies are AVAILABLE)
        boolean archived = bookService.archiveBook(created.getBookId());
        assertTrue(archived);

        Book reloaded = bookRepository.findById(created.getBookId(), true);
        assertTrue(reloaded.isArchived());
        // All active copies should now be ARCHIVED
        assertEquals(0, reloaded.getTotalActiveCopies());
        assertEquals(0, reloaded.getAvailableCopies());
    }
}
