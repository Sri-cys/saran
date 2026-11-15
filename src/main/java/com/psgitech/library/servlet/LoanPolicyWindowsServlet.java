package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.LoanPolicyWindow;
import com.psgitech.library.service.LoanPolicyService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns list of configured exam-season threshold windows (Stretch D12).
 * URL Mapping: GET /admin/loan-policy/windows
 * Permissions: LIBRARIAN, ADMIN
 */
@WebServlet(name = "LoanPolicyWindowsServlet", urlPatterns = {"/admin/loan-policy/windows"})
public class LoanPolicyWindowsServlet extends HttpServlet {

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
            List<LoanPolicyWindow> windows = policyService.listWindows();
            json.put("success", true);
            json.put("data", windows);
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Error listing exam windows: " + e.getMessage());
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
