package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.FineNotFoundException;
import com.psgitech.library.exception.InvalidFineOperationException;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.model.User;
import com.psgitech.library.service.FineService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet handling Staff Fine Management (search, filter, pagination)
 * and Administrative Fine Waivers.
 * URL Mapping: /fines
 */
@WebServlet(name = "FineServlet", urlPatterns = {"/fines"})
public class FineServlet extends HttpServlet {
    private FineService fineService;
    private Gson gson;

    @Override
    public void init() {
        this.fineService = new FineService();
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
            String fineIdParam = req.getParameter("fineId");
            if (fineIdParam != null && !fineIdParam.trim().isEmpty()) {
                int fineId = Integer.parseInt(fineIdParam.trim());
                FineRecord fine = fineService.getFineById(fineId);
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("success", true);
                responseData.put("fine", fine);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(responseData));
                return;
            }

            int page = 1;
            int pageSize = 20;
            String pageStr = req.getParameter("page");
            String limitStr = req.getParameter("limit");
            if (limitStr == null) limitStr = req.getParameter("pageSize");

            if (pageStr != null && !pageStr.trim().isEmpty()) {
                try { page = Math.max(1, Integer.parseInt(pageStr.trim())); } catch (NumberFormatException ignored) {}
            }
            if (limitStr != null && !limitStr.trim().isEmpty()) {
                try { pageSize = Math.min(100, Math.max(1, Integer.parseInt(limitStr.trim()))); } catch (NumberFormatException ignored) {}
            }

            String regNo = req.getParameter("registerNumber");
            if (regNo != null && regNo.trim().isEmpty()) regNo = null;

            String statusStr = req.getParameter("status");
            FineStatus statusFilter = null;
            if (statusStr != null && !statusStr.trim().isEmpty() && !statusStr.equalsIgnoreCase("ALL")) {
                try {
                    statusFilter = FineStatus.valueOf(statusStr.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }

            List<FineRecord> fines = fineService.getFinesPaginated(regNo, statusFilter, page, pageSize);
            int totalCount = fineService.countFines(regNo, statusFilter);
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("fines", fines);
            responseData.put("totalCount", totalCount);
            responseData.put("page", page);
            responseData.put("pageSize", pageSize);
            responseData.put("totalPages", totalPages);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (FineNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error querying fines: " + e.getMessage());
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

        // Decision D7: Admin-only waivers
        if (currentUser.getRole() != Role.ADMIN) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access denied. Only Administrators can grant fine waivers.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        Integer fineId = null;
        BigDecimal amount = null;
        String reason = null;

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
                if (json.has("fineId")) fineId = json.get("fineId").getAsInt();
                if (json.has("amount")) amount = json.get("amount").getAsBigDecimal();
                if (json.has("reason")) reason = json.get("reason").getAsString();
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Invalid JSON format: " + e.getMessage());
                resp.getWriter().write(gson.toJson(err));
                return;
            }
        } else {
            String fStr = req.getParameter("fineId");
            if (fStr != null && !fStr.trim().isEmpty()) {
                try { fineId = Integer.parseInt(fStr.trim()); } catch (NumberFormatException ignored) {}
            }
            String amtStr = req.getParameter("amount");
            if (amtStr != null && !amtStr.trim().isEmpty()) {
                try { amount = new BigDecimal(amtStr.trim()); } catch (NumberFormatException ignored) {}
            }
            reason = req.getParameter("reason");
        }

        if (fineId == null || fineId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Valid fine ID is required.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Waiver amount must be greater than zero.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        if (reason == null || reason.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Waiver reason is mandatory.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        try {
            FineRecord updatedFine = fineService.applyWaiver(fineId, amount, reason.trim(),
                    currentUser.getUserId(), currentUser.getRole());

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Fine waiver of INR " + amount.toPlainString() + " applied successfully.");
            responseData.put("fine", updatedFine);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (FineNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        } catch (InvalidFineOperationException | IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "An unexpected error occurred while processing waiver: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}

