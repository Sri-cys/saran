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
import com.psgitech.library.service.GoodwillExtensionService;
import com.psgitech.library.model.GoodwillExtension;
import com.psgitech.library.service.NotificationService;
import com.psgitech.library.service.TrustStandingService;
import com.psgitech.library.dto.librarian.TrustStandingDTO;
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
import java.sql.SQLException;
import com.psgitech.library.model.Student;
import com.psgitech.library.config.BorrowingPolicy;
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
@WebServlet(name = "LibrarianCirculationServlet", urlPatterns = {"/api/librarian/circulation", "/api/librarian/circulation/*"})
public class LibrarianCirculationServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LibrarianCirculationServlet.class.getName());

    private BorrowingService borrowingService;
    private BorrowRepository borrowRepository;
    private AllocationService allocationService;
    private AllocationRepository allocationRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private ActivityLogService activityLogService;
    private GoodwillExtensionService goodwillService;
    private TrustStandingService trustStandingService;
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
        this.goodwillService = new GoodwillExtensionService();
        this.trustStandingService = new TrustStandingService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/calendar")) {
                int year = LocalDate.now().getYear();
                int month = LocalDate.now().getMonthValue();
                if (req.getParameter("year") != null) {
                    try { year = Integer.parseInt(req.getParameter("year")); } catch (NumberFormatException ignored) {}
                }
                if (req.getParameter("month") != null) {
                    try { month = Integer.parseInt(req.getParameter("month")); } catch (NumberFormatException ignored) {}
                }
                List<Map<String, Object>> data = borrowRepository.findDueCalendarData(year, month);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(data)));
            } else if (path.endsWith("/due-soon")) {
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
            } else if (path.endsWith("/goodwill-status")) {
                String studentIdStr = req.getParameter("studentId");
                if (studentIdStr == null || studentIdStr.trim().isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write(gson.toJson(ApiResponse.error("MISSING_PARAM", "studentId is required")));
                    return;
                }
                int studentId = Integer.parseInt(studentIdStr.trim());
                boolean hasUsed = goodwillService.hasUsedGoodwillExtension(studentId);
                GoodwillExtension ext = goodwillService.getExtensionForStudent(studentId);
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("hasUsed", hasUsed);
                map.put("extension", ext);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(map)));
            } else if (path.endsWith("/goodwill-extensions")) {
                List<GoodwillExtension> list = goodwillService.getRecentExtensions(50);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
            } else if (path.endsWith("/borrower-card")) {
                handleBorrowerCard(req, resp);
            } else if (path.endsWith("/copy-inspect")) {
                handleCopyInspect(req, resp);
            } else {
                // Default: active borrowings with optional search query or specific due date
                String query = req.getParameter("q");
                String dueDateStr = req.getParameter("dueDate");
                List<BorrowTransaction> list;
                if (dueDateStr != null && !dueDateStr.trim().isEmpty()) {
                    list = borrowingService.getAllActiveBorrowings(null).stream()
                            .filter(t -> t.getDueDate() != null && t.getDueDate().toString().equals(dueDateStr.trim()))
                            .collect(java.util.stream.Collectors.toList());
                } else {
                    list = borrowingService.getAllActiveBorrowings(query);
                }
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
            } else if (path.endsWith("/goodwill-extension")) {
                handleGoodwillExtension(req, resp);
            } else if (path.endsWith("/express-action")) {
                handleExpressAction(req, resp);
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

    private void handleGoodwillExtension(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        if (!json.has("studentId") || !json.has("transactionId") || !json.has("reason")) {
            throw new ValidationException("studentId, transactionId, and reason are required.");
        }
        int studentId = json.get("studentId").getAsInt();
        int transactionId = json.get("transactionId").getAsInt();
        int extraDays = json.has("extraDays") && !json.get("extraDays").isJsonNull() ? json.get("extraDays").getAsInt() : 7;
        String reason = getRequiredString(json, "reason");

        User currentStaff = getAuthenticatedStaff(req);
        GoodwillExtension ext = goodwillService.grantGoodwillExtension(
                studentId, transactionId, extraDays, reason, currentStaff.getUserId(), req.getRemoteAddr()
        );

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(ext)));
    }

    private void handleBorrowerCard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String query = req.getParameter("query");
        if (query == null || query.trim().isEmpty()) {
            query = req.getParameter("q");
        }
        if (query == null || query.trim().isEmpty()) {
            throw new ValidationException("Query parameter (student roll number, email or ID) is required.");
        }
        String q = query.trim();

        User student = null;
        try {
            int uid = Integer.parseInt(q);
            student = userRepository.findById(uid);
        } catch (NumberFormatException ignored) {}

        if (student == null) {
            student = userRepository.findByRegisterNumber(q);
        }
        if (student == null) {
            student = userRepository.findByEmail(q);
        }

        if (student == null || student.getRole() != Role.STUDENT) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Student record not found for: " + q)));
            return;
        }

        int studentId = student.getUserId();
        TrustStandingDTO trust = trustStandingService.computeTrustStanding(studentId);
        List<BorrowTransaction> activeLoans = borrowingService.getStudentActiveBorrowings(studentId);

        int borrowingLimit = BorrowingPolicy.getMaxActiveBorrowingLimit();

        boolean canBorrow = activeLoans.size() < borrowingLimit &&
                trust.getActiveOverdueCount() == 0 &&
                trust.getTier() != TrustStandingDTO.Tier.RESTRICTED &&
                student.isActive();

        String regNo = (student instanceof Student) ? ((Student) student).getRegisterNumber() : student.getEmail();
        String dept = (student instanceof Student) ? ((Student) student).getDepartment() : "General";

        Map<String, Object> card = new java.util.HashMap<>();
        card.put("studentId", student.getUserId());
        card.put("name", student.getName());
        card.put("registerNumber", regNo != null ? regNo : student.getEmail());
        card.put("email", student.getEmail());
        card.put("department", dept != null ? dept : "General");
        card.put("active", student.isActive());
        card.put("borrowingLimit", borrowingLimit);
        card.put("activeLoansCount", activeLoans.size());
        card.put("canBorrow", canBorrow);
        card.put("trustStanding", trust);
        card.put("activeLoans", activeLoans);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(card)));
    }

    private void handleCopyInspect(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String identifier = req.getParameter("identifier");
        if (identifier == null || identifier.trim().isEmpty()) {
            identifier = req.getParameter("copyId");
        }
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ValidationException("Copy identifier (copy code or ID) is required.");
        }
        String idStr = identifier.trim();

        Map<String, Object> copyInfo = new java.util.HashMap<>();
        String sql = "SELECT bc.copy_id, bc.copy_code, bc.status AS copy_status, " +
                "b.book_id, b.title, b.author, b.isbn, b.category " +
                "FROM book_copies bc " +
                "JOIN books b ON bc.book_id = b.book_id " +
                "WHERE bc.copy_code = ? OR bc.copy_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idStr);
            int cid = -1;
            try { cid = Integer.parseInt(idStr); } catch (NumberFormatException ignored) {}
            ps.setInt(2, cid);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Copy not found for: " + idStr)));
                    return;
                }

                int copyId = rs.getInt("copy_id");
                String copyCode = rs.getString("copy_code");
                String status = rs.getString("copy_status");
                String title = rs.getString("title");

                copyInfo.put("copyId", copyId);
                copyInfo.put("copyCode", copyCode);
                copyInfo.put("status", status);
                copyInfo.put("bookId", rs.getInt("book_id"));
                copyInfo.put("title", title);
                copyInfo.put("author", rs.getString("author"));
                copyInfo.put("isbn", rs.getString("isbn"));
                copyInfo.put("category", rs.getString("category"));

                if ("AVAILABLE".equalsIgnoreCase(status)) {
                    copyInfo.put("suggestedAction", "ISSUE");
                    copyInfo.put("actionDescription", "Ready for checkout (Available)");
                } else if ("BORROWED".equalsIgnoreCase(status)) {
                    copyInfo.put("suggestedAction", "RETURN");
                    copyInfo.put("actionDescription", "Currently on loan (Return)");

                    // Fetch active loan details
                    String loanSql = "SELECT bt.transaction_id, bt.student_id, u.name AS borrower_name, " +
                            "COALESCE(u.register_number, u.email) AS borrower_reg, bt.due_date, " +
                            "(bt.due_date < CURRENT_DATE) AS is_overdue " +
                            "FROM borrow_transactions bt " +
                            "JOIN users u ON bt.student_id = u.user_id " +
                            "WHERE bt.copy_id = ? AND bt.status = 'ACTIVE' LIMIT 1";
                    try (PreparedStatement psLoan = conn.prepareStatement(loanSql)) {
                        psLoan.setInt(1, copyId);
                        try (ResultSet rsLoan = psLoan.executeQuery()) {
                            if (rsLoan.next()) {
                                copyInfo.put("transactionId", rsLoan.getInt("transaction_id"));
                                copyInfo.put("borrowerId", rsLoan.getInt("student_id"));
                                copyInfo.put("borrowerName", rsLoan.getString("borrower_name"));
                                copyInfo.put("borrowerReg", rsLoan.getString("borrower_reg"));
                                copyInfo.put("dueDate", rsLoan.getString("due_date"));
                                copyInfo.put("isOverdue", rsLoan.getBoolean("is_overdue"));
                            }
                        }
                    }
                } else if ("ALLOCATED".equalsIgnoreCase(status)) {
                    copyInfo.put("suggestedAction", "CONFIRM_PICKUP");
                    copyInfo.put("actionDescription", "Reserved copy awaiting student pickup");

                    String allocSql = "SELECT a.allocation_id, a.student_id, u.name AS allocated_name, " +
                            "COALESCE(u.register_number, u.email) AS allocated_reg, a.expires_at AS pickup_deadline " +
                            "FROM allocations a " +
                            "JOIN users u ON a.student_id = u.user_id " +
                            "WHERE a.copy_id = ? AND a.status = 'ALLOCATED' LIMIT 1";
                    try (PreparedStatement psAlloc = conn.prepareStatement(allocSql)) {
                        psAlloc.setInt(1, copyId);
                        try (ResultSet rsAlloc = psAlloc.executeQuery()) {
                            if (rsAlloc.next()) {
                                copyInfo.put("allocationId", rsAlloc.getInt("allocation_id"));
                                copyInfo.put("allocatedStudentId", rsAlloc.getInt("student_id"));
                                copyInfo.put("allocatedStudentName", rsAlloc.getString("allocated_name"));
                                copyInfo.put("allocatedStudentReg", rsAlloc.getString("allocated_reg"));
                                copyInfo.put("pickupDeadline", rsAlloc.getString("pickup_deadline"));
                            }
                        }
                    }
                } else {
                    copyInfo.put("suggestedAction", "BLOCKED");
                    copyInfo.put("actionDescription", "Copy is currently " + status + " and cannot be circulated.");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to inspect copy: " + idStr, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", "Database error inspecting copy.")));
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(copyInfo)));
    }

    private void handleExpressAction(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        String action = getRequiredString(json, "action");
        User currentStaff = getAuthenticatedStaff(req);

        if ("ISSUE".equalsIgnoreCase(action)) {
            String registerNumber = getRequiredString(json, "registerNumber");
            int copyId = json.get("copyId").getAsInt();

            BorrowTransaction tx = borrowingService.issueBook(registerNumber, copyId, currentStaff.getUserId());
            logStaffAction(req, "EXPRESS_ISSUE", "BORROW_TRANSACTION", String.valueOf(tx.getTransactionId()),
                    "Express Desk issued copy #" + copyId + " to student " + registerNumber);

            Map<String, Object> res = new java.util.HashMap<>();
            res.put("action", "ISSUE");
            res.put("transaction", tx);
            res.put("message", "Successfully issued " + (tx.getBookTitle() != null ? tx.getBookTitle() : ("copy #" + copyId)) + " to " + registerNumber);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(ApiResponse.ok(res)));

        } else if ("RETURN".equalsIgnoreCase(action)) {
            int transactionId = 0;
            if (json.has("transactionId") && !json.get("transactionId").isJsonNull()) {
                transactionId = json.get("transactionId").getAsInt();
            } else if (json.has("copyId") && !json.get("copyId").isJsonNull()) {
                int copyId = json.get("copyId").getAsInt();
                BorrowTransaction activeTx = borrowRepository.findActiveByCopyId(copyId);
                if (activeTx != null) {
                    transactionId = activeTx.getTransactionId();
                }
            }

            if (transactionId <= 0) {
                throw new ValidationException("A valid transactionId or active copyId is required for check-in.");
            }

            BorrowTransaction tx = borrowingService.returnBook(transactionId, currentStaff.getUserId());
            logStaffAction(req, "EXPRESS_RETURN", "BORROW_TRANSACTION", String.valueOf(transactionId),
                    "Express Desk returned book for Tx #" + transactionId);

            Map<String, Object> res = new java.util.HashMap<>();
            res.put("action", "RETURN");
            res.put("transaction", tx);
            res.put("message", "Book successfully returned (Tx #" + transactionId + ")");

            // Check if returned copy triggered FIFO auto-allocation to a waiting reservation
            boolean holdAtDesk = false;
            String allocatedToStudent = null;
            String bookTitle = tx.getBookTitle();

            try (Connection conn = DBConnection.getConnection()) {
                String allocSql = "SELECT u.name AS student_name, COALESCE(u.register_number, u.email) AS reg_no, b.title AS book_title " +
                        "FROM allocations a " +
                        "JOIN users u ON a.student_id = u.user_id " +
                        "JOIN books b ON a.book_id = b.book_id " +
                        "WHERE a.copy_id = ? AND a.status = 'ALLOCATED' " +
                        "ORDER BY a.allocated_at DESC LIMIT 1";
                try (PreparedStatement psAlloc = conn.prepareStatement(allocSql)) {
                    psAlloc.setInt(1, tx.getCopyId());
                    try (ResultSet rsAlloc = psAlloc.executeQuery()) {
                        if (rsAlloc.next()) {
                            holdAtDesk = true;
                            allocatedToStudent = rsAlloc.getString("student_name") + " (" + rsAlloc.getString("reg_no") + ")";
                            if (bookTitle == null) bookTitle = rsAlloc.getString("book_title");
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to verify return auto-allocation for hold-at-desk", e);
            }

            res.put("holdAtDesk", holdAtDesk);
            res.put("allocatedToStudent", allocatedToStudent);
            res.put("bookTitle", bookTitle);
            if (holdAtDesk) {
                res.put("alertBanner", "HOLD AT DESK — Allocated to " + allocatedToStudent);
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(ApiResponse.ok(res)));

        } else {
            throw new ValidationException("Unsupported Express Desk action: " + action + " (Expected ISSUE or RETURN).");
        }
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
