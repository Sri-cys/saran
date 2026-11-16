package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.CaseNotFoundException;
import com.psgitech.library.exception.InvalidCaseStateException;
import com.psgitech.library.model.LossDamageCase;
import com.psgitech.library.model.User;
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

@WebServlet(name = "CaseChooseServlet", urlPatterns = {"/cases/choose"})
public class CaseChooseServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(CaseChooseServlet.class.getName());

    private LossDamageService lossDamageService;
    private Gson gson;

    @Override
    public void init() {
        this.lossDamageService = new LossDamageService();
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

        Integer caseId = null;
        String choice = null;

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
                    if (json.has("caseId") && !json.get("caseId").isJsonNull()) {
                        caseId = json.get("caseId").getAsInt();
                    }
                    if (json.has("choice") && !json.get("choice").isJsonNull()) {
                        choice = json.get("choice").getAsString();
                    }
                }
            } catch (Exception e) {
                sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "Malformed JSON request body.", null);
                return;
            }
        } else {
            String cParam = req.getParameter("caseId");
            if (cParam != null && !cParam.trim().isEmpty()) {
                try {
                    caseId = Integer.parseInt(cParam.trim());
                } catch (NumberFormatException ignored) {}
            }
            choice = req.getParameter("choice");
        }

        if (caseId == null || caseId <= 0) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "Valid caseId is required.", null);
            return;
        }

        if (choice == null || choice.trim().isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "Resolution choice ('PAYMENT' or 'REPLACEMENT') is required.", null);
            return;
        }

        String normalizedChoice = choice.trim().toUpperCase();
        if (!"PAYMENT".equals(normalizedChoice) && !"REPLACEMENT".equals(normalizedChoice)) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid choice: must be 'PAYMENT' or 'REPLACEMENT'.", null);
            return;
        }

        try {
            LossDamageCase existing = lossDamageService.getCaseById(caseId);
            if (existing == null) {
                sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "Case #" + caseId + " not found.", null);
                return;
            }

            if (currentUser.getRole() == Role.STUDENT && existing.getStudentId() != currentUser.getUserId()) {
                sendJson(resp, HttpServletResponse.SC_FORBIDDEN, false, "Access Denied: You may only select options for your own cases.", null);
                return;
            }

            LossDamageCase updated;
            if ("PAYMENT".equals(normalizedChoice)) {
                updated = lossDamageService.choosePayment(caseId, currentUser.getUserId());
            } else {
                updated = lossDamageService.chooseReplacement(caseId, currentUser.getUserId());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("case", updated);
            sendJson(resp, HttpServletResponse.SC_OK, true, "Resolution option '" + normalizedChoice + "' selected successfully.", data);

        } catch (CaseNotFoundException e) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, e.getMessage(), null);
        } catch (InvalidCaseStateException | IllegalStateException e) {
            sendJson(resp, HttpServletResponse.SC_CONFLICT, false, e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in CaseChooseServlet", e);
            sendJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Failed to update resolution choice: " + e.getMessage(), null);
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
