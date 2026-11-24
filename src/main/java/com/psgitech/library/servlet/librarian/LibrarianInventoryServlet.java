package com.psgitech.library.servlet.librarian;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.dto.librarian.ApiResponse;
import com.psgitech.library.dto.librarian.ShelfLocationDTO;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.exception.DuplicateBookException;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookCopy;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.service.ActivityLogService;
import com.psgitech.library.service.BookService;
import com.psgitech.library.service.ShelfLocatorService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet managing Librarian Inventory, Catalog Operations, Copies, and Shelf Locator.
 * URL Mapping: /api/librarian/inventory/*
 */
@WebServlet(name = "LibrarianInventoryServlet", urlPatterns = {"/api/librarian/inventory", "/api/librarian/inventory/*"})
public class LibrarianInventoryServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LibrarianInventoryServlet.class.getName());

    private BookService bookService;
    private BookRepository bookRepository;
    private ShelfLocatorService shelfLocatorService;
    private ActivityLogService activityLogService;
    private Gson gson;

    @Override
    public void init() {
        this.bookService = new BookService();
        this.bookRepository = new BookRepository();
        this.shelfLocatorService = new ShelfLocatorService();
        this.activityLogService = new ActivityLogService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/details")) {
                handleBookDetails(req, resp);
            } else if (path.endsWith("/shelf-location")) {
                handleGetShelfLocation(req, resp);
            } else {
                handleListBooks(req, resp);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Inventory GET error", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/book")) {
                handleAddBook(req, resp);
            } else if (path.endsWith("/copies")) {
                handleAddCopies(req, resp);
            } else if (path.endsWith("/retire")) {
                handleRetireCopy(req, resp);
            } else if (path.endsWith("/shelf-location")) {
                handleSaveShelfLocation(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Unknown endpoint: " + path)));
            }
        } catch (ValidationException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(ApiResponse.error("VALIDATION_ERROR", e.getMessage())));
        } catch (DuplicateBookException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(gson.toJson(ApiResponse.error("CONFLICT", e.getMessage())));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Inventory POST error", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            handleUpdateBook(req, resp);
        } catch (ValidationException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(ApiResponse.error("VALIDATION_ERROR", e.getMessage())));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Inventory PUT error", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    private void handleListBooks(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int page = 1;
        int pageSize = 20;
        try {
            if (req.getParameter("page") != null) page = Integer.parseInt(req.getParameter("page"));
            if (req.getParameter("pageSize") != null) pageSize = Integer.parseInt(req.getParameter("pageSize"));
        } catch (NumberFormatException ignored) {}

        page = Math.max(1, page);
        pageSize = Math.max(1, Math.min(100, pageSize));
        int offset = (page - 1) * pageSize;

        String query = req.getParameter("q");
        String category = req.getParameter("category");
        String department = req.getParameter("department");
        String stockFilter = req.getParameter("stock"); // IN_STOCK, LOW_STOCK, OUT_OF_STOCK

        List<Book> books = bookRepository.findBooksWithFilters(query, category, department, stockFilter, offset, pageSize);
        int total = bookRepository.countBooksWithFilters(query, category, department, stockFilter);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(books, page, pageSize, total)));
    }

    private void handleBookDetails(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("bookId");
        if (idStr == null || idStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(ApiResponse.error("VALIDATION_ERROR", "bookId parameter is required.")));
            return;
        }

        int bookId = Integer.parseInt(idStr.trim());
        Book book = bookService.getBookDetails(bookId, true);
        if (book == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Book not found: " + bookId)));
            return;
        }

        ShelfLocationDTO shelfLoc = shelfLocatorService.getLocationForBook(bookId);
        Map<String, Object> data = new HashMap<>();
        data.put("book", book);
        data.put("shelfLocation", shelfLoc);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(data)));
    }

    private void handleAddBook(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        Book book = new Book();
        book.setTitle(getRequiredString(json, "title"));
        book.setAuthor(getRequiredString(json, "author"));
        book.setIsbn(getRequiredString(json, "isbn"));
        book.setPublisher(getRequiredString(json, "publisher"));
        book.setCategory(getRequiredString(json, "category"));
        book.setDepartment(getRequiredString(json, "department"));
        book.setEdition(json.has("edition") && !json.get("edition").isJsonNull() ? json.get("edition").getAsString() : "1st");
        book.setPublicationYear(json.has("publicationYear") ? json.get("publicationYear").getAsInt() : 2024);
        book.setDescription(json.has("description") && !json.get("description").isJsonNull() ? json.get("description").getAsString() : "");
        book.setCoverImageUrl(json.has("coverImageUrl") && !json.get("coverImageUrl").isJsonNull() ? json.get("coverImageUrl").getAsString() : null);

        int initialCopies = json.has("copies") ? json.get("copies").getAsInt() : 1;
        Book created = bookService.addBook(book, initialCopies);

        // Optional initial shelf location
        if (json.has("shelfLocation") && json.get("shelfLocation").isJsonObject()) {
            JsonObject sl = json.getAsJsonObject("shelfLocation");
            shelfLocatorService.updateLocation(
                    created.getBookId(), null,
                    sl.has("floor") ? sl.get("floor").getAsString() : "Ground Floor",
                    sl.has("block") ? sl.get("block").getAsString() : "Main Block",
                    sl.has("section") ? sl.get("section").getAsString() : created.getDepartment(),
                    sl.has("rack") ? sl.get("rack").getAsString() : "R-01",
                    sl.has("shelf") ? sl.get("shelf").getAsString() : "S-1"
            );
        }

        logStaffAction(req, "BOOK_CREATED", "BOOK", String.valueOf(created.getBookId()), "Created book: " + created.getTitle() + " with " + initialCopies + " copies.");

        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(created)));
    }

    private void handleUpdateBook(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int bookId = json.get("bookId").getAsInt();
        Book book = bookService.getBookDetails(bookId, false);
        if (book == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Book not found.")));
            return;
        }

        if (json.has("title")) book.setTitle(json.get("title").getAsString());
        if (json.has("author")) book.setAuthor(json.get("author").getAsString());
        if (json.has("publisher")) book.setPublisher(json.get("publisher").getAsString());
        if (json.has("category")) book.setCategory(json.get("category").getAsString());
        if (json.has("department")) book.setDepartment(json.get("department").getAsString());
        if (json.has("edition")) book.setEdition(json.get("edition").getAsString());
        if (json.has("publicationYear")) book.setPublicationYear(json.get("publicationYear").getAsInt());
        if (json.has("description")) book.setDescription(json.get("description").getAsString());
        if (json.has("coverImageUrl")) book.setCoverImageUrl(json.get("coverImageUrl").getAsString());

        Book updated = bookService.updateBook(book);
        logStaffAction(req, "BOOK_UPDATED", "BOOK", String.valueOf(bookId), "Updated metadata for: " + updated.getTitle());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(updated)));
    }

    private void handleAddCopies(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int bookId = json.get("bookId").getAsInt();
        int count = json.get("count").getAsInt();
        if (count <= 0 || count > 50) {
            throw new ValidationException("Copy count must be between 1 and 50.");
        }

        List<BookCopy> copies = bookService.addCopies(bookId, count);
        logStaffAction(req, "COPIES_ADDED", "BOOK", String.valueOf(bookId), "Added " + count + " new copies.");

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(copies)));
    }

    private void handleRetireCopy(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int copyId = json.get("copyId").getAsInt();
        String reason = json.has("reason") ? json.get("reason").getAsString() : "Routine withdrawal";

        BookCopy copy = bookRepository.findCopyById(copyId);
        if (copy == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Copy not found: " + copyId)));
            return;
        }

        if (copy.getStatus() == CopyStatus.BORROWED || copy.getStatus() == CopyStatus.ALLOCATED
                || copy.getStatus() == CopyStatus.TRANSFER_PENDING) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(gson.toJson(ApiResponse.error("CONFLICT",
                    "Cannot retire copy while in " + copy.getStatus() + " status.")));
            return;
        }

        boolean ok = bookRepository.updateCopyStatus(copyId, "RETIRED");
        if (ok) {
            logStaffAction(req, "COPY_RETIRED", "COPY", String.valueOf(copyId), "Copy retired: " + copy.getCopyCode() + ". Reason: " + reason);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(ApiResponse.ok(Map.of("copyId", copyId, "status", "RETIRED"))));
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", "Failed to retire copy.")));
        }
    }

    private void handleGetShelfLocation(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String bookIdStr = req.getParameter("bookId");
        String copyIdStr = req.getParameter("copyId");

        ShelfLocationDTO loc = null;
        if (copyIdStr != null && !copyIdStr.trim().isEmpty()) {
            loc = shelfLocatorService.getLocationForCopy(Integer.parseInt(copyIdStr.trim()));
        } else if (bookIdStr != null && !bookIdStr.trim().isEmpty()) {
            loc = shelfLocatorService.getLocationForBook(Integer.parseInt(bookIdStr.trim()));
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(loc)));
    }

    private void handleSaveShelfLocation(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int bookId = json.get("bookId").getAsInt();
        Integer copyId = json.has("copyId") && !json.get("copyId").isJsonNull() ? json.get("copyId").getAsInt() : null;

        String floor = getRequiredString(json, "floor");
        String block = getRequiredString(json, "block");
        String section = getRequiredString(json, "section");
        String rack = getRequiredString(json, "rack");
        String shelf = getRequiredString(json, "shelf");

        boolean ok = shelfLocatorService.updateLocation(bookId, copyId, floor, block, section, rack, shelf);
        if (ok) {
            logStaffAction(req, "SHELF_LOCATION_UPDATED", "BOOK", String.valueOf(bookId),
                    "Updated location: " + floor + " / " + block + " / " + section + " / " + rack + " / " + shelf);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(ApiResponse.ok(Map.of("success", true, "bookId", bookId))));
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", "Failed to update shelf location.")));
        }
    }

    private JsonObject parseJsonBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private String getRequiredString(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull() || json.get(field).getAsString().trim().isEmpty()) {
            throw new ValidationException("Field '" + field + "' is required.");
        }
        return json.get(field).getAsString().trim();
    }

    private void logStaffAction(HttpServletRequest req, String eventType, String targetEntity, String targetId, String details) {
        HttpSession session = req.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("user") : null;
        int actorId = user != null ? user.getUserId() : 0;
        String role = user != null ? user.getRole().name() : "LIBRARIAN";
        activityLogService.logEvent(eventType, actorId, role, targetEntity, targetId, details, req.getRemoteAddr());
    }
}
