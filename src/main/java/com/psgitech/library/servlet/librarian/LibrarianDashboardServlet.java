package com.psgitech.library.servlet.librarian;

import com.google.gson.Gson;
import com.psgitech.library.dto.librarian.ApiResponse;
import com.psgitech.library.dto.librarian.DashboardStatsDTO;
import com.psgitech.library.service.LibrarianDashboardService;
import com.psgitech.library.service.LibrarianTriageService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet handling Librarian Dashboard real-time KPIs, charts, collections, and global search.
 * URL Mapping: /api/librarian/dashboard/* and /api/librarian/search
 */
@WebServlet(name = "LibrarianDashboardServlet", urlPatterns = {"/api/librarian/dashboard/*", "/api/librarian/search"})
public class LibrarianDashboardServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LibrarianDashboardServlet.class.getName());

    private LibrarianDashboardService dashboardService;
    private LibrarianTriageService triageService;
    private Gson gson;

    @Override
    public void init() {
        this.dashboardService = new LibrarianDashboardService();
        this.triageService = new LibrarianTriageService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.equals("/api/librarian/search")) {
                handleSearch(req, resp);
                return;
            }

            if (path.endsWith("/stats")) {
                handleStats(req, resp);
            } else if (path.endsWith("/charts")) {
                handleCharts(req, resp);
            } else if (path.endsWith("/collections")) {
                handleCollections(req, resp);
            } else if (path.endsWith("/triage")) {
                handleTriage(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Unknown dashboard endpoint: " + path)));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "LibrarianDashboardServlet error for path: " + path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", "Server error processing dashboard request.")));
        }
    }

    private void handleStats(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        DashboardStatsDTO stats = dashboardService.getDashboardStats();
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(stats)));
    }

    private void handleCharts(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String windowStr = req.getParameter("window");
        int days = 30;
        if ("7".equals(windowStr)) days = 7;
        else if ("90".equals(windowStr)) days = 90;
        else if ("all".equalsIgnoreCase(windowStr)) days = 0;

        Map<String, Object> charts = dashboardService.getChartsData(days);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(charts)));
    }

    private void handleCollections(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> collections = dashboardService.getHomeCollections();
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(collections)));
    }

    private void handleTriage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String section = req.getParameter("section");
        if (section != null && !section.trim().isEmpty()) {
            List<Map<String, Object>> drilldown = triageService.getDrilldown(section.trim());
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(ApiResponse.ok(drilldown)));
        } else {
            Map<String, Object> summary = triageService.getTriageSummary();
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(ApiResponse.ok(summary)));
        }
    }

    private void handleSearch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String q = req.getParameter("q");
        Map<String, List<Map<String, Object>>> results = dashboardService.performGlobalSearch(q);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(results)));
    }
}
