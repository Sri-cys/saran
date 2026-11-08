package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.BookRecommendation;
import com.psgitech.library.service.BookPopularityService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for campus popular and trending books (Module 10.4, Decision D4 & D5).
 * URL Mapping: GET /books/popular?type=popular|trending
 */
@WebServlet(name = "PopularBooksServlet", urlPatterns = {"/books/popular"})
public class PopularBooksServlet extends HttpServlet {
    private BookPopularityService popularityService;
    private Gson gson;

    @Override
    public void init() {
        this.popularityService = new BookPopularityService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        Map<String, Object> json = new HashMap<>();

        String type = req.getParameter("type");
        if (type == null || type.trim().isEmpty()) {
            type = "popular";
        } else {
            type = type.trim().toLowerCase();
            if (!type.equals("popular") && !type.equals("trending")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", "Invalid popularity type '" + type + "'. Must be 'popular' or 'trending'.");
                resp.getWriter().write(gson.toJson(json));
                return;
            }
        }

        int limit = 10;
        String limitStr = req.getParameter("limit");
        if (limitStr != null && !limitStr.trim().isEmpty()) {
            try {
                int parsed = Integer.parseInt(limitStr.trim());
                if (parsed <= 0) {
                    throw new IllegalArgumentException("Limit must be a positive integer.");
                }
                limit = Math.min(parsed, 10);
            } catch (IllegalArgumentException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                json.put("success", false);
                json.put("message", e.getMessage());
                resp.getWriter().write(gson.toJson(json));
                return;
            }
        }

        try {
            List<BookRecommendation> books = "trending".equals(type)
                    ? popularityService.getTrendingBooks(limit)
                    : popularityService.getPopularBooks(limit);

            json.put("success", true);
            json.put("type", type);
            json.put("count", books.size());
            json.put("books", books);
            json.put("asOf", Instant.now().toString());
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal server error retrieving popular books.");
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
