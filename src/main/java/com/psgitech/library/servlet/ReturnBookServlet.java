package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.BorrowTransactionNotFoundException;
import com.psgitech.library.exception.InvalidBorrowingOperationException;
import com.psgitech.library.model.AllocationRecord;
import com.psgitech.library.model.AllocationResult;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.model.User;
import com.psgitech.library.service.BorrowingService;
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
import java.util.Map;

/**
 * Servlet handling Book Returns.
 * Restricted to Librarians and Administrators.
 * URL Mapping: /borrow/return, /borrowings/return
 */
@WebServlet(name = "ReturnBookServlet", urlPatterns = {"/borrow/return", "/borrowings/return"})
public class ReturnBookServlet extends HttpServlet {
    private BorrowingService borrowingService;
    private Gson gson;

    @Override
    public void init() {
        this.borrowingService = new BorrowingService();
        this.gson = GsonProvider.getGson();
    }

    private User getAuthenticatedUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return (session != null) ? (User) session.getAttribute("user") : null;
    }

    private boolean isStaff(User user) {
        return user != null && (user.getRole() == Role.LIBRARIAN || user.getRole() == Role.ADMIN);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required. Please log in.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        if (!isStaff(currentUser)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access denied. Only Librarians and Administrators can process returns.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        Integer transactionId = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            try {
                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                if (json.has("transactionId")) transactionId = json.get("transactionId").getAsInt();
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Invalid JSON format: " + e.getMessage());
                resp.getWriter().write(gson.toJson(err));
                return;
            }
        } else {
            String txStr = req.getParameter("transactionId");
            if (txStr != null && !txStr.trim().isEmpty()) {
                try {
                    transactionId = Integer.parseInt(txStr.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<String, Object> responseData = new HashMap<>();

        if (transactionId == null || transactionId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("success", false);
            responseData.put("message", "Valid transaction ID is required.");
            resp.getWriter().write(gson.toJson(responseData));
            return;
        }

        try {
            BorrowTransaction tx = borrowingService.returnBook(transactionId, currentUser.getUserId());

            resp.setStatus(HttpServletResponse.SC_OK);
            responseData.put("success", true);
            responseData.put("message", "Book returned successfully");

            Map<String, Object> data = new HashMap<>();
            data.put("transactionId", tx.getTransactionId());
            data.put("returnDate", tx.getReturnDate() != null ? tx.getReturnDate().toString() : null);
            data.put("status", tx.getStatus().name());
            data.put("copyCode", tx.getCopyCode());
            data.put("overdueDays", tx.getOverdueDays());

            // Feature 5 (Section 9.1): Add "allocation" object or null
            AllocationResult allocRes = tx.getAllocationResult();
            if (allocRes != null && allocRes.isAllocated() && allocRes.getAllocation() != null) {
                AllocationRecord ar = allocRes.getAllocation();
                Map<String, Object> allocMap = new HashMap<>();
                allocMap.put("allocationId", ar.getAllocationId());
                allocMap.put("copyId", ar.getCopyId());
                allocMap.put("copyIdentifier", ar.getCopyCode());
                allocMap.put("copyCode", ar.getCopyCode());
                allocMap.put("bookTitle", ar.getBookTitle());
                allocMap.put("studentName", ar.getStudentName());
                allocMap.put("registerNumber", ar.getRegisterNumber());
                allocMap.put("expiresAt", ar.getExpiresAt() != null ? ar.getExpiresAt().toString() : null);
                data.put("allocation", allocMap);
            } else {
                data.put("allocation", null);
            }

            // Feature 6: Add "fine" object or null
            FineRecord fine = tx.getFineRecord();
            if (fine != null && fine.getFineId() != null) {
                Map<String, Object> fineMap = new HashMap<>();
                fineMap.put("fineId", fine.getFineId());
                fineMap.put("overdueDays", fine.getOverdueDays());
                fineMap.put("amount", fine.getOriginalAmount());
                fineMap.put("outstanding", fine.getOutstandingAmount());
                fineMap.put("outstandingAmount", fine.getOutstandingAmount());
                fineMap.put("status", fine.getStatus().name());
                data.put("fine", fineMap);
            } else {
                data.put("fine", null);
            }

            responseData.put("data", data);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (BorrowTransactionNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (InvalidBorrowingOperationException | IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseData.put("success", false);
            responseData.put("message", "An unexpected error occurred during book return.");
            resp.getWriter().write(gson.toJson(responseData));
        }
    }
}

