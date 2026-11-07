package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.InvalidPaymentException;
import com.psgitech.library.exception.PaymentNotFoundException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet handling Staff UPI Payment Verifications, Rejections,
 * Administrative Dispute Resolution, and Manual Bank Refunds.
 * URL Mapping: /payments/verify
 */
@WebServlet(name = "PaymentVerificationServlet", urlPatterns = {"/payments/verify"})
public class PaymentVerificationServlet extends HttpServlet {
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

        if (!isStaff(currentUser)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access denied. Librarian or Administrator role required.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        try {
            List<Payment> pending = paymentService.getPendingVerifications();
            Map<String, Object> respData = new HashMap<>();
            respData.put("success", true);
            respData.put("pendingVerifications", pending);
            respData.put("count", pending.size());

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(respData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error loading pending verifications: " + e.getMessage());
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

        if (!isStaff(currentUser)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access denied. Librarian or Administrator role required.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        String action = null;
        Integer paymentId = null;
        String reason = null;
        Boolean reopen = false;

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
                if (json.has("paymentId")) paymentId = json.get("paymentId").getAsInt();
                if (json.has("reason")) reason = json.get("reason").getAsString();
                if (json.has("notes")) reason = json.get("notes").getAsString();
                if (json.has("reopen")) reopen = json.get("reopen").getAsBoolean();
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
            String pStr = req.getParameter("paymentId");
            if (pStr != null && !pStr.trim().isEmpty()) {
                try { paymentId = Integer.parseInt(pStr.trim()); } catch (NumberFormatException ignored) {}
            }
            reason = req.getParameter("reason");
            if (reason == null) reason = req.getParameter("notes");
            String roStr = req.getParameter("reopen");
            if (roStr != null) {
                reopen = Boolean.parseBoolean(roStr);
            }
        }

        if (paymentId == null || paymentId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Valid payment ID is required.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        if (action == null || action.trim().isEmpty()) {
            action = "verify";
        }

        try {
            switch (action.toLowerCase()) {
                case "verify": {
                    Payment p = paymentService.verifyPayment(paymentId, currentUser.getUserId(), currentUser.getRole());

                    resp.setStatus(HttpServletResponse.SC_OK);
                    Map<String, Object> respData = new HashMap<>();
                    respData.put("success", true);
                    respData.put("message", "Payment verified successfully. Receipt: " + p.getReceiptNumber());
                    respData.put("payment", p);
                    resp.getWriter().write(gson.toJson(respData));
                    break;
                }

                case "reject": {
                    if (reason == null || reason.trim().isEmpty()) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "Rejection reason is mandatory.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    Payment p = paymentService.rejectPayment(paymentId, currentUser.getUserId(), currentUser.getRole(), reason.trim());

                    resp.setStatus(HttpServletResponse.SC_OK);
                    Map<String, Object> respData = new HashMap<>();
                    respData.put("success", true);
                    respData.put("message", "Payment rejected. Reason: " + reason.trim());
                    respData.put("payment", p);
                    resp.getWriter().write(gson.toJson(respData));
                    break;
                }

                case "resolve_dispute":
                case "resolvedispute": {
                    if (currentUser.getRole() != Role.ADMIN) {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "Access denied. Only Administrators can resolve payment disputes.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    Payment p = paymentService.resolveDispute(paymentId, currentUser.getUserId(),
                            currentUser.getRole(), Boolean.TRUE.equals(reopen), reason);

                    resp.setStatus(HttpServletResponse.SC_OK);
                    Map<String, Object> respData = new HashMap<>();
                    respData.put("success", true);
                    respData.put("message", reopen ? "Dispute accepted. Payment returned to pending verification queue." : "Dispute reviewed and closed.");
                    respData.put("payment", p);
                    resp.getWriter().write(gson.toJson(respData));
                    break;
                }

                case "refund": {
                    if (currentUser.getRole() != Role.ADMIN) {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "Access denied. Only Administrators can record refunds.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    if (reason == null || reason.trim().isEmpty()) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        Map<String, Object> err = new HashMap<>();
                        err.put("success", false);
                        err.put("message", "Refund reason is mandatory.");
                        resp.getWriter().write(gson.toJson(err));
                        return;
                    }

                    Payment p = paymentService.recordRefund(paymentId, currentUser.getUserId(), currentUser.getRole(), reason.trim());

                    resp.setStatus(HttpServletResponse.SC_OK);
                    Map<String, Object> respData = new HashMap<>();
                    respData.put("success", true);
                    respData.put("message", "Refund recorded and fine balance restored.");
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

        } catch (PaymentNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (InvalidPaymentException | IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Verification processing error: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}

