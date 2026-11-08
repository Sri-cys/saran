package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.exception.BorrowTransactionNotFoundException;
import com.psgitech.library.exception.InvalidBorrowingOperationException;
import com.psgitech.library.exception.StudentNotFoundException;
import com.psgitech.library.model.BorrowTransaction;
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
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "RenewBookServlet", urlPatterns = {"/borrow/renew"})
public class RenewBookServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(RenewBookServlet.class.getName());
    private BorrowingService borrowingService;
    private Gson gson;

    @Override
    public void init() {
        this.borrowingService = new BorrowingService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute("user") : null;
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required. Please sign in.");
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
                try { transactionId = Integer.parseInt(txStr.trim()); } catch (NumberFormatException ignored) {}
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
            BorrowTransaction renewedTx = borrowingService.renewLoan(
                    transactionId, currentUser.getUserId(), currentUser.getUserId(), currentUser.getRole()
            );

            resp.setStatus(HttpServletResponse.SC_OK);
            responseData.put("success", true);
            responseData.put("message", "Loan renewed successfully! New due date: " + renewedTx.getDueDate() + ".");
            responseData.put("data", renewedTx);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (BorrowTransactionNotFoundException | StudentNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));
        } catch (InvalidBorrowingOperationException | IllegalArgumentException | IllegalStateException e) {
            // Record Feature 11: RENEWAL_REJECTED in separate transaction (Decision D10)
            try (java.sql.Connection notifConn = com.psgitech.library.util.DBConnection.getConnection()) {
                notifConn.setAutoCommit(false);
                com.psgitech.library.model.NotificationDraft draft = com.psgitech.library.model.NotificationDraft
                        .builder(com.psgitech.library.enums.NotificationType.RENEWAL_REJECTED, currentUser.getUserId(), "RENEWAL_REJECTED:" + transactionId + ":" + System.currentTimeMillis())
                        .entity("BORROW_TRANSACTION", transactionId)
                        .param("bookTitle", "Book Loan #" + transactionId)
                        .param("reason", e.getMessage())
                        .build();
                new com.psgitech.library.service.NotificationRecorder().record(notifConn, draft);
                notifConn.commit();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to record renewal rejection notification", ex);
            }

            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error renewing loan #" + transactionId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseData.put("success", false);
            responseData.put("message", "An unexpected error occurred while processing the renewal request.");
            resp.getWriter().write(gson.toJson(responseData));
        }
    }
}

