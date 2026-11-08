package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.exception.RateLimitExceededException;
import com.psgitech.library.model.SearchSuggestion;
import com.psgitech.library.service.SmartDiscoveryService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for autocomplete suggestions with rate limiting (Module 10.2, Decision D11).
 * URL Mapping: GET /books/suggestions
 */
@WebServlet(name = "BookSuggestionServlet", urlPatterns = {"/books/suggestions"})
public class BookSuggestionServlet extends HttpServlet {
    private SmartDiscoveryService discoveryService;
    private Gson gson;

    @Override
    public void init() {
        this.discoveryService = new SmartDiscoveryService();
        this.gson = GsonProvider.getGson();
    }

    private String getClientIdentifier(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            return session.getId();
        }
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = req.getRemoteAddr();
        }
        return ip != null ? ip : "unknown-client";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");

        Map<String, Object> json = new HashMap<>();
        String q = req.getParameter("q");

        if (q != null && q.length() > 100) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", "Search query exceeds maximum length of 100 characters.");
            resp.getWriter().write(gson.toJson(json));
            return;
        }

        try {
            String clientId = getClientIdentifier(req);
            List<SearchSuggestion> suggestions;
            if (q == null || q.trim().length() < 2) {
                suggestions = Collections.emptyList();
            } else {
                suggestions = discoveryService.getSuggestions(q, clientId);
            }

            json.put("success", true);
            json.put("count", suggestions.size());
            json.put("suggestions", suggestions);
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (RateLimitExceededException e) {
            resp.setStatus(429); // HTTP 429 Too Many Requests
            json.put("success", false);
            json.put("message", e.getMessage());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal server error retrieving suggestions.");
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
