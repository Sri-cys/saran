package com.psgitech.library.service;

import com.psgitech.library.dto.librarian.ShelfLocationDTO;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.Book;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.Rfc4180Parser;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CsvImportService {
    private static final Logger LOGGER = Logger.getLogger(CsvImportService.class.getName());
    public static final int MAX_ROWS = 500;

    private final BookRepository bookRepository;
    private final ShelfLocatorService shelfLocatorService;
    private final ActivityLogService activityLogService;

    public CsvImportService() {
        this.bookRepository = new BookRepository();
        this.shelfLocatorService = new ShelfLocatorService();
        this.activityLogService = new ActivityLogService();
    }

    public CsvImportService(BookRepository bookRepository, ShelfLocatorService shelfLocatorService, ActivityLogService activityLogService) {
        this.bookRepository = bookRepository;
        this.shelfLocatorService = shelfLocatorService;
        this.activityLogService = activityLogService;
    }

    public static class CsvRowPreview {
        public int rowNumber;
        public String title;
        public String author;
        public String isbn;
        public String publisher;
        public String edition;
        public String category;
        public int year;
        public int copies;
        public String floor;
        public String aisle;
        public String shelf;
        public String status; // VALID, WARNING_DUPLICATE_ISBN, INVALID
        public String errorMessage;
    }

    public String generateTemplate() {
        return "\uFEFFTitle,Author,ISBN,Publisher,Edition,Category,Year,Copies,Floor,Aisle,Shelf\r\n" +
               "\"Operating Systems Principles\",\"Abraham Silberschatz\",\"9781118063330\",\"Wiley\",\"9th Edition\",\"Computer Science\",2018,3,\"1\",\"Aisle-2\",\"Shelf-B\"\r\n" +
               "\"Digital Signal Processing\",\"John Proakis\",\"9780131873742\",\"Pearson\",\"4th Edition\",\"ECE\",2021,2,\"2\",\"Aisle-1\",\"Shelf-A\"\r\n";
    }

    public List<CsvRowPreview> previewCsv(String csvContent) throws IOException {
        List<List<String>> rawRows = Rfc4180Parser.parse(csvContent);
        if (rawRows.isEmpty()) {
            throw new ValidationException("CSV file is empty.");
        }

        // Header row check
        int startIdx = 0;
        List<String> firstRow = rawRows.get(0);
        if (firstRow.size() > 0 && "title".equalsIgnoreCase(firstRow.get(0).trim())) {
            startIdx = 1;
        }

        int dataRowCount = rawRows.size() - startIdx;
        if (dataRowCount <= 0) {
            throw new ValidationException("CSV has no data rows.");
        }
        if (dataRowCount > MAX_ROWS) {
            throw new ValidationException("CSV exceeds maximum batch limit of " + MAX_ROWS + " rows (found " + dataRowCount + ").");
        }

        Set<String> seenIsbnsInBatch = new HashSet<>();
        List<CsvRowPreview> previews = new ArrayList<>();

        for (int i = startIdx; i < rawRows.size(); i++) {
            List<String> cols = rawRows.get(i);
            CsvRowPreview row = new CsvRowPreview();
            row.rowNumber = (i - startIdx + 1);

            row.title = getCol(cols, 0);
            row.author = getCol(cols, 1);
            row.isbn = getCol(cols, 2);
            row.publisher = getCol(cols, 3);
            row.edition = getCol(cols, 4);
            row.category = getCol(cols, 5);
            row.year = parseIntOrDefault(getCol(cols, 6), 2026);
            row.copies = parseIntOrDefault(getCol(cols, 7), 1);
            row.floor = getCol(cols, 8);
            row.aisle = getCol(cols, 9);
            row.shelf = getCol(cols, 10);

            validateRow(row, seenIsbnsInBatch);
            previews.add(row);
        }

        return previews;
    }

    private void validateRow(CsvRowPreview row, Set<String> seenIsbns) {
        if (row.title == null || row.title.trim().isEmpty()) {
            row.status = "INVALID";
            row.errorMessage = "Title is required.";
            return;
        }
        if (row.author == null || row.author.trim().isEmpty()) {
            row.status = "INVALID";
            row.errorMessage = "Author is required.";
            return;
        }
        if (row.copies < 1) {
            row.status = "INVALID";
            row.errorMessage = "Copies must be at least 1.";
            return;
        }

        if (row.isbn != null && !row.isbn.trim().isEmpty()) {
            String cleanIsbn = row.isbn.replaceAll("[^0-9X-]", "").trim();
            if (seenIsbns.contains(cleanIsbn)) {
                row.status = "WARNING_DUPLICATE_ISBN";
                row.errorMessage = "Duplicate ISBN within this CSV batch.";
                return;
            }
            seenIsbns.add(cleanIsbn);

            Book existing = bookRepository.findByIsbn(cleanIsbn);
            if (existing != null) {
                row.status = "WARNING_DUPLICATE_ISBN";
                row.errorMessage = "ISBN already exists in catalog (Book #" + existing.getBookId() + ": " + existing.getTitle() + ").";
                return;
            }
        }

        row.status = "VALID";
        row.errorMessage = "";
    }

    public Map<String, Object> commitBatch(List<CsvRowPreview> rows, boolean skipDuplicates, int staffId) {
        if (rows == null || rows.isEmpty()) {
            throw new ValidationException("No rows provided for import.");
        }

        int importedCount = 0;
        int skippedCount = 0;
        List<Integer> createdBookIds = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (CsvRowPreview row : rows) {
                    if ("INVALID".equalsIgnoreCase(row.status)) {
                        conn.rollback();
                        throw new ValidationException("Cannot commit batch containing invalid row #" + row.rowNumber + ": " + row.errorMessage);
                    }

                    if ("WARNING_DUPLICATE_ISBN".equalsIgnoreCase(row.status)) {
                        if (skipDuplicates) {
                            skippedCount++;
                            continue;
                        } else {
                            conn.rollback();
                            throw new ValidationException("Batch import aborted due to duplicate ISBN on row #" + row.rowNumber + ": " + row.isbn);
                        }
                    }

                    // Create Book
                    Book book = new Book();
                    book.setTitle(row.title);
                    book.setAuthor(row.author);
                    String safeIsbn = (row.isbn != null && (row.isbn.length() == 10 || row.isbn.length() == 13)) ?
                            row.isbn : String.format("978%010d", Math.abs((System.currentTimeMillis() + row.rowNumber) % 10000000000L));
                    book.setIsbn(safeIsbn);
                    book.setPublisher(row.publisher != null && !row.publisher.isEmpty() ? row.publisher : "PSG iTech Press");
                    book.setEdition(row.edition != null && !row.edition.isEmpty() ? row.edition : "1st Edition");
                    book.setCategory(row.category != null && !row.category.isEmpty() ? row.category : "General");
                    book.setDepartment(row.category != null && !row.category.isEmpty() ? row.category : "General");
                    book.setPublicationYear(row.year > 0 ? row.year : 2026);
                    book.setReplacementPrice(new BigDecimal("500.00"));

                    Book created = bookRepository.createBookWithCopies(book, row.copies);
                    createdBookIds.add(created.getBookId());

                    // Shelf location
                    if (row.floor != null && !row.floor.trim().isEmpty()) {
                        shelfLocatorService.updateLocation(
                                created.getBookId(), null, row.floor.trim(),
                                row.aisle != null ? row.aisle.trim() : "Aisle-1",
                                "A",
                                "R1",
                                row.shelf != null ? row.shelf.trim() : "Shelf-1"
                        );
                    }

                    importedCount++;
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Bulk import database error", e);
            throw new RuntimeException("Database error during bulk CSV import: " + e.getMessage(), e);
        }

        activityLogService.logEvent("BULK_CSV_IMPORT", staffId, "LIBRARIAN",
                "BOOK_CATALOG", "BATCH",
                "Bulk imported " + importedCount + " books (" + skippedCount + " skipped) via CSV", null);

        Map<String, Object> result = new HashMap<>();
        result.put("importedCount", importedCount);
        result.put("skippedCount", skippedCount);
        result.put("createdBookIds", createdBookIds);
        return result;
    }

    private String getCol(List<String> cols, int idx) {
        if (cols != null && idx < cols.size()) {
            return cols.get(idx);
        }
        return "";
    }

    private int parseIntOrDefault(String val, int def) {
        if (val == null || val.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(val.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
