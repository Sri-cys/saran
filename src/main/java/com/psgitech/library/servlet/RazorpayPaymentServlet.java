package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.config.RazorpayConfig;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.Payment;
import com.psgitech.library.model.User;
import com.psgitech.library.service.RazorpayPaymentService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet managing the direct Razorpay payment flow:
 * - /payments/razorpay (GET): Fetch public config (Key ID, Currency)
 * - /payments/razorpay/create-order (POST): Create individual or Pay All Razorpay order
 * - /payments/razorpay/verify (POST): Cryptographically verify checkout signature and settle fines
 * - /payments/razorpay/cancel (POST): Mark cancelled upon checkout modal dismissal
 * - /payments/razorpay/failed (POST): Mark failed upon checkout error
 */
@WebServlet(name = "RazorpayPaymentServlet", urlPatterns = {"/payments/razorpay", "/payments/razorpay/*"})
public class RazorpayPaymentServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(RazorpayPaymentServlet.class.getName());

    private RazorpayPaymentService razorpayPaymentService;
    private Gson gson;

    @Override
    public void init() {
        this.razorpayPaymentService = new RazorpayPaymentService();
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

        User user = getAuthenticatedUser(req);
        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(gson.toJson(buildErrorResponse("Authentication required. Please log in.")));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("keyId", RazorpayConfig.KEY_ID);
        data.put("currency", RazorpayConfig.CURRENCY);
        data.put("mockGateway", RazorpayConfig.isMockGatewayEnabled());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(data));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User user = getAuthenticatedUser(req);
        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(gson.toJson(buildErrorResponse("Authentication required. Please log in.")));
            return;
        }

        String pathInfo = req.getPathInfo();
        String action = req.getParameter("action");

        // Parse JSON payload if present
        JsonObject jsonBody = null;
        try (BufferedReader reader = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            if (sb.length() > 0) {
                JsonElement elem = JsonParser.parseString(sb.toString());
                if (elem.isJsonObject()) {
                    jsonBody = elem.getAsJsonObject();
                }
            }
        } catch (Exception ignored) {}

        if (action == null && jsonBody != null && jsonBody.has("action")) {
            action = jsonBody.get("action").getAsString();
        }

        String effectiveAction = "unknown";
        if (pathInfo != null) {
            String p = pathInfo.toLowerCase();
            if (p.contains("create-order") || p.contains("order")) {
                effectiveAction = "create_order";
            } else if (p.contains("verify")) {
                effectiveAction = "verify";
            } else if (p.contains("cancel")) {
                effectiveAction = "cancel";
            } else if (p.contains("failed") || p.contains("fail")) {
                effectiveAction = "failed";
            }
        }
        if ("unknown".equals(effectiveAction) && action != null) {
            effectiveAction = action.toLowerCase();
        }

        try {
            switch (effectiveAction) {
                case "create_order":
                case "create-order":
                case "order":
                    handleCreateOrder(user, jsonBody, req, resp);
                    break;

                case "verify":
                    handleVerifyPayment(user, jsonBody, req, resp);
                    break;

                case "cancel":
                    handleCancelPayment(user, jsonBody, req, resp);
                    break;

                case "failed":
                case "fail":
                    handleFailedPayment(user, jsonBody, req, resp);
                    break;

                default:
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write(gson.toJson(buildErrorResponse("Invalid or unsupported payment action: " + effectiveAction)));
                    break;
            }

        } catch (FineNotFoundException | PaymentNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(buildErrorResponse(e.getMessage())));

        } catch (PaymentInProgressException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(gson.toJson(buildErrorResponse(e.getMessage())));

        } catch (InvalidPaymentException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("access denied")) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            resp.getWriter().write(gson.toJson(buildErrorResponse(e.getMessage())));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in RazorpayPaymentServlet", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(buildErrorResponse("Internal server error: " + e.getMessage())));
        }
    }

    private void handleCreateOrder(User user, JsonObject jsonBody, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String type = getStringVal(jsonBody, req, "type", "INDIVIDUAL");
        String fineIdStr = getStringVal(jsonBody, req, "fineId", null);

        if ("BULK".equalsIgnoreCase(type) || "PAY_ALL".equalsIgnoreCase(type) || fineIdStr == null || fineIdStr.trim().isEmpty()) {
            // Bulk order ("Pay All")
            List<Integer> fineIds = new ArrayList<>();
            if (jsonBody != null && jsonBody.has("fineIds") && jsonBody.get("fineIds").isJsonArray()) {
                JsonArray arr = jsonBody.getAsJsonArray("fineIds");
                for (JsonElement e : arr) {
                    try { fineIds.add(e.getAsInt()); } catch (Exception ignored) {}
                }
            }
            Map<String, Object> orderData = razorpayPaymentService.createPayAllOrder(user.getUserId(), fineIds);
            orderData.put("success", true);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(orderData));

        } else {
            // Individual fine order
            int fineId;
            try {
                fineId = Integer.parseInt(fineIdStr.trim());
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(gson.toJson(buildErrorResponse("Invalid fineId format.")));
                return;
            }

            Map<String, Object> orderData = razorpayPaymentService.createIndividualOrder(user.getUserId(), fineId);
            orderData.put("success", true);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(orderData));
        }
    }

    private void handleVerifyPayment(User user, JsonObject jsonBody, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String orderId = getStringVal(jsonBody, req, "razorpay_order_id", null);
        if (orderId == null) orderId = getStringVal(jsonBody, req, "orderId", null);

        String paymentId = getStringVal(jsonBody, req, "razorpay_payment_id", null);
        if (paymentId == null) paymentId = getStringVal(jsonBody, req, "paymentId", null);

        String signature = getStringVal(jsonBody, req, "razorpay_signature", null);
        if (signature == null) signature = getStringVal(jsonBody, req, "signature", null);

        if (orderId == null || paymentId == null || signature == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(buildErrorResponse("Missing Razorpay verification parameters: orderId, paymentId, and signature are required.")));
            return;
        }

        Payment payment = razorpayPaymentService.verifyAndCapturePayment(user.getUserId(), orderId, paymentId, signature);

        Map<String, Object> respData = new HashMap<>();
        respData.put("success", true);
        respData.put("message", "Payment verified and recorded successfully.");
        respData.put("paymentId", payment.getPaymentId());
        respData.put("receiptNumber", payment.getReceiptNumber());
        respData.put("referenceId", payment.getReferenceId());
        respData.put("amount", payment.getAmount());
        respData.put("status", payment.getStatus().name());
        respData.put("paymentType", payment.getPaymentType());
        respData.put("settledFineIds", payment.getSettledFineIds());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(respData));
    }

    private void handleCancelPayment(User user, JsonObject jsonBody, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String orderId = getStringVal(jsonBody, req, "orderId", null);
        if (orderId == null) orderId = getStringVal(jsonBody, req, "razorpay_order_id", null);
        String reason = getStringVal(jsonBody, req, "reason", "Student dismissed payment checkout.");

        if (orderId != null) {
            razorpayPaymentService.recordCancelledPayment(user.getUserId(), orderId, reason);
        }

        Map<String, Object> respData = new HashMap<>();
        respData.put("success", true);
        respData.put("message", "Payment cancellation acknowledged.");
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(respData));
    }

    private void handleFailedPayment(User user, JsonObject jsonBody, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String orderId = getStringVal(jsonBody, req, "orderId", null);
        if (orderId == null) orderId = getStringVal(jsonBody, req, "razorpay_order_id", null);
        String reason = getStringVal(jsonBody, req, "reason", "Payment failed during checkout.");

        if (orderId != null) {
            razorpayPaymentService.recordFailedPayment(user.getUserId(), orderId, reason);
        }

        Map<String, Object> respData = new HashMap<>();
        respData.put("success", true);
        respData.put("message", "Payment failure recorded.");
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(respData));
    }

    private String getStringVal(JsonObject jsonBody, HttpServletRequest req, String key, String defaultVal) {
        if (jsonBody != null && jsonBody.has(key) && !jsonBody.get(key).isJsonNull()) {
            return jsonBody.get(key).getAsString();
        }
        String p = req.getParameter(key);
        return (p != null && !p.trim().isEmpty()) ? p.trim() : defaultVal;
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("message", message);
        return err;
    }
}
