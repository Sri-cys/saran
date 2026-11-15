package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.policy.LoanPolicy;
import com.psgitech.library.policy.LoanPolicyManager;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public/authenticated read-only endpoint returning batched effective loan policies for catalogue cards.
 * URL Mapping: GET /books/policy?ids=1,2,3
 */
@WebServlet(name = "BookPolicyServlet", urlPatterns = {"/books/policy"})
public class BookPolicyServlet extends HttpServlet {

    private LoanPolicyManager policyManager;
    private Gson gson;

    @Override
    public void init() {
        this.policyManager = LoanPolicyManager.getInstance();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        Map<String, Object> json = new HashMap<>();

        String idsParam = req.getParameter("ids");
        if (idsParam == null || idsParam.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", "Parameter 'ids' (comma-separated book IDs) is required.");
            resp.getWriter().write(gson.toJson(json));
            return;
        }

        String[] parts = idsParam.split(",");
        List<Integer> bookIds = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                try {
                    int id = Integer.parseInt(trimmed);
                    if (id > 0 && !bookIds.contains(id)) {
                        bookIds.add(id);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (bookIds.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", "No valid book IDs provided in 'ids' parameter.");
            resp.getWriter().write(gson.toJson(json));
            return;
        }

        if (bookIds.size() > 50) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", "Maximum of 50 book IDs allowed per batch request.");
            resp.getWriter().write(gson.toJson(json));
            return;
        }

        Map<Integer, LoanPolicy> policies = policyManager.effectivePolicies(bookIds);
        Map<String, Object> data = new HashMap<>();

        for (Map.Entry<Integer, LoanPolicy> entry : policies.entrySet()) {
            LoanPolicy lp = entry.getValue();
            Map<String, Object> item = new HashMap<>();
            item.put("code", lp.getCode());
            item.put("label", lp.getLabel());
            item.put("loanDays", lp.getLoanDays());
            item.put("canRenew", lp.canRenew());
            item.put("isHighDemand", lp.isHighDemand());
            data.put(String.valueOf(entry.getKey()), item);
        }

        json.put("success", true);
        json.put("data", data);
        json.put("asOf", Instant.now().toString());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(json));
    }
}
