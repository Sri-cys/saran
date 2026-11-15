package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.LoanPolicyChangeRecord;
import com.psgitech.library.model.PagedResult;
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
 * Returns paginated audit history of dynamic loan policy changes.
 * URL Mapping: GET /admin/loan-policy/history
 * Permissions: LIBRARIAN, ADMIN
 */
@WebServlet(name = "LoanPolicyHistoryServlet", urlPatterns = {"/admin/loan-policy/history"})
public class LoanPolicyHistoryServlet extends HttpServlet {

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
            int page = 1;
            int pageSize = 20;

            String p = req.getParameter("page");
            if (p != null && !p.trim().isEmpty()) {
                try { page = Integer.parseInt(p.trim()); } catch (NumberFormatException ignored) {}
            }

            String ps = req.getParameter("pageSize");
            if (ps != null && !ps.trim().isEmpty()) {
                try { pageSize = Integer.parseInt(ps.trim()); } catch (NumberFormatException ignored) {}
            }

            PagedResult<LoanPolicyChangeRecord> result = policyService.getChangeHistory(page, pageSize);

            json.put("success", true);
            json.put("data", result);
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Error loading loan policy change history: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
