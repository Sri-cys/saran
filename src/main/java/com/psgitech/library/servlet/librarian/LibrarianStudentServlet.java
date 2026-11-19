package com.psgitech.library.servlet.librarian;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.dto.librarian.ApiResponse;
import com.psgitech.library.dto.librarian.NoDuesSlipDTO;
import com.psgitech.library.dto.librarian.TrustStandingDTO;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.User;
import com.psgitech.library.service.LibrarianStudentService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet handling Student directory search, profile audits, No-Dues clearance, and status changes.
 * URL Mapping: /api/librarian/students/*
 */
@WebServlet(name = "LibrarianStudentServlet", urlPatterns = {"/api/librarian/students/*"})
public class LibrarianStudentServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LibrarianStudentServlet.class.getName());

    private LibrarianStudentService studentService;
    private Gson gson;

    @Override
    public void init() {
        this.studentService = new LibrarianStudentService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/profile")) {
                handleStudentProfile(req, resp);
            } else if (path.endsWith("/no-dues")) {
                handleNoDuesCheck(req, resp);
            } else if (path.endsWith("/trust-standing")) {
                handleTrustStanding(req, resp);
            } else {
                handleListStudents(req, resp);
            }
        } catch (ValidationException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(ApiResponse.error("VALIDATION_ERROR", e.getMessage())));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Student GET error: " + path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/toggle-status")) {
                handleToggleStatus(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Unknown endpoint: " + path)));
            }
        } catch (ValidationException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(ApiResponse.error("VALIDATION_ERROR", e.getMessage())));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Student POST error: " + path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    private void handleListStudents(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int page = 1;
        int pageSize = 20;
        try {
            if (req.getParameter("page") != null) page = Integer.parseInt(req.getParameter("page"));
            if (req.getParameter("pageSize") != null) pageSize = Integer.parseInt(req.getParameter("pageSize"));
        } catch (NumberFormatException ignored) {}

        String query = req.getParameter("q");
        String filter = req.getParameter("filter");

        List<Map<String, Object>> students = studentService.getStudents(page, pageSize, query, filter);
        int total = studentService.countStudents(query, filter);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(students, page, pageSize, total)));
    }

    private void handleStudentProfile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("studentId");
        if (idStr == null || idStr.trim().isEmpty()) {
            throw new ValidationException("studentId parameter is required.");
        }
        int studentId = Integer.parseInt(idStr.trim());
        Map<String, Object> profile = studentService.getStudentProfile(studentId);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(profile)));
    }

    private void handleNoDuesCheck(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("studentId");
        if (idStr == null || idStr.trim().isEmpty()) {
            throw new ValidationException("studentId parameter is required.");
        }
        int studentId = Integer.parseInt(idStr.trim());

        HttpSession session = req.getSession(false);
        User staff = (session != null) ? (User) session.getAttribute("user") : null;
        int staffId = staff != null ? staff.getUserId() : 0;
        String staffName = staff != null ? staff.getName() : "Librarian";

        NoDuesSlipDTO slip = studentService.generateNoDuesSlip(studentId, staffId, staffName, req.getRemoteAddr());
        if (slip == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Student record not found.")));
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(slip)));
    }

    private void handleTrustStanding(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("studentId");
        if (idStr == null || idStr.trim().isEmpty()) {
            throw new ValidationException("studentId parameter is required.");
        }
        int studentId = Integer.parseInt(idStr.trim());
        TrustStandingDTO dto = new com.psgitech.library.service.TrustStandingService().computeTrustStanding(studentId);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(dto)));
    }

    private void handleToggleStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();

        int studentId = json.get("studentId").getAsInt();
        boolean active = json.get("active").getAsBoolean();
        String reason = json.has("reason") ? json.get("reason").getAsString() : "";

        HttpSession session = req.getSession(false);
        User staff = (session != null) ? (User) session.getAttribute("user") : null;
        int staffId = staff != null ? staff.getUserId() : 0;

        boolean ok = studentService.toggleStudentStatus(studentId, active, reason, staffId, req.getRemoteAddr());
        if (ok) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(ApiResponse.ok(Map.of("studentId", studentId, "active", active))));
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", "Failed to update student status.")));
        }
    }
}
