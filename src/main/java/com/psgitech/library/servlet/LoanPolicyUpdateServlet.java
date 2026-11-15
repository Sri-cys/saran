package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.exception.StaleVersionConflictException;
import com.psgitech.library.model.LoanPolicySettings;
import com.psgitech.library.model.User;
import com.psgitech.library.service.LoanPolicyService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Endpoint for updating loan policy settings with optimistic version verification.
 * URL Mapping: POST /admin/loan-policy/update
 * Permissions: ADMIN only
 */
@WebServlet(name = "LoanPolicyUpdateServlet", urlPatterns = {"/admin/loan-policy/update"})
public class LoanPolicyUpdateServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LoanPolicyUpdateServlet.class.getName());

    private LoanPolicyService policyService;
    private Gson gson;

    @Override
    public void init() {
        this.policyService = new LoanPolicyService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute("user") : null;

        Map<String, Object> json = new HashMap<>();

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            if (sb.length() == 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", "Request body is empty.");
                resp.getWriter().write(gson.toJson(json));
                return;
            }

            JsonObject payload = JsonParser.parseString(sb.toString()).getAsJsonObject();

            if (!payload.has("enterThreshold") || !payload.has("exitThreshold")
                    || !payload.has("highDemandDays") || !payload.has("expectedVersion")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", "Required fields: enterThreshold, exitThreshold, highDemandDays, expectedVersion.");
                resp.getWriter().write(gson.toJson(json));
                return;
            }

            boolean enabled = payload.has("enabled") ? payload.get("enabled").getAsBoolean() : true;
            int enterThreshold = payload.get("enterThreshold").getAsInt();
            int exitThreshold = payload.get("exitThreshold").getAsInt();
            int highDemandDays = payload.get("highDemandDays").getAsInt();
            int expectedVersion = payload.get("expectedVersion").getAsInt();

            Integer adminId = (currentUser != null) ? currentUser.getUserId() : null;
            String adminName = (currentUser != null) ? currentUser.getName() : "Administrator";

            LoanPolicySettings newSettings = new LoanPolicySettings(
                    enabled, enterThreshold, exitThreshold, highDemandDays,
                    expectedVersion + 1, adminId, new Timestamp(System.currentTimeMillis())
            );

            LoanPolicySettings updated = policyService.updateSettings(
                    newSettings, expectedVersion, adminId, adminName
            );

            json.put("success", true);
            json.put("message", "Loan policy configuration updated successfully.");
            json.put("data", updated);
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (StaleVersionConflictException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            json.put("success", false);
            json.put("message", e.getMessage());
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error updating loan policy", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal server error: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
