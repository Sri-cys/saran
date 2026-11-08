package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookRecommendation;
import com.psgitech.library.repository.RecommendationRepository;
import com.psgitech.library.service.BookRecommendationService;
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
 * REST endpoint for content-based similar books (Module 10.6).
 * URL Mapping: GET /books/similar?id={bookId}
 */
@WebServlet(name = "SimilarBooksServlet", urlPatterns = {"/books/similar"})
public class SimilarBooksServlet extends HttpServlet {
    private BookRecommendationService recommendationService;
    private RecommendationRepository recommendationRepository;
    private Gson gson;

    @Override
    public void init() {
        this.recommendationService = new BookRecommendationService();
        this.recommendationRepository = new RecommendationRepository();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        Map<String, Object> json = new HashMap<>();

        String idStr = req.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", "Target book ID parameter 'id' is required.");
            resp.getWriter().write(gson.toJson(json));
            return;
        }

        int bookId;
        try {
            bookId = Integer.parseInt(idStr.trim());
            if (bookId <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", "Book ID must be a valid positive integer.");
            resp.getWriter().write(gson.toJson(json));
            return;
        }

        // Verify book existence and active status
        Book target = recommendationRepository.findBookById(bookId);
        if (target == null || target.isArchived()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            json.put("success", false);
            json.put("message", "Book not found or is no longer available in the catalog.");
            resp.getWriter().write(gson.toJson(json));
            return;
        }

        int limit = 6;
        String limitStr = req.getParameter("limit");
        if (limitStr != null && !limitStr.trim().isEmpty()) {
            try {
                int parsed = Integer.parseInt(limitStr.trim());
                if (parsed > 0) {
                    limit = Math.min(parsed, 6);
                }
            } catch (NumberFormatException ignored) {}
        }

        try {
            List<BookRecommendation> similar = recommendationService.getSimilarBooks(bookId, limit);

            json.put("success", true);
            json.put("targetBookId", bookId);
            json.put("count", similar.size());
            json.put("books", similar);
            json.put("asOf", Instant.now().toString());
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal server error retrieving similar books.");
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
