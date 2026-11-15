package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.service.LoanPolicyService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deactivates an exam-season threshold window.
 * URL Mapping: POST /admin/loan-policy/windows/deactivate
 * Permissions: ADMIN only
 */
@WebServlet(name = "LoanPolicyWindowDeactivateServlet", urlPatterns = {"/admin/loan-policy/windows/deactivate"})
public class LoanPolicyWindowDeactivateServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LoanPolicyWindowDeactivateServlet.class.getName());

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

        Map<String, Object> json = new HashMap<>();

        try {
            int windowId = -1;

            String windowIdParam = req.getParameter("windowId");
            if (windowIdParam != null && !windowIdParam.trim().isEmpty()) {
                windowId = Integer.parseInt(windowIdParam.trim());
            } else {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                if (sb.length() > 0) {
                    JsonObject payload = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    if (payload.has("windowId")) {
                        windowId = payload.get("windowId").getAsInt();
                    }
                }
            }

            if (windowId <= 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", "Valid windowId is required.");
                resp.getWriter().write(gson.toJson(json));
                return;
            }

            boolean success = policyService.deactivateWindow(windowId);
            if (success) {
                resp.setStatus(HttpServletResponse.SC_OK);
                json.put("success", true);
                json.put("message", "Exam window deactivated successfully.");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                json.put("success", false);
                json.put("message", "Window not found or already inactive.");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deactivating exam window", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal server error: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
