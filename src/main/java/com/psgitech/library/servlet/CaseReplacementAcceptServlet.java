package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.CaseNotFoundException;
import com.psgitech.library.exception.InvalidCaseStateException;
import com.psgitech.library.exception.IsbnMismatchException;
import com.psgitech.library.exception.ReplacementRejectedException;
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

@WebServlet(name = "CaseReplacementAcceptServlet", urlPatterns = {"/cases/replacement/accept"})
public class CaseReplacementAcceptServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(CaseReplacementAcceptServlet.class.getName());

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

        if (currentUser.getRole() != Role.LIBRARIAN && currentUser.getRole() != Role.ADMIN) {
            sendJson(resp, HttpServletResponse.SC_FORBIDDEN, false, "Access Denied: Staff role required.", null);
            return;
        }

        Integer caseId = null;
        String scannedIsbn = null;
        String condition = null;
        String notes = null;

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
                    if (json.has("scannedIsbn") && !json.get("scannedIsbn").isJsonNull()) {
                        scannedIsbn = json.get("scannedIsbn").getAsString();
                    }
                    if (json.has("condition") && !json.get("condition").isJsonNull()) {
                        condition = json.get("condition").getAsString();
                    }
                    if (json.has("notes") && !json.get("notes").isJsonNull()) {
                        notes = json.get("notes").getAsString();
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
            scannedIsbn = req.getParameter("scannedIsbn");
            condition = req.getParameter("condition");
            notes = req.getParameter("notes");
        }

        if (caseId == null || caseId <= 0) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "Valid caseId is required.", null);
            return;
        }

        if (scannedIsbn == null || scannedIsbn.trim().isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "Scanned ISBN is required for verification.", null);
            return;
        }

        if (condition == null || condition.trim().isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "Book condition ('NEW' or 'LIKE_NEW') is required.", null);
            return;
        }

        try {
            LossDamageCase resolved = lossDamageService.acceptReplacement(
                    caseId,
                    scannedIsbn,
                    condition,
                    notes,
                    currentUser.getUserId(),
                    currentUser.getRole().name()
            );

            Map<String, Object> data = new HashMap<>();
            data.put("case", resolved);
            data.put("replacementCopyId", resolved.getReplacementCopyId());
            sendJson(resp, HttpServletResponse.SC_OK, true, "Physical replacement accepted and registered into catalog. Case resolved.", data);

        } catch (CaseNotFoundException e) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, e.getMessage(), null);
        } catch (IsbnMismatchException | ReplacementRejectedException e) {
            sendJson(resp, 422, false, e.getMessage(), null);
        } catch (InvalidCaseStateException | IllegalStateException e) {
            sendJson(resp, HttpServletResponse.SC_CONFLICT, false, e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in CaseReplacementAcceptServlet", e);
            sendJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Failed to accept replacement: " + e.getMessage(), null);
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
