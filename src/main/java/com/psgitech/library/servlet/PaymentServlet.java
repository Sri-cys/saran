package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.PaymentStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.Payment;
import com.psgitech.library.model.User;
import com.psgitech.library.service.PaymentService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet handling Student Fine Payment Lifecycle:
 * - Payment initiation with idempotency & rate limits
 * - Reference (UTR) submission
 * - Initiation cancellation
 * - Dispute lodging
 * - Payment listing (Student own / Staff all)
 * URL Mapping: /payments
 */
@WebServlet(name = "PaymentServlet", urlPatterns = {"/payments"})
public class PaymentServlet extends HttpServlet {
    private PaymentService paymentService;
    private Gson gson;

    @Override
    public void init() {
        this.paymentService = new PaymentService();
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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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

        try {
            if (!isStaff(currentUser)) {
                // Student queries own payments
                List<Payment> payments = paymentService.getPaymentsByStudent(currentUser.getUserId());
                Map<String, Object> respData = new HashMap<>();
                respData.put("success", true);
                respData.put("payments", payments);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(respData));
                return;
            }

            // Staff querying payments
            int page = 1;
            int pageSize = 20;
            String pageStr = req.getParameter("page");
            String limitStr = req.getParameter("limit");
            if (limitStr == null) limitStr = req.getParameter("pageSize");

            if (pageStr != null && !pageStr.trim().isEmpty()) {
                try { page = Math.max(1, Integer.parseInt(pageStr.trim())); } catch (NumberFormatException ignored) {}
            }
            if (limitStr != null && !limitStr.trim().isEmpty()) {
                try { pageSize = Math.min(100, Math.max(1, Integer.parseInt(limitStr.trim()))); } catch (NumberFormatException ignored) {}
            }

            String regNo = req.getParameter("registerNumber");
            if (regNo != null && regNo.trim().isEmpty()) regNo = null;

            String statusStr = req.getParameter("status");
            PaymentStatus statusFilter = null;
            if (statusStr != null && !statusStr.trim().isEmpty() && !statusStr.equalsIgnoreCase("ALL")) {
                try {
                    statusFilter = PaymentStatus.valueOf(statusStr.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }

            List<Payment> payments = paymentService.getAllPaymentsPaginated(regNo, statusFilter, page, pageSize);
            int totalCount = paymentService.countPayments(regNo, statusFilter);
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            Map<String, Object> respData = new HashMap<>();
            respData.put("success", true);
            respData.put("payments", payments);
            respData.put("totalCount", totalCount);
            respData.put("page", page);
            respData.put("pageSize", pageSize);
            respData.put("totalPages", totalPages);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(respData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error loading payments: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
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

        String action = null;
        Integer fineId = null;
        Integer paymentId = null;
        BigDecimal amount = null;
        String idempotencyKey = null;
        String reference = null;
        String reason = null;

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
                if (json.has("action")) action = json.get("action").getAsString();
                if (json.has("fineId")) fineId = json.get("fineId").getAsInt();
                if (json.has("paymentId")) paymentId = json.get("paymentId").getAsInt();
                if (json.has("amount") && !json.get("amount").isJsonNull()) {
                    amount = json.get("amount").getAsBigDecimal();
                }
                if (json.has("idempotencyKey")) idempotencyKey = json.get("idempotencyKey").getAsString();
                if (json.has("reference")) reference = json.get("reference").getAsString();
                if (json.has("reason")) reason = json.get("reason").getAsString();
                if (json.has("note")) reason = json.get("note").getAsString();
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Invalid JSON format: " + e.getMessage());
                resp.getWriter().write(gson.toJson(err));
                return;
            }
        } else {
            action = req.getParameter("action");
            String fStr = req.getParameter("fineId");
            if (fStr != null && !fStr.trim().isEmpty()) {
                try { fineId = Integer.parseInt(fStr.trim()); } catch (NumberFormatException ignored) {}
            }
            String pStr = req.getParameter("paymentId");
            if (pStr != null && !pStr.trim().isEmpty()) {
                try { paymentId = Integer.parseInt(pStr.trim()); } catch (NumberFormatException ignored) {}
            }
            String amtStr = req.getParameter("amount");
            if (amtStr != null && !amtStr.trim().isEmpty()) {
                try { amount = new BigDecimal(amtStr.trim()); } catch (NumberFormatException ignored) {}
            }
            idempotencyKey = req.getParameter("idempotencyKey");
            reference = req.getParameter("reference");
            reason = req.getParameter("reason");
            if (reason == null) reason = req.getParameter("note");
        }

        if (action == null || action.trim().isEmpty()) {
            action = "initiate"; // default action if fineId present
        }

        try {
            switch (action.toLowerCase()) {
                case "initiate": {
                    if (fineId == null || fineId <= 0) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "Fine ID is required to initiate payment.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    Map<String, Object> result = paymentService.initiatePayment(
                            currentUser.getUserId(), fineId, amount, idempotencyKey);

                    resp.setStatus(HttpServletResponse.SC_OK);
                    Map<String, Object> respData = new HashMap<>(result);
                    respData.put("success", true);
                    respData.put("message", "Payment initiated. Please pay via UPI and submit the 12-digit reference number.");
                    resp.getWriter().write(gson.toJson(respData));
                    break;
                }

                case "submit_reference":
                case "submitreference": {
                    if (paymentId == null || paymentId <= 0) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "Payment ID is required.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    if (reference == null || reference.trim().isEmpty()) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "UPI transaction reference ID (UTR) is required.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    Payment p = paymentService.submitReference(paymentId, currentUser.getUserId(), reference.trim());

                    resp.setStatus(HttpServletResponse.SC_OK);
                    Map<String, Object> respData = new HashMap<>();
                    respData.put("success", true);
                    respData.put("message", "UPI reference submitted successfully. Staff will verify it against the bank statement.");
                    respData.put("payment", p);
                    resp.getWriter().write(gson.toJson(respData));
                    break;
                }

                case "cancel": {
                    if (paymentId == null || paymentId <= 0) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "Payment ID is required.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    Payment p = paymentService.cancelPayment(paymentId, currentUser.getUserId());

                    resp.setStatus(HttpServletResponse.SC_OK);
                    Map<String, Object> respData = new HashMap<>();
                    respData.put("success", true);
                    respData.put("message", "Payment cancelled.");
                    respData.put("payment", p);
                    resp.getWriter().write(gson.toJson(respData));
                    break;
                }

                case "dispute": {
                    if (paymentId == null || paymentId <= 0) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "Payment ID is required.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }
                    if (reason == null || reason.trim().isEmpty()) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "Dispute reason is mandatory.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    Payment p = paymentService.raiseDispute(paymentId, currentUser.getUserId(), reason.trim());

                    resp.setStatus(HttpServletResponse.SC_OK);
                    Map<String, Object> respData = new HashMap<>();
                    respData.put("success", true);
                    respData.put("message", "Dispute submitted to Administrator for review.");
                    respData.put("payment", p);
                    resp.getWriter().write(gson.toJson(respData));
                    break;
                }

                default: {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    Map<String, Object> err = new HashMap<>();
                    err.put("success", false);
                    err.put("message", "Unknown action: " + action);
                    resp.getWriter().write(gson.toJson(err));
                    break;
                }
            }

        } catch (PaymentRateLimitExceededException e) {
            resp.setStatus(429); // 429 Too Many Requests
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (PaymentInProgressException | DuplicatePaymentReferenceException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (FineNotFoundException | PaymentNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (InvalidPaymentException | IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Payment processing error: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}

