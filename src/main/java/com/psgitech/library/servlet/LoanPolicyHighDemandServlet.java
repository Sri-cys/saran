package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.PagedResult;
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
 * Returns paginated list of catalog titles currently subject to high-demand policy.
 * URL Mapping: GET /admin/loan-policy/high-demand
 * Permissions: LIBRARIAN, ADMIN
 */
@WebServlet(name = "LoanPolicyHighDemandServlet", urlPatterns = {"/admin/loan-policy/high-demand"})
public class LoanPolicyHighDemandServlet extends HttpServlet {

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

            PagedResult<Map<String, Object>> result = policyService.getHighDemandBooks(page, pageSize);

            json.put("success", true);
            json.put("data", result);
            json.put("asOf", Instant.now().toString());
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Error loading high-demand books: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
