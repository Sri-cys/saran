package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.model.User;
import com.psgitech.library.service.NotificationService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint for marking all notifications up to a given ID as read.
 * URL Mapping: POST /notifications/read-all
 */
@WebServlet(name = "NotificationReadAllServlet", urlPatterns = {"/notifications/read-all"})
public class NotificationReadAllServlet extends HttpServlet {
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
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User user = getAuthenticatedUser(req);
        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        long upToId = 0L;
        String contentType = req.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            try (BufferedReader reader = req.getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("upToId")) {
                    upToId = json.get("upToId").getAsLong();
                }
            } catch (Exception ignored) {}
        } else {
            String param = req.getParameter("upToId");
            if (param != null) {
                try {
                    upToId = Long.parseLong(param.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        int updatedCount = notificationService.markAllAsRead(user.getUserId(), upToId);
        int unreadCount = notificationService.getUnreadCount(user.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("updatedCount", updatedCount);
        result.put("unreadCount", unreadCount);

        resp.getWriter().write(gson.toJson(result));
    }
}
