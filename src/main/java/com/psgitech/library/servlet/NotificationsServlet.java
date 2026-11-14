package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.model.Notification;
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
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for in-app notifications.
 * URL Mapping: GET /notifications
 * Supports keyset pagination (cursor), category filtering, and unread-only filtering.
 */
@WebServlet(name = "NotificationsServlet", urlPatterns = {"/notifications"})
public class NotificationsServlet extends HttpServlet {
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

        String catParam = req.getParameter("category");
        NotificationCategory category = null;
        if (catParam != null && !catParam.trim().isEmpty() && !"ALL".equalsIgnoreCase(catParam.trim())) {
            category = NotificationCategory.fromString(catParam);
        }

        String unreadOnlyParam = req.getParameter("unreadOnly");
        Boolean unreadOnly = (unreadOnlyParam != null && "true".equalsIgnoreCase(unreadOnlyParam.trim())) ? Boolean.TRUE : null;

        Long cursor = null;
        String cursorParam = req.getParameter("cursor");
        if (cursorParam != null && !cursorParam.trim().isEmpty()) {
            try {
                cursor = Long.parseLong(cursorParam.trim());
            } catch (NumberFormatException ignored) {}
        }

        int limit = 20;
        String limitParam = req.getParameter("limit");
        if (limitParam != null && !limitParam.trim().isEmpty()) {
            try {
                int parsed = Integer.parseInt(limitParam.trim());
                if (parsed > 0 && parsed <= 50) {
                    limit = parsed;
                }
            } catch (NumberFormatException ignored) {}
        }

        // Fetch limit + 1 to detect hasMore
        List<Notification> list = notificationService.getNotifications(user.getUserId(), category, unreadOnly, cursor, limit + 1);

        boolean hasMore = list.size() > limit;
        if (hasMore) {
            list = list.subList(0, limit);
        }

        Long nextCursor = null;
        if (!list.isEmpty() && hasMore) {
            nextCursor = list.get(list.size() - 1).getNotificationId();
        }

        int unreadCount = notificationService.getUnreadCount(user.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("notifications", list);
        result.put("hasMore", hasMore);
        result.put("nextCursor", nextCursor);
        result.put("unreadCount", unreadCount);

        resp.getWriter().write(gson.toJson(result));
    }
}

