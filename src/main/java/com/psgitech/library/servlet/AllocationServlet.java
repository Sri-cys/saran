package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.AllocationStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.AllocationRecord;
import com.psgitech.library.model.User;
import com.psgitech.library.service.AllocationService;
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
 * Servlet providing Hold Shelf management and allocation queries.
 * - Librarians/Admins: View all active/historical allocations with filters.
 * - Students (?mine=true): View own active allocations ready for collection.
 * URL Mapping: /allocations, /allocations/
 */
@WebServlet(name = "AllocationServlet", urlPatterns = {"/allocations", "/allocations/"})
public class AllocationServlet extends HttpServlet {
    private AllocationService allocationService;
    private Gson gson;

    @Override
    public void init() {
        this.allocationService = new AllocationService();
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

        String mineParam = req.getParameter("mine");
        boolean isMineRequest = "true".equalsIgnoreCase(mineParam);

        Map<String, Object> responseData = new HashMap<>();

        try {
            if (isMineRequest || currentUser.getRole() == Role.STUDENT) {
                // Student view: Return student's own active ALLOCATED allocations
                List<AllocationRecord> studentAllocations = allocationService.getActiveAllocationsByStudent(currentUser.getUserId());
                responseData.put("success", true);
                responseData.put("data", studentAllocations);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(responseData));
                return;
            }

            // Staff view: Librarians & Administrators
            if (!isStaff(currentUser)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                responseData.put("success", false);
                responseData.put("message", "Access denied. Insufficient permissions to view hold shelf.");
                resp.getWriter().write(gson.toJson(responseData));
                return;
            }

            // Parse filters
            String statusParam = req.getParameter("status");
            AllocationStatus status = (statusParam != null && !statusParam.trim().isEmpty()) ?
                    AllocationStatus.fromString(statusParam.trim()) : AllocationStatus.ALLOCATED;

            Integer bookId = null;
            String bookIdParam = req.getParameter("bookId");
            if (bookIdParam != null && !bookIdParam.trim().isEmpty()) {
                try {
                    bookId = Integer.parseInt(bookIdParam.trim());
                } catch (NumberFormatException ignored) {}
            }

            int page = 1;
            String pageParam = req.getParameter("page");
            if (pageParam != null && !pageParam.trim().isEmpty()) {
                try {
                    page = Math.max(1, Integer.parseInt(pageParam.trim()));
                } catch (NumberFormatException ignored) {}
            }

            int limit = 50;
            String limitParam = req.getParameter("limit");
            if (limitParam != null && !limitParam.trim().isEmpty()) {
                try {
                    limit = Math.min(100, Math.max(1, Integer.parseInt(limitParam.trim())));
                } catch (NumberFormatException ignored) {}
            }

            List<AllocationRecord> allocations = allocationService.listHoldShelfAllocations(status, bookId, page, limit);
            int totalCount = allocationService.countHoldShelfAllocations(status, bookId);

            Map<String, Object> pageData = new HashMap<>();
            pageData.put("allocations", allocations);
            pageData.put("totalCount", totalCount);
            pageData.put("page", page);
            pageData.put("limit", limit);
            pageData.put("totalPages", (int) Math.ceil((double) totalCount / limit));

            responseData.put("success", true);
            responseData.put("data", pageData);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseData.put("success", false);
            responseData.put("message", "Database error retrieving allocations: " + e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));
        }
    }
}

