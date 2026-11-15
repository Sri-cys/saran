package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.model.LoanPolicyWindow;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates an exam-season lower enter-threshold window (Stretch D12).
 * URL Mapping: POST /admin/loan-policy/windows/create
 * Permissions: ADMIN only
 */
@WebServlet(name = "LoanPolicyWindowCreateServlet", urlPatterns = {"/admin/loan-policy/windows/create"})
public class LoanPolicyWindowCreateServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LoanPolicyWindowCreateServlet.class.getName());

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

            if (!payload.has("name") || !payload.has("startDate") || !payload.has("endDate") || !payload.has("enterThresholdOverride")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", "Required fields: name, startDate (YYYY-MM-DD), endDate (YYYY-MM-DD), enterThresholdOverride.");
                resp.getWriter().write(gson.toJson(json));
                return;
            }

            String name = payload.get("name").getAsString().trim();
            String startStr = payload.get("startDate").getAsString().trim();
            String endStr = payload.get("endDate").getAsString().trim();
            int override = payload.get("enterThresholdOverride").getAsInt();

            if (name.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", "Window name cannot be empty.");
                resp.getWriter().write(gson.toJson(json));
                return;
            }

            LocalDate startDate;
            LocalDate endDate;
            try {
                startDate = LocalDate.parse(startStr);
                endDate = LocalDate.parse(endStr);
            } catch (DateTimeParseException ex) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", "Invalid date format. Expected YYYY-MM-DD.");
                resp.getWriter().write(gson.toJson(json));
                return;
            }

            if (startDate.isAfter(endDate)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", "Start date cannot be after end date.");
                resp.getWriter().write(gson.toJson(json));
                return;
            }

            if (override < 2) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", "Enter threshold override must be at least 2.");
                resp.getWriter().write(gson.toJson(json));
                return;
            }

            Integer adminId = (currentUser != null) ? currentUser.getUserId() : null;

            LoanPolicyWindow window = new LoanPolicyWindow();
            window.setName(name);
            window.setStartDate(startDate);
            window.setEndDate(endDate);
            window.setEnterThresholdOverride(override);
            window.setActive(true);
            window.setCreatedBy(adminId);
            window.setCreatedAt(new Timestamp(System.currentTimeMillis()));

            boolean created = policyService.createWindow(window);
            if (created) {
                resp.setStatus(HttpServletResponse.SC_CREATED);
                json.put("success", true);
                json.put("message", "Exam window created successfully.");
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                json.put("success", false);
                json.put("message", "Failed to save exam window to database.");
            }

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating exam window", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal server error: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
