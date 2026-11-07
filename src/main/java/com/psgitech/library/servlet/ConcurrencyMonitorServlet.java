package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.concurrency.ConcurrencyMonitorService;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.User;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-only API endpoint for retrieving real-time concurrent request metrics.
 * Endpoint: GET /api/admin/concurrency/status and GET /admin/concurrency/status
 */
@WebServlet(name = "ConcurrencyMonitorServlet", urlPatterns = {"/api/admin/concurrency/status", "/admin/concurrency/status"})
public class ConcurrencyMonitorServlet extends HttpServlet {

    private ConcurrencyMonitorService monitorService;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        this.monitorService = new ConcurrencyMonitorService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("user") : null;

        if (user == null) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return;
        }

        if (user.getRole() != Role.ADMIN) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Administrator role required.");
            return;
        }

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        resp.setHeader("Pragma", "no-cache");

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("success", true);
        responseData.put("message", "Concurrency metrics retrieved successfully");
        responseData.put("data", monitorService.getStatusSnapshot());

        resp.getWriter().write(gson.toJson(responseData));
    }
}

