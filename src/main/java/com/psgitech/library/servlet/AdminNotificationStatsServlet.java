package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.NotificationOutboxRepository;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Operational telemetry endpoint for the notification outbox queue.
 * URL Mapping: GET /admin/notifications/stats, /api/admin/notifications/stats
 * Access: Administrator only.
 */
@WebServlet(name = "AdminNotificationStatsServlet", urlPatterns = {"/admin/notifications/stats", "/api/admin/notifications/stats"})
public class AdminNotificationStatsServlet extends HttpServlet {
    private NotificationOutboxRepository outboxRepo;
    private Gson gson;

    @Override
    public void init() {
        this.outboxRepo = new NotificationOutboxRepository();
        this.gson = GsonProvider.getGson();
    }

    private User getAuthenticatedUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return (session != null) ? (User) session.getAttribute("user") : null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");

        User user = getAuthenticatedUser(req);
        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        if (user.getRole() != Role.ADMIN) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access Denied: Administrator role required");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        Map<String, Object> stats = outboxRepo.getAdminStats();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("stats", stats);

        resp.getWriter().write(gson.toJson(result));
    }
}

