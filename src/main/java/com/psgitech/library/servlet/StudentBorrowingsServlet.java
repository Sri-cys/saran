package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.Student;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet providing student borrowing lists.
 * Security: Students only ever see their own records. Student ID from session is strictly enforced.
 * URL Mapping: /borrow/student
 */
@WebServlet(name = "StudentBorrowingsServlet", urlPatterns = {"/borrow/student"})
public class StudentBorrowingsServlet extends HttpServlet {
    private BorrowingService borrowingService;
    private UserRepository userRepository;
    private Gson gson;

    @Override
    public void init() {
        this.borrowingService = new BorrowingService();
        this.userRepository = new UserRepository();
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

        int targetStudentId;

        // Security rule: If current user is a STUDENT, strictly bind to their own session user_id
        if (currentUser.getRole() == Role.STUDENT) {
            targetStudentId = currentUser.getUserId();
        } else if (isStaff(currentUser)) {
            // Staff may inspect a specific student via parameter
            String idStr = req.getParameter("studentId");
            String regNo = req.getParameter("registerNumber");
            if (idStr != null && !idStr.trim().isEmpty()) {
                try {
                    targetStudentId = Integer.parseInt(idStr.trim());
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    Map<String, Object> err = new HashMap<>();
                    err.put("success", false);
                    err.put("message", "Invalid student ID format.");
                    resp.getWriter().write(gson.toJson(err));
                    return;
                }
            } else if (regNo != null && !regNo.trim().isEmpty()) {
                Student s = userRepository.findByRegisterNumber(regNo.trim());
                if (s == null) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    Map<String, Object> err = new HashMap<>();
                    err.put("success", false);
                    err.put("message", "Student not found for register number: " + regNo);
                    resp.getWriter().write(gson.toJson(err));
                    return;
                }
                targetStudentId = s.getUserId();
            } else {
                targetStudentId = currentUser.getUserId();
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access denied.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        String type = req.getParameter("type"); // "active", "history", "returned", "overdue"
        List<BorrowTransaction> results;

        if ("history".equalsIgnoreCase(type)) {
            results = borrowingService.getStudentBorrowingHistory(targetStudentId);
        } else if ("returned".equalsIgnoreCase(type)) {
            List<BorrowTransaction> all = borrowingService.getStudentBorrowingHistory(targetStudentId);
            results = new ArrayList<>();
            for (BorrowTransaction tx : all) {
                if (tx.getStatus() == BorrowStatus.RETURNED) {
                    results.add(tx);
                }
            }
        } else if ("overdue".equalsIgnoreCase(type)) {
            List<BorrowTransaction> all = borrowingService.getStudentActiveBorrowings(targetStudentId);
            results = new ArrayList<>();
            for (BorrowTransaction tx : all) {
                if (tx.isOverdue()) {
                    results.add(tx);
                }
            }
        } else {
            // Default: active borrowings
            results = borrowingService.getStudentActiveBorrowings(targetStudentId);
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("success", true);
        responseData.put("count", results.size());
        responseData.put("data", results);
        resp.getWriter().write(gson.toJson(responseData));
    }
}

