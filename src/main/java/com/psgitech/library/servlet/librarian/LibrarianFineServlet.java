package com.psgitech.library.servlet.librarian;

import com.google.gson.Gson;
import com.psgitech.library.dto.librarian.ApiResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet providing read-only fine tracking and payment reference audit for the Librarian Portal.
 * Mutating fine actions (waivers, manual reconciliation) strictly reject Librarians with HTTP 403.
 * URL Mapping: /api/librarian/fines/*
 */
@WebServlet(name = "LibrarianFineServlet", urlPatterns = {"/api/librarian/fines/*"})
public class LibrarianFineServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LibrarianFineServlet.class.getName());
    private Gson gson;

    @Override
    public void init() {
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        int page = 1;
        int pageSize = 20;
        try {
            if (req.getParameter("page") != null) page = Integer.parseInt(req.getParameter("page"));
            if (req.getParameter("pageSize") != null) pageSize = Integer.parseInt(req.getParameter("pageSize"));
        } catch (NumberFormatException ignored) {}

        page = Math.max(1, page);
        pageSize = Math.max(1, Math.min(100, pageSize));
        int offset = (page - 1) * pageSize;

        String statusFilter = req.getParameter("status"); // UNPAID, PARTIALLY_PAID, PAID, WAIVED
        String query = req.getParameter("q");

        StringBuilder sql = new StringBuilder(
            "SELECT f.fine_id, f.borrow_transaction_id, f.student_id, f.book_id, f.copy_id, " +
            "f.overdue_days, f.original_amount, f.paid_amount, f.waived_amount, f.outstanding_amount, " +
            "f.status, f.fine_type, f.created_at, " +
            "b.title, b.isbn, c.copy_code, u.name AS student_name, u.register_number, " +
            "p.razorpay_payment_id, p.transaction_reference, p.payment_method, p.payment_date " +
            "FROM fines f " +
            "JOIN books b ON f.book_id = b.book_id " +
            "JOIN book_copies c ON f.copy_id = c.copy_id " +
            "JOIN users u ON f.student_id = u.user_id " +
            "LEFT JOIN payments p ON f.fine_id = p.fine_id AND p.payment_status = 'SUCCESS' " +
            "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            sql.append("AND f.status = ? ");
            params.add(statusFilter.trim().toUpperCase());
        }
        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (b.title LIKE ? OR u.name LIKE ? OR u.register_number LIKE ? OR c.copy_code LIKE ?) ");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q); params.add(q);
        }

        sql.append("ORDER BY f.fine_id DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> fines = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("fineId", rs.getInt("fine_id"));
                    m.put("transactionId", rs.getInt("borrow_transaction_id"));
                    m.put("studentId", rs.getInt("student_id"));
                    m.put("studentName", rs.getString("student_name"));
                    m.put("registerNumber", rs.getString("register_number"));
                    m.put("bookTitle", rs.getString("title"));
                    m.put("isbn", rs.getString("isbn"));
                    m.put("copyCode", rs.getString("copy_code"));
                    m.put("overdueDays", rs.getInt("overdue_days"));
                    m.put("originalAmount", rs.getBigDecimal("original_amount"));
                    m.put("paidAmount", rs.getBigDecimal("paid_amount"));
                    m.put("waivedAmount", rs.getBigDecimal("waived_amount"));
                    m.put("outstandingAmount", rs.getBigDecimal("outstanding_amount"));
                    m.put("status", rs.getString("status"));
                    m.put("fineType", rs.getString("fine_type"));
                    m.put("createdAt", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : "");

                    // Masked payment reference
                    String rzpId = rs.getString("razorpay_payment_id");
                    String txRef = rs.getString("transaction_reference");
                    String ref = (rzpId != null && !rzpId.isEmpty()) ? rzpId : txRef;
                    m.put("paymentReference", maskReference(ref));
                    m.put("paymentMethod", rs.getString("payment_method"));
                    m.put("paymentDate", rs.getTimestamp("payment_date") != null ? rs.getTimestamp("payment_date").toString() : "");

                    fines.add(m);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to query fine audit records", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(fines)));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Enforce strict read-only constraint for librarians on fines
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(gson.toJson(ApiResponse.error("FORBIDDEN",
                "Access Denied: Librarians cannot modify fine amounts or grant waivers. Administrator privileges required.")));
    }

    private String maskReference(String ref) {
        if (ref == null || ref.trim().isEmpty()) return "N/A";
        String s = ref.trim();
        if (s.length() <= 6) return s;
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }
}
