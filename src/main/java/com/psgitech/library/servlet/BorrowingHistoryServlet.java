package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.Role;
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
 * Servlet returning borrowing history.
 * Students receive only their own history.
 * Staff members receive complete institutional history with search support.
 * URL Mapping: /borrow/history
 */
@WebServlet(name = "BorrowingHistoryServlet", urlPatterns = {"/borrow/history"})
public class BorrowingHistoryServlet extends HttpServlet {
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

        List<BorrowTransaction> history;

        if (currentUser.getRole() == Role.STUDENT) {
            // Strictly student's own history
            history = borrowingService.getStudentBorrowingHistory(currentUser.getUserId());
        } else if (currentUser.getRole() == Role.LIBRARIAN || currentUser.getRole() == Role.ADMIN) {
            String query = req.getParameter("q");
            if (query == null) query = req.getParameter("query");
            history = borrowingService.getAllBorrowingHistory(query);
        } else {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access denied.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("success", true);
        responseData.put("count", history.size());
        responseData.put("data", history);
        resp.getWriter().write(gson.toJson(responseData));
    }
}

