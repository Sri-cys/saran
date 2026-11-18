package com.psgitech.library.service;

import com.psgitech.library.dto.librarian.ShelfLocationDTO;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.AcquisitionRequest;
import com.psgitech.library.model.Book;
import com.psgitech.library.repository.AcquisitionRepository;
import com.psgitech.library.repository.BookRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Logger;

public class AcquisitionService {
    private static final Logger LOGGER = Logger.getLogger(AcquisitionService.class.getName());

    private final AcquisitionRepository acquisitionRepository;
    private final BookService bookService;
    private final BookRepository bookRepository;
    private final ShelfLocatorService shelfLocatorService;
    private final ActivityLogService activityLogService;

    public AcquisitionService() {
        this.acquisitionRepository = new AcquisitionRepository();
        this.bookService = new BookService();
        this.bookRepository = new BookRepository();
        this.shelfLocatorService = new ShelfLocatorService();
        this.activityLogService = new ActivityLogService();
    }

    public AcquisitionService(AcquisitionRepository acquisitionRepository, BookService bookService,
                              BookRepository bookRepository, ShelfLocatorService shelfLocatorService,
                              ActivityLogService activityLogService) {
        this.acquisitionRepository = acquisitionRepository;
        this.bookService = bookService;
        this.bookRepository = bookRepository;
        this.shelfLocatorService = shelfLocatorService;
        this.activityLogService = activityLogService;
    }

    public List<AcquisitionRequest> getRequests(String status, String department, int page, int pageSize) {
        int clampedPage = Math.max(1, page);
        int clampedSize = Math.max(1, Math.min(100, pageSize));
        int offset = (clampedPage - 1) * clampedSize;
        return acquisitionRepository.findRequests(status, department, offset, clampedSize);
    }

    public AcquisitionRequest getById(int requestId) {
        return acquisitionRepository.findById(requestId);
    }

    public boolean updateStatus(int requestId, String newStatus, String remarks, int staffId) {
        AcquisitionRequest existing = acquisitionRepository.findById(requestId);
        if (existing == null) {
            throw new ValidationException("Acquisition request #" + requestId + " not found.");
        }

        String currentStatus = existing.getStatus();
        validateTransition(currentStatus, newStatus);

        boolean updated = acquisitionRepository.updateStatus(requestId, newStatus, remarks, staffId);
        if (updated) {
            activityLogService.logEvent("ACQUISITION_STATUS_UPDATED", staffId, "LIBRARIAN",
                    "ACQUISITION_REQUEST", String.valueOf(requestId),
                    "Status changed from " + currentStatus + " to " + newStatus + ". Remarks: " + remarks, null);
        }
        return updated;
    }

    public Book convertToInventory(int requestId, String category, int numberOfCopies,
                                  String floor, String block, String section, String rack, String shelf,
                                  int staffId) {
        AcquisitionRequest req = acquisitionRepository.findById(requestId);
        if (req == null) {
            throw new ValidationException("Acquisition request #" + requestId + " not found.");
        }

        int copiesToCreate = numberOfCopies > 0 ? numberOfCopies : Math.max(1, req.getDemandCount());
        String cat = (category != null && !category.trim().isEmpty()) ? category.trim() : (req.getDepartment() != null ? req.getDepartment() : "General");

        // Check if book already exists by ISBN
        Book book = null;
        if (req.getIsbn() != null && !req.getIsbn().trim().isEmpty()) {
            book = bookRepository.findByIsbn(req.getIsbn().trim());
        }

        if (book != null) {
            // Book exists: add copies
            bookService.addCopies(book.getBookId(), copiesToCreate);
        } else {
            // Create new book
            Book newBook = new Book();
            newBook.setTitle(req.getTitle());
            newBook.setAuthor(req.getAuthor());
            String safeIsbn = (req.getIsbn() != null && (req.getIsbn().length() == 10 || req.getIsbn().length() == 13)) ?
                    req.getIsbn() : String.format("978%010d", Math.abs(System.currentTimeMillis() % 10000000000L));
            newBook.setIsbn(safeIsbn);
            newBook.setPublisher(req.getPublisher() != null ? req.getPublisher() : "PSG iTech Publications");
            newBook.setEdition(req.getEdition() != null ? req.getEdition() : "1st Edition");
            newBook.setCategory(cat);
            newBook.setDepartment(req.getDepartment() != null ? req.getDepartment() : cat);
            newBook.setReplacementPrice(req.getEstimatedPrice() != null ? req.getEstimatedPrice() : new BigDecimal("500.00"));
            newBook.setPublicationYear(2026);

            book = bookService.addBook(newBook, copiesToCreate);
        }

        // Assign shelf location
        if (floor != null && !floor.trim().isEmpty()) {
            shelfLocatorService.updateLocation(
                    book.getBookId(), null, floor.trim(),
                    block != null ? block.trim() : "Main",
                    section != null ? section.trim() : "A",
                    rack != null ? rack.trim() : "R1",
                    shelf != null ? shelf.trim() : "S1"
            );
        }

        // Update acquisition status to RECEIVED
        acquisitionRepository.updateStatus(requestId, "RECEIVED", "Converted to catalog book #" + book.getBookId(), staffId);

        activityLogService.logEvent("ACQUISITION_CONVERTED", staffId, "LIBRARIAN",
                "BOOK", String.valueOf(book.getBookId()),
                "Converted acquisition request #" + requestId + " to Book #" + book.getBookId() + " with " + copiesToCreate + " copies", null);

        return book;
    }

    private void validateTransition(String current, String next) {
        if (current.equalsIgnoreCase(next)) {
            return;
        }
        if ("REQUESTED".equalsIgnoreCase(current)) {
            if (!"APPROVED".equalsIgnoreCase(next) && !"REJECTED".equalsIgnoreCase(next)) {
                throw new ValidationException("REQUESTED requests can only transition to APPROVED or REJECTED.");
            }
        } else if ("APPROVED".equalsIgnoreCase(current)) {
            if (!"ORDERED".equalsIgnoreCase(next) && !"REJECTED".equalsIgnoreCase(next) && !"RECEIVED".equalsIgnoreCase(next)) {
                throw new ValidationException("APPROVED requests can only transition to ORDERED, RECEIVED, or REJECTED.");
            }
        } else if ("ORDERED".equalsIgnoreCase(current)) {
            if (!"RECEIVED".equalsIgnoreCase(next) && !"REJECTED".equalsIgnoreCase(next)) {
                throw new ValidationException("ORDERED requests can only transition to RECEIVED or REJECTED.");
            }
        } else if ("RECEIVED".equalsIgnoreCase(current)) {
            throw new ValidationException("RECEIVED requests are already closed and cannot change state.");
        } else if ("REJECTED".equalsIgnoreCase(current)) {
            if (!"REQUESTED".equalsIgnoreCase(next)) {
                throw new ValidationException("REJECTED requests cannot be modified unless reopened to REQUESTED.");
            }
        }
    }
}
