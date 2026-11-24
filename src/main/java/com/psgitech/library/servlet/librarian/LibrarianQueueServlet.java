package com.psgitech.library.servlet.librarian;

import com.google.gson.Gson;
import com.psgitech.library.dto.librarian.ApiResponse;
import com.psgitech.library.model.BookInterchange;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.service.BookInterchangeService;
import com.psgitech.library.service.ReservationService;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet handling Queues (Reservations & Allocations) and Student-to-Student Transfers.
 * Read-only view for Librarians.
 * URL Mapping: /api/librarian/queues/*
 */
@WebServlet(name = "LibrarianQueueServlet", urlPatterns = {"/api/librarian/queues", "/api/librarian/queues/*"})
public class LibrarianQueueServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LibrarianQueueServlet.class.getName());

    private ReservationService reservationService;
    private BookInterchangeService interchangeService;
    private Gson gson;

    @Override
    public void init() {
        this.reservationService = new ReservationService();
        this.interchangeService = new BookInterchangeService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/transfers")) {
                handleTransfers(req, resp);
            } else if (path.endsWith("/allocations")) {
                handleAllocations(req, resp);
            } else {
                handleReservations(req, resp);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Queues GET error: " + path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    private void handleReservations(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int page = 1;
        int pageSize = 20;
        try {
            if (req.getParameter("page") != null) page = Integer.parseInt(req.getParameter("page"));
            if (req.getParameter("pageSize") != null) pageSize = Integer.parseInt(req.getParameter("pageSize"));
        } catch (NumberFormatException ignored) {}

        Integer bookId = null;
        if (req.getParameter("bookId") != null && !req.getParameter("bookId").trim().isEmpty()) {
            try { bookId = Integer.parseInt(req.getParameter("bookId").trim()); } catch (NumberFormatException ignored) {}
        }

        List<Reservation> list = reservationService.getAllActiveReservations(page, pageSize, bookId);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
    }

    private void handleAllocations(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String sql =
            "SELECT a.allocation_id, a.reservation_id, a.copy_id, a.book_id, a.student_id, " +
            "a.allocated_at, a.collection_deadline, a.status, " +
            "b.title, b.isbn, c.copy_code, u.name AS student_name, u.register_number, u.email " +
            "FROM allocations a " +
            "JOIN books b ON a.book_id = b.book_id " +
            "JOIN book_copies c ON a.copy_id = c.copy_id " +
            "JOIN users u ON a.student_id = u.user_id " +
            "ORDER BY a.collection_deadline ASC LIMIT 50";

        List<Map<String, Object>> allocations = new ArrayList<>();
        long nowMillis = System.currentTimeMillis();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("allocationId", rs.getInt("allocation_id"));
                m.put("reservationId", rs.getInt("reservation_id"));
                m.put("copyId", rs.getInt("copy_id"));
                m.put("copyCode", rs.getString("copy_code"));
                m.put("bookId", rs.getInt("book_id"));
                m.put("bookTitle", rs.getString("title"));
                m.put("isbn", rs.getString("isbn"));
                m.put("studentId", rs.getInt("student_id"));
                m.put("studentName", rs.getString("student_name"));
                m.put("registerNumber", rs.getString("register_number"));
                m.put("status", rs.getString("status"));

                Timestamp deadline = rs.getTimestamp("collection_deadline");
                m.put("collectionDeadline", deadline != null ? deadline.toString() : "");

                if (deadline != null) {
                    long remainingSecs = Math.max(0, (deadline.getTime() - nowMillis) / 1000);
                    m.put("remainingSeconds", remainingSecs);
                    m.put("isExpired", remainingSecs <= 0);
                } else {
                    m.put("remainingSeconds", 0);
                    m.put("isExpired", false);
                }
                allocations.add(m);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load active allocations", e);
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(allocations)));
    }

    private void handleTransfers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int limit = 50;
        int offset = 0;
        String statusFilter = req.getParameter("status"); // REQUESTED, COMPLETED, REJECTED, CANCELLED

        List<BookInterchange> rawList = interchangeService.getAllInterchanges(limit, offset, statusFilter);

        // Map to safe DTO representation (ensure OTP hashes are never exposed)
        List<Map<String, Object>> safeTransfers = new ArrayList<>();
        for (BookInterchange bi : rawList) {
            Map<String, Object> m = new HashMap<>();
            m.put("interchangeId", bi.getInterchangeId());
            m.put("transactionId", bi.getTransactionId());
            m.put("fromStudentId", bi.getFromStudentId());
            m.put("fromStudentName", bi.getFromStudentName());
            m.put("fromRegisterNumber", bi.getFromStudentRegNo());
            m.put("toStudentId", bi.getToStudentId());
            m.put("toStudentName", bi.getToStudentName());
            m.put("toRegisterNumber", bi.getToStudentRegNo());
            m.put("bookId", bi.getBookId());
            m.put("bookTitle", bi.getBookTitle());
            m.put("copyId", bi.getCopyId());
            m.put("copyCode", bi.getCopyCode());
            m.put("status", bi.getStatus() != null ? bi.getStatus().name() : "");
            m.put("requestedAt", bi.getRequestedAt() != null ? bi.getRequestedAt().toString() : "");
            m.put("completedAt", bi.getCompletedAt() != null ? bi.getCompletedAt().toString() : "");

            // Masked OTP status: strictly "Verified" or "Not Verified"
            boolean isVerified = "COMPLETED".equalsIgnoreCase(String.valueOf(bi.getStatus()));
            m.put("otpVerificationStatus", isVerified ? "Verified" : "Not Verified");

            safeTransfers.add(m);
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(safeTransfers)));
    }
}
