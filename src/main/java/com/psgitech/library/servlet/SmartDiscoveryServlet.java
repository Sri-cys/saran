package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookSearchCriteria;
import com.psgitech.library.model.PagedResult;
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
import java.util.Map;

/**
 * REST endpoint for smart book discovery, parameterized search, and multi-faceted filtering (Module 10.1 & 10.3).
 * URL Mapping: GET /books/discover
 */
@WebServlet(name = "SmartDiscoveryServlet", urlPatterns = {"/books/discover"})
public class SmartDiscoveryServlet extends HttpServlet {
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
            String q = req.getParameter("q");
            if (q == null) q = req.getParameter("keyword");

            BookSearchCriteria.Builder builder = BookSearchCriteria.builder()
                    .query(q)
                    .category(req.getParameter("category"))
                    .author(req.getParameter("author"))
                    .publisher(req.getParameter("publisher"))
                    .department(req.getParameter("department"))
                    .availability(req.getParameter("availability"))
                    .sort(req.getParameter("sort"));

            String yf = req.getParameter("yearFrom");
            if (yf != null && !yf.trim().isEmpty()) {
                try {
                    builder.yearFrom(Integer.parseInt(yf.trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("yearFrom must be a valid integer.");
                }
            }

            String yt = req.getParameter("yearTo");
            if (yt != null && !yt.trim().isEmpty()) {
                try {
                    builder.yearTo(Integer.parseInt(yt.trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("yearTo must be a valid integer.");
                }
            }

            String p = req.getParameter("page");
            if (p != null && !p.trim().isEmpty()) {
                try {
                    builder.page(Integer.parseInt(p.trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("page must be a valid positive integer.");
                }
            }

            String ps = req.getParameter("pageSize");
            if (ps != null && !ps.trim().isEmpty()) {
                try {
                    builder.pageSize(Integer.parseInt(ps.trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("pageSize must be a valid positive integer.");
                }
            }

            BookSearchCriteria criteria = builder.build();
            PagedResult<Book> result = discoveryService.search(criteria);

            json.put("success", true);
            json.put("message", "Books discovered successfully.");
            json.put("data", result);
            json.put("asOf", Instant.now().toString());
            resp.setStatus(HttpServletResponse.SC_OK);

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            json.put("success", false);
            json.put("message", e.getMessage());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json.put("success", false);
            json.put("message", "Internal server error during book discovery search.");
        }

        resp.getWriter().write(gson.toJson(json));
    }
}
