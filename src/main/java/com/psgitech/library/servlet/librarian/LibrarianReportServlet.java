package com.psgitech.library.servlet.librarian;

import com.google.gson.Gson;
import com.psgitech.library.dto.librarian.ApiResponse;
import com.psgitech.library.exception.InvalidDateRangeException;
import com.psgitech.library.service.ActivityLogService;
import com.psgitech.library.service.report.LibrarianReportService;
import com.psgitech.library.service.report.ReportFilter;
import com.psgitech.library.service.report.ReportResult;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "LibrarianReportServlet", urlPatterns = {"/api/librarian/reports/*"})
public class LibrarianReportServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LibrarianReportServlet.class.getName());

    private ActivityLogService activityLogService;
    private LibrarianReportService reportService;
    private Gson gson;

    @Override
    public void init() {
        this.activityLogService = new ActivityLogService();
        this.reportService = new LibrarianReportService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/export")) {
                handleExportReport(req, resp);
                return;
            }

            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");

            if (path.endsWith("/activity-logs")) {
                handleGetActivityLogs(req, resp);
            } else if (path.endsWith("/generate")) {
                handleGenerateReport(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Unknown reports endpoint: " + path)));
            }
        } catch (InvalidDateRangeException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INVALID_DATE_RANGE", e.getMessage())));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Reports GET error: " + path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    private void handleGetActivityLogs(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String eventType = req.getParameter("eventType");
        String targetEntity = req.getParameter("targetEntity");
        Integer actorId = null;
        if (req.getParameter("actorId") != null && !req.getParameter("actorId").isEmpty()) {
            try { actorId = Integer.parseInt(req.getParameter("actorId")); } catch (NumberFormatException ignored) {}
        }
        LocalDate fromDate = (req.getParameter("from") != null && !req.getParameter("from").isEmpty()) ?
                LocalDate.parse(req.getParameter("from")) : null;
        LocalDate toDate = (req.getParameter("to") != null && !req.getParameter("to").isEmpty()) ?
                LocalDate.parse(req.getParameter("to")) : null;
        int page = parseInt(req.getParameter("page"), 1);
        int limit = parseInt(req.getParameter("limit"), 50);

        List<Map<String, Object>> logs = activityLogService.getLogs(page, limit, eventType, targetEntity, actorId, fromDate, toDate);
        int total = activityLogService.countLogs(eventType, targetEntity, actorId, fromDate, toDate);

        Map<String, Object> data = Map.of(
                "items", logs,
                "total", total,
                "page", page,
                "totalPages", (int) Math.ceil((double) total / limit)
        );

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(data)));
    }

    private void handleGenerateReport(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvalidDateRangeException {
        ReportFilter filter = extractFilter(req);
        ReportResult result = reportService.generateReport(filter);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(result)));
    }

    private void handleExportReport(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvalidDateRangeException {
        ReportFilter filter = extractFilter(req);
        ReportResult result = reportService.generateReport(filter);

        String csv = reportService.exportToCsv(result);
        String filename = "PSG_Library_Report_" + result.getReportType() + "_" + LocalDate.now() + ".csv";

        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        resp.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
    }

    private ReportFilter extractFilter(HttpServletRequest req) {
        String reportType = req.getParameter("reportType");
        String preset = req.getParameter("preset");
        String fromStr = req.getParameter("from");
        String toStr = req.getParameter("to");
        String dept = req.getParameter("department");

        LocalDate from = (fromStr != null && !fromStr.isEmpty()) ? LocalDate.parse(fromStr) : null;
        LocalDate to = (toStr != null && !toStr.isEmpty()) ? LocalDate.parse(toStr) : null;

        return new ReportFilter(reportType, preset, from, to, dept);
    }

    private int parseInt(String str, int def) {
        if (str == null || str.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
