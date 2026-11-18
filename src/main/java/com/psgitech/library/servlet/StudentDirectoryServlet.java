package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.model.StudentMaster;
import com.psgitech.library.model.User;
import com.psgitech.library.service.StudentDirectoryService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet providing API and directory endpoints for the Institutional Student Master Directory.
 * Supports peer discovery for book interchanges, institutional identity verification,
 * and librarian/student master record lookups.
 * URL Mappings: /students/directory, /api/students/directory, /api/students/profile, /api/students/verify
 */
@WebServlet(name = "StudentDirectoryServlet", urlPatterns = {
        "/students/directory",
        "/api/students/directory",
        "/api/students/profile",
        "/api/students/verify"
})
public class StudentDirectoryServlet extends HttpServlet {
    private StudentDirectoryService directoryService;
    private Gson gson;

    @Override
    public void init() {
        this.directoryService = new StudentDirectoryService();
        this.gson = GsonProvider.getGson();
    }

    private User getAuthenticatedUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return (session != null) ? (User) session.getAttribute("user") : null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String servletPath = req.getServletPath();

        // 1. Web Page view navigation redirect
        if ("/students/directory".equals(servletPath)) {
            boolean isJson = "XMLHttpRequest".equalsIgnoreCase(req.getHeader("X-Requested-With"))
                    || (req.getHeader("Accept") != null && req.getHeader("Accept").contains("application/json"));
            if (!isJson) {
                resp.sendRedirect(req.getContextPath() + "/student-directory.html");
                return;
            }
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // 2. Pre-registration instant verification (allowed without authentication)
        if ("/api/students/verify".equals(servletPath)) {
            handleVerifyRollNumber(req, resp);
            return;
        }

        // 3. Profile / Book Holdings endpoint
        if ("/api/students/profile".equals(servletPath)) {
            handleStudentProfile(req, resp);
            return;
        }

        // 4. Directory Listing & Search endpoint (/api/students/directory or JSON /students/directory)
        handleDirectoryList(req, resp);
    }

    private void handleDirectoryList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String query = req.getParameter("q");
        if (query == null) query = req.getParameter("query");

        String registrationStatus = req.getParameter("registrationStatus");
        String accountStatus = req.getParameter("accountStatus");

        int page = 1;
        String pageStr = req.getParameter("page");
        if (pageStr != null && !pageStr.trim().isEmpty()) {
            try {
                page = Integer.parseInt(pageStr.trim());
            } catch (NumberFormatException ignored) {}
        }

        int pageSize = 20;
        String pageSizeStr = req.getParameter("pageSize");
        if (pageSizeStr != null && !pageSizeStr.trim().isEmpty()) {
            try {
                pageSize = Integer.parseInt(pageSizeStr.trim());
            } catch (NumberFormatException ignored) {}
        }

        Map<String, Object> result = directoryService.getDirectory(query, registrationStatus, accountStatus, page, pageSize);

        // Include current authenticated user context if logged in
        User currentUser = getAuthenticatedUser(req);
        if (currentUser != null) {
            Map<String, Object> viewer = new HashMap<>();
            viewer.put("userId", currentUser.getUserId());
            viewer.put("name", currentUser.getName());
            viewer.put("role", currentUser.getRole().name());
            viewer.put("identifier", currentUser.getIdentifier());
            result.put("viewer", viewer);
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(result));
    }

    private void handleStudentProfile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rollNumber = req.getParameter("rollNumber");
        if (rollNumber == null || rollNumber.trim().isEmpty()) {
            rollNumber = req.getParameter("regNo");
        }

        if (rollNumber == null || rollNumber.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Roll number parameter is required.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        StudentMaster student = directoryService.getStudentProfile(rollNumber.trim());
        if (student == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Student with roll number '" + rollNumber + "' was not found in institutional records.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("student", student);
        res.put("activeBorrowings", student.getActiveBorrowings());

        // Check if current user is logged in
        User currentUser = getAuthenticatedUser(req);
        if (currentUser != null) {
            res.put("canInitiateInterchange",
                    student.isRegistered() && student.isActive() && currentUser.getUserId() != (student.getUserId() != null ? student.getUserId() : -1));
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(res));
    }

    private void handleVerifyRollNumber(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rollNumber = req.getParameter("rollNumber");
        if (rollNumber == null || rollNumber.trim().isEmpty()) {
            rollNumber = req.getParameter("regNo");
        }

        Map<String, Object> result = directoryService.verifyRollNumber(rollNumber);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(result));
    }
}
