package com.psgitech.library.servlet.librarian;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.dto.librarian.ApiResponse;
import com.psgitech.library.enums.CopyDisposition;
import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.BorrowingLimitExceededException;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.AllocationRecord;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.Notification;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.AllocationRepository;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.NotificationRepository;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.service.ActivityLogService;
import com.psgitech.library.service.AllocationService;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.service.NotificationService;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet handling Librarian Circulation Operations:
 * Issue, Return, Renewal, Allocation Collection, and Overdue Notifications.
 * All mutations route strictly through the core domain services.
 * URL Mapping: /api/librarian/circulation/*
 */
@WebServlet(name = "LibrarianCirculationServlet", urlPatterns = {"/api/librarian/circulation/*"})
public class LibrarianCirculationServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LibrarianCirculationServlet.class.getName());

    private BorrowingService borrowingService;
    private BorrowRepository borrowRepository;
    private AllocationService allocationService;
    private AllocationRepository allocationRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private ActivityLogService activityLogService;
    private Gson gson;

    @Override
    public void init() {
        this.borrowingService = new BorrowingService();
        this.borrowRepository = new BorrowRepository();
        this.allocationService = new AllocationService();
        this.allocationRepository = new AllocationRepository();
        this.userRepository = new UserRepository();
        this.notificationService = new NotificationService();
        this.activityLogService = new ActivityLogService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/due-soon")) {
                List<BorrowTransaction> list = borrowRepository.findDueSoonTransactions();
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
            } else if (path.endsWith("/overdue")) {
                List<BorrowTransaction> list = borrowingService.getOverdueBorrowings();
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
            } else if (path.endsWith("/recent-issued")) {
                List<BorrowTransaction> list = borrowRepository.findRecentlyIssuedTransactions(50);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
            } else if (path.endsWith("/recent-returned")) {
                List<BorrowTransaction> list = borrowRepository.findRecentlyReturnedTransactions(50);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
            } else {
                // Default: active borrowings with optional search query
                String query = req.getParameter("q");
                List<BorrowTransaction> list = borrowingService.getAllActiveBorrowings(query);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Circulation GET error: " + path, e);
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
            if (path.endsWith("/issue")) {
                handleIssueBook(req, resp);
            } else if (path.endsWith("/return")) {
                handleReturnBook(req, resp);
            } else if (path.endsWith("/renew")) {
                handleRenewLoan(req, resp);
            } else if (path.endsWith("/confirm-pickup")) {
                handleConfirmPickup(req, resp);
            } else if (path.endsWith("/overdue-reminder")) {
                handleOverdueReminder(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Unknown endpoint: " + path)));
            }
        } catch (BorrowingLimitExceededException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(gson.toJson(ApiResponse.error("BORROWING_LIMIT_EXCEEDED", e.getMessage())));
        } catch (ValidationException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(ApiResponse.error("VALIDATION_ERROR", e.getMessage())));
        } catch (IllegalStateException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(gson.toJson(ApiResponse.error("CONFLICT", e.getMessage())));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Circulation POST error: " + path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    private void handleIssueBook(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        String registerNumber = getRequiredString(json, "registerNumber");
        int copyId = json.get("copyId").getAsInt();

        User currentStaff = getAuthenticatedStaff(req);
        BorrowTransaction tx = borrowingService.issueBook(registerNumber, copyId, currentStaff.getUserId());

        logStaffAction(req, "LOAN_ISSUED", "BORROW_TRANSACTION", String.valueOf(tx.getTransactionId()),
                "Issued copy #" + copyId + " to student " + registerNumber + " (Tx #" + tx.getTransactionId() + ")");

        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(tx)));
    }

    private void handleReturnBook(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int transactionId = json.get("transactionId").getAsInt();

        User currentStaff = getAuthenticatedStaff(req);
        BorrowTransaction tx = borrowingService.returnBook(transactionId, currentStaff.getUserId());

        logStaffAction(req, "LOAN_RETURNED", "BORROW_TRANSACTION", String.valueOf(transactionId),
                "Checked in returned book for Tx #" + transactionId);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(tx)));
    }

    private void handleRenewLoan(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int transactionId = json.get("transactionId").getAsInt();

        BorrowTransaction existing = borrowingService.getTransactionById(transactionId);
        if (existing == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Borrowing transaction #" + transactionId + " not found.")));
            return;
        }

        User currentStaff = getAuthenticatedStaff(req);
        BorrowTransaction renewed = borrowingService.renewLoan(
                transactionId, existing.getStudentId(), currentStaff.getUserId(), currentStaff.getRole()
        );

        logStaffAction(req, "LOAN_RENEWED", "BORROW_TRANSACTION", String.valueOf(transactionId),
                "Loan #" + transactionId + " renewed by staff. New due date: " + renewed.getDueDate());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(renewed)));
    }

    private void handleConfirmPickup(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int copyId = json.get("copyId").getAsInt();
        String studentRegNo = getRequiredString(json, "registerNumber");

        User currentStaff = getAuthenticatedStaff(req);
        BorrowTransaction tx = borrowingService.issueBook(studentRegNo, copyId, currentStaff.getUserId());

        logStaffAction(req, "ALLOCATION_COLLECTED", "BORROW_TRANSACTION", String.valueOf(tx.getTransactionId()),
                "Allocation collected by " + studentRegNo + " for copy #" + copyId + " (Tx #" + tx.getTransactionId() + ")");

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(tx)));
    }

    private void handleOverdueReminder(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int transactionId = json.get("transactionId").getAsInt();

        BorrowTransaction tx = borrowingService.getTransactionById(transactionId);
        if (tx == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Transaction #" + transactionId + " not found.")));
            return;
        }

        // Rate limit: Check if an overdue reminder was logged in the last 24h
        String checkSql = "SELECT COUNT(*) FROM library_activity_logs " +
                "WHERE event_type = 'OVERDUE_REMINDER_SENT' AND target_id = ? AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 24 HOUR)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, String.valueOf(transactionId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    resp.setStatus(429);
                    resp.getWriter().write(gson.toJson(ApiResponse.error("RATE_LIMITED",
                            "An overdue reminder has already been dispatched for this loan in the last 24 hours.")));
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check reminder rate limit", e);
        }

        // Send Notification
        String bookTitle = tx.getBookTitle() != null ? tx.getBookTitle() : "borrowed book";
        Notification notification = new Notification();
        notification.setRecipientUserId(tx.getStudentId());
        notification.setNotificationType(NotificationType.OVERDUE);
        notification.setCategory(NotificationCategory.DUE_REMINDERS);
        notification.setTitle("Overdue Book Reminder: " + bookTitle);
        notification.setMessage("Dear Student, your borrowed book '" + bookTitle + "' was due on " +
                tx.getDueDate().format(DateTimeFormatter.ISO_LOCAL_DATE) + ". Please return it to the library desk to avoid accumulating overdue fines.");
        notification.setDeduplicationKey("OVERDUE_TX_" + transactionId + "_" + LocalDate.now());
        notification.setRead(false);

        try (Connection conn = DBConnection.getConnection()) {
            new NotificationRepository().insert(conn, notification);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to insert in-app overdue notification", e);
        }

        logStaffAction(req, "OVERDUE_REMINDER_SENT", "BORROW_TRANSACTION", String.valueOf(transactionId),
                "Dispatched overdue reminder to student #" + tx.getStudentId() + " for Tx #" + transactionId);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(Map.of("success", true, "transactionId", transactionId))));
    }

    private User getAuthenticatedStaff(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return (User) session.getAttribute("user");
        }
        throw new ValidationException("Authentication session expired.");
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
        User user = getAuthenticatedStaff(req);
        activityLogService.logEvent(eventType, user.getUserId(), user.getRole().name(), targetEntity, targetId, details, req.getRemoteAddr());
    }
}
