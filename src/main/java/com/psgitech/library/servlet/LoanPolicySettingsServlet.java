package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.model.LoanPolicySettings;
import com.psgitech.library.service.LoanPolicyService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint returning active loan policy configuration, version, and defaults.
 * URL Mapping: GET /admin/loan-policy
 * Permissions: LIBRARIAN, ADMIN
 */
@WebServlet(name = "LoanPolicySettingsServlet", urlPatterns = {"/admin/loan-policy"})
public class LoanPolicySettingsServlet extends HttpServlet {

    private LoanPolicyService policyService;
    private Gson gson;

    @Override
    public void init() {
        this.policyService = new LoanPolicyService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");

        Map<String, Object> json = new HashMap<>();

        try {
            LoanPolicySettings settings = policyService.getSettings();
            Map<String, Object> data = new HashMap<>();
            data.put("enabled", settings.isEnabled());
            data.put("enterThreshold", settings.getEnterThreshold());
            data.put("exitThreshold", settings.getExitThreshold());
            data.put("highDemandDays", settings.getHighDemandDays());
            data.put("version", settings.getVersion());
            data.put("updatedBy", settings.getUpdatedBy());
            data.put("updatedAt", settings.getUpdatedAt() != null ? settings.getUpdatedAt().toString() : null);
            data.put("standardLoanDays", BorrowingPolicy.DEFAULT_BORROWING_DAYS);

            json.put("success", true);
            json.put("message", "Loan policy settings retrieved successfully.");
            json.put("data", data);
            json.put("asOf", Instant.now().toString());
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal error retrieving loan policy settings: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
