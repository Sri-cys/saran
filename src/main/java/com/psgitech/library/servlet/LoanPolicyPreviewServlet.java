package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.LoanPolicySettings;
import com.psgitech.library.service.LoanPolicyService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Dry-run simulation preview for proposed policy threshold changes.
 * URL Mapping: GET /admin/loan-policy/preview?enter=&exit=&days=
 * Permissions: LIBRARIAN, ADMIN
 */
@WebServlet(name = "LoanPolicyPreviewServlet", urlPatterns = {"/admin/loan-policy/preview"})
public class LoanPolicyPreviewServlet extends HttpServlet {

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
            String enterStr = req.getParameter("enter");
            String exitStr = req.getParameter("exit");
            String daysStr = req.getParameter("days");

            int enter = (enterStr != null && !enterStr.trim().isEmpty()) ? Integer.parseInt(enterStr.trim()) : 4;
            int exit = (exitStr != null && !exitStr.trim().isEmpty()) ? Integer.parseInt(exitStr.trim()) : 1;
            int days = (daysStr != null && !daysStr.trim().isEmpty()) ? Integer.parseInt(daysStr.trim()) : 5;

            LoanPolicySettings.validate(enter, exit, days, 1);

            int affectedCount = policyService.previewProposedSettings(enter, exit, days);

            Map<String, Object> data = new HashMap<>();
            data.put("proposedEnterThreshold", enter);
            data.put("proposedExitThreshold", exit);
            data.put("proposedHighDemandDays", days);
            data.put("estimatedHighDemandBookCount", affectedCount);

            json.put("success", true);
            json.put("data", data);
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", e.getMessage());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal preview calculation error: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
