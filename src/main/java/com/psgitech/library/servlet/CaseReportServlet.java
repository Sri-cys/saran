package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.BorrowTransactionNotFoundException;
import com.psgitech.library.exception.InvalidCaseStateException;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.LossDamageCase;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.service.LossDamageService;
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

@WebServlet(name = "CaseReportServlet", urlPatterns = {"/cases/report"})
public class CaseReportServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(CaseReportServlet.class.getName());

    private LossDamageService lossDamageService;
    private BorrowRepository borrowRepository;
    private Gson gson;

    @Override
    public void init() {
        this.lossDamageService = new LossDamageService();
        this.borrowRepository = new BorrowRepository();
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
            sendJson(resp, HttpServletResponse.SC_UNAUTHORIZED, false, "Authentication required. Please sign in.", null);
            return;
        }

        Integer transactionId = null;
        String note = null;

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
                if (sb.length() > 0) {
                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    if (json.has("transactionId") && !json.get("transactionId").isJsonNull()) {
                        transactionId = json.get("transactionId").getAsInt();
                    }
                    if (json.has("note") && !json.get("note").isJsonNull()) {
                        note = json.get("note").getAsString();
                    }
                }
            } catch (Exception e) {
                sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "Malformed JSON request body.", null);
                return;
            }
        } else {
            String txParam = req.getParameter("transactionId");
            if (txParam != null && !txParam.trim().isEmpty()) {
                try {
                    transactionId = Integer.parseInt(txParam.trim());
                } catch (NumberFormatException ignored) {}
            }
            note = req.getParameter("note");
        }

        if (transactionId == null || transactionId <= 0) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "Valid transactionId is required.", null);
            return;
        }

        try {
            BorrowTransaction tx = borrowRepository.findById(transactionId);
            if (tx == null) {
                sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "Borrowing transaction #" + transactionId + " not found.", null);
                return;
            }

            int studentId = tx.getStudentId();
            if (currentUser.getRole() == Role.STUDENT && currentUser.getUserId() != studentId) {
                sendJson(resp, HttpServletResponse.SC_FORBIDDEN, false, "Access Denied: You may only report loss for your own book loans.", null);
                return;
            }

            LossDamageCase result = lossDamageService.reportLost(
                    transactionId,
                    studentId,
                    note,
                    currentUser.getUserId(),
                    currentUser.getRole().name()
            );

            Map<String, Object> data = new HashMap<>();
            data.put("case", result);
            sendJson(resp, HttpServletResponse.SC_OK, true, "Book reported lost successfully. Staff assessment pending.", data);

        } catch (BorrowTransactionNotFoundException e) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, e.getMessage(), null);
        } catch (InvalidCaseStateException | IllegalStateException e) {
            sendJson(resp, HttpServletResponse.SC_CONFLICT, false, e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in CaseReportServlet", e);
            sendJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Failed to submit loss report: " + e.getMessage(), null);
        }
    }

    private void sendJson(HttpServletResponse resp, int status, boolean success, String message, Map<String, Object> extra) throws IOException {
        resp.setStatus(status);
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        map.put("message", message);
        if (extra != null) {
            map.putAll(extra);
        }
        resp.getWriter().write(gson.toJson(map));
    }
}
