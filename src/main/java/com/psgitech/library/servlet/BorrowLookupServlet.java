package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.StudentNotFoundException;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.User;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet handling real-time lookups for the Librarian Issue & Return Desks.
 * Supports student eligibility verification and active checkout retrieval.
 * URL Mapping: /borrow/lookup
 */
@WebServlet(name = "BorrowLookupServlet", urlPatterns = {"/borrow/lookup"})
public class BorrowLookupServlet extends HttpServlet {
    private BorrowingService borrowingService;
    private Gson gson;

    @Override
    public void init() {
        this.borrowingService = new BorrowingService();
        this.gson = GsonProvider.getGson();
    }

    private User getAuthenticatedUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return (session != null) ? (User) session.getAttribute("user") : null;
    }

    private boolean isStaff(User user) {
        return user != null && (user.getRole() == Role.LIBRARIAN || user.getRole() == Role.ADMIN);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required. Please log in.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        if (!isStaff(currentUser)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access denied. Librarian or Administrator privileges required.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        String type = req.getParameter("type"); // "student" or "return"

        if ("student".equalsIgnoreCase(type)) {
            handleStudentLookup(req, resp);
        } else if ("return".equalsIgnoreCase(type)) {
            handleReturnLookup(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Valid lookup type ('student' or 'return') is required.");
            resp.getWriter().write(gson.toJson(err));
        }
    }

    private void handleStudentLookup(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String regNo = req.getParameter("registerNumber");
        if (regNo == null) regNo = req.getParameter("regNo");

        if (regNo == null || regNo.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Student register number is required for lookup.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        Integer bookId = null;
        String bookIdStr = req.getParameter("bookId");
        if (bookIdStr != null && !bookIdStr.trim().isEmpty()) {
            try {
                bookId = Integer.parseInt(bookIdStr.trim());
            } catch (NumberFormatException ignored) {}
        }

        try {
            Map<String, Object> studentInfo = borrowingService.lookupStudentForBorrowing(regNo.trim(), bookId);
            Map<String, Object> respData = new HashMap<>();
            respData.put("success", true);
            respData.put("data", studentInfo);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(respData));

        } catch (StudentNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(err));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error retrieving student information: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }

    private void handleReturnLookup(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String query = req.getParameter("q");
        if (query == null) query = req.getParameter("query");

        try {
            List<BorrowTransaction> results = borrowingService.lookupActiveForReturn(query);
            Map<String, Object> respData = new HashMap<>();
            respData.put("success", true);
            respData.put("count", results.size());
            respData.put("data", results);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(respData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error querying active return records: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}

