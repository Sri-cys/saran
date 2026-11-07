package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.InvalidPaymentException;
import com.psgitech.library.exception.PaymentNotFoundException;
import com.psgitech.library.model.User;
import com.psgitech.library.service.PaymentService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet providing printable fine payment receipts. (Decision D14).
 * URL Mapping: /receipt, /payments/receipt
 */
@WebServlet(name = "ReceiptServlet", urlPatterns = {"/receipt", "/payments/receipt"})
public class ReceiptServlet extends HttpServlet {
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

        String paymentIdStr = req.getParameter("paymentId");
        if (paymentIdStr == null || paymentIdStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Payment ID is required.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        int paymentId;
        try {
            paymentId = Integer.parseInt(paymentIdStr.trim());
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Invalid payment ID format.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        try {
            Map<String, Object> receiptData = paymentService.getReceiptData(
                    paymentId, currentUser.getUserId(), currentUser.getRole());

            Map<String, Object> respData = new HashMap<>();
            respData.put("success", true);
            respData.put("receipt", receiptData);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(respData));

        } catch (PaymentNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (InvalidPaymentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error retrieving receipt: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}

