package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.model.User;
import com.psgitech.library.service.ReservationService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;

/**
 * Servlet returning active and historical book reservations for students.
 * URL Mapping: /reservations/my
 */
@WebServlet(name = "MyReservationsServlet", urlPatterns = {"/reservations/my"})
public class MyReservationsServlet extends HttpServlet {
    private ReservationService reservationService;
    private Gson gson;

    @Override
    public void init() {
        this.reservationService = new ReservationService();
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

        int targetStudentId = currentUser.getUserId();

        // Staff can optionally pass studentId parameter to inspect a student's queue status
        String studentIdParam = req.getParameter("studentId");
        if (studentIdParam != null && !studentIdParam.trim().isEmpty()) {
            if (isStaff(currentUser)) {
                try {
                    targetStudentId = Integer.parseInt(studentIdParam.trim());
                } catch (NumberFormatException ignored) {}
            }
            // If student passes another studentId, it is ignored (Security Enforcement)
        }

        try {
            List<Reservation> activeReservations = reservationService.getActiveReservationsForStudent(targetStudentId);
            List<Reservation> historyReservations = reservationService.getReservationHistoryForStudent(targetStudentId);

            int maxLimit = reservationService.getMaxActiveReservationLimit();

            Map<String, Object> summary = new HashMap<>();
            summary.put("activeCount", activeReservations.size());
            summary.put("maxLimit", maxLimit);
            summary.put("remainingCapacity", Math.max(0, maxLimit - activeReservations.size()));

            Map<String, Object> data = new HashMap<>();
            data.put("activeReservations", activeReservations);
            data.put("reservationHistory", historyReservations);
            data.put("summary", summary);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("data", data);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Database error retrieving reservations: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}

