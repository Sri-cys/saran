package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.model.NotificationPreference;
import com.psgitech.library.model.User;
import com.psgitech.library.service.NotificationPreferenceService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for querying and updating user notification channel preferences.
 * URL Mapping: GET /notifications/preferences, POST /notifications/preferences
 */
@WebServlet(name = "NotificationPreferencesServlet", urlPatterns = {"/notifications/preferences"})
public class NotificationPreferencesServlet extends HttpServlet {
    private NotificationPreferenceService preferenceService;
    private Gson gson;

    @Override
    public void init() {
        this.preferenceService = new NotificationPreferenceService();
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

        List<NotificationPreference> preferences = preferenceService.getUserPreferences(user.getUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("preferences", preferences);

        resp.getWriter().write(gson.toJson(result));
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

        List<NotificationPreference> updates = new ArrayList<>();
        try (BufferedReader reader = req.getReader()) {
            JsonElement root = JsonParser.parseReader(reader);
            JsonArray array = null;
            if (root.isJsonObject() && root.getAsJsonObject().has("preferences")) {
                array = root.getAsJsonObject().getAsJsonArray("preferences");
            } else if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            }

            if (array != null) {
                for (JsonElement elem : array) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();
                    if (!obj.has("category")) continue;

                    String catStr = obj.get("category").getAsString();
                    NotificationCategory cat = NotificationCategory.fromString(catStr);
                    if (cat == null) continue;

                    boolean inApp = obj.has("inAppEnabled") && obj.get("inAppEnabled").getAsBoolean();
                    boolean email = obj.has("emailEnabled") && obj.get("emailEnabled").getAsBoolean();

                    updates.add(new NotificationPreference(user.getUserId(), cat, inApp, email));
                }
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Invalid JSON payload: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        preferenceService.updateUserPreferences(user.getUserId(), updates);
        List<NotificationPreference> latest = preferenceService.getUserPreferences(user.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("preferences", latest);

        resp.getWriter().write(gson.toJson(result));
    }
}
