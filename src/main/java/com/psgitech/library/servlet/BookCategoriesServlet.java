package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.DiscoveryCategory;
import com.psgitech.library.service.SmartDiscoveryService;
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
 * REST endpoint for catalog category aggregation with live book and copy counts (Module 10.7, Decision D13).
 * URL Mapping: GET /books/categories
 */
@WebServlet(name = "BookCategoriesServlet", urlPatterns = {"/books/categories"})
public class BookCategoriesServlet extends HttpServlet {
    private SmartDiscoveryService discoveryService;
    private Gson gson;

    @Override
    public void init() {
        this.discoveryService = new SmartDiscoveryService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        Map<String, Object> json = new HashMap<>();

        try {
            List<DiscoveryCategory> categories = discoveryService.getCategories();

            json.put("success", true);
            json.put("count", categories.size());
            json.put("categories", categories);
            json.put("asOf", Instant.now().toString());
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal server error retrieving categories.");
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
