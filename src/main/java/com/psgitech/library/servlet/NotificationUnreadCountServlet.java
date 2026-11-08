package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.User;
import com.psgitech.library.service.NotificationService;
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
 * Lightweight endpoint polled by the notification bell.
 * URL Mapping: GET /notifications/unread-count
 * Returns { "unreadCount": int, "latestId": long }
 */
@WebServlet(name = "NotificationUnreadCountServlet", urlPatterns = {"/notifications/unread-count"})
public class NotificationUnreadCountServlet extends HttpServlet {
    private NotificationService notificationService;
    private Gson gson;

    @Override
    public void init() {
        this.notificationService = new NotificationService();
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

        int unreadCount = notificationService.getUnreadCount(user.getUserId());
        Long latestId = notificationService.getLatestNotificationId(user.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("unreadCount", unreadCount);
        result.put("latestId", latestId != null ? latestId : 0L);

        resp.getWriter().write(gson.toJson(result));
    }
}
