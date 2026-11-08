package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.BookRecommendation;
import com.psgitech.library.model.User;
import com.psgitech.library.service.BookRecommendationService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for personalized student book recommendations (Module 10.5, Decisions D6, D8, D15).
 * URL Mapping: GET /books/recommendations
 * Security: STUDENT ONLY (Staff get 403, Anonymous get 401).
 */
@WebServlet(name = "PersonalizedRecommendationsServlet", urlPatterns = {"/books/recommendations"})
public class PersonalizedRecommendationsServlet extends HttpServlet {
    private BookRecommendationService recommendationService;
    private Gson gson;

    @Override
    public void init() {
        this.recommendationService = new BookRecommendationService();
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

        Map<String, Object> json = new HashMap<>();
        User currentUser = getAuthenticatedUser(req);

        // Security check: Must be authenticated
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            json.put("success", false);
            json.put("message", "Authentication required to view personalized recommendations.");
            resp.getWriter().write(gson.toJson(json));
            return;
        }

        // Security check: Student role only (Staff get 403)
        if (currentUser.getRole() != Role.STUDENT) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            json.put("success", false);
            json.put("message", "Access Denied: Personalized recommendations are available to students only.");
            resp.getWriter().write(gson.toJson(json));
            return;
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
            // Student ID extracted strictly from session
            int studentId = currentUser.getUserId();
            String department = (currentUser instanceof com.psgitech.library.model.Student)
                    ? ((com.psgitech.library.model.Student) currentUser).getDepartment()
                    : null;

            List<BookRecommendation> recommendations = recommendationService.getStudentRecommendations(studentId, department, limit);

            json.put("success", true);
            json.put("count", recommendations.size());
            json.put("recommendations", recommendations);
            json.put("asOf", Instant.now().toString());
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal server error retrieving personalized recommendations.");
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
