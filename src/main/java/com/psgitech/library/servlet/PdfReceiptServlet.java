package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.exception.InvalidPaymentException;
import com.psgitech.library.exception.PaymentNotFoundException;
import com.psgitech.library.model.User;
import com.psgitech.library.service.PaymentService;
import com.psgitech.library.service.PdfReceiptService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "PdfReceiptServlet", urlPatterns = {"/payments/receipt/pdf"})
public class PdfReceiptServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(PdfReceiptServlet.class.getName());

    private PaymentService paymentService;
    private PdfReceiptService pdfReceiptService;
    private Gson gson;

    @Override
    public void init() {
        this.paymentService = new PaymentService();
        this.pdfReceiptService = new PdfReceiptService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute("user") : null;
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json");
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required. Please sign in.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        String paymentIdStr = req.getParameter("paymentId");
        if (paymentIdStr == null || paymentIdStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
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
            resp.setContentType("application/json");
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Invalid payment ID format.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        try {
            Map<String, Object> receiptData = paymentService.getReceiptData(
                    paymentId, currentUser.getUserId(), currentUser.getRole()
            );

            byte[] pdfBytes = pdfReceiptService.generateReceiptPdfBytes(receiptData);

            String receiptNo = String.valueOf(receiptData.getOrDefault("receiptNumber", String.valueOf(paymentId)));
            String filename = "PSG_iTech_Receipt_" + receiptNo.replace('/', '_') + ".pdf";

            resp.setContentType("application/pdf");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            resp.setContentLength(pdfBytes.length);

            try (OutputStream out = resp.getOutputStream()) {
                out.write(pdfBytes);
                out.flush();
            }

        } catch (PaymentNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.setContentType("application/json");
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (InvalidPaymentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating receipt PDF for payment #" + paymentId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error generating receipt PDF: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}
