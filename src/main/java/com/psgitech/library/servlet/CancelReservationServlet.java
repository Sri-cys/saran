package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.AllocationNotFoundException;
import com.psgitech.library.exception.InvalidAllocationException;
import com.psgitech.library.exception.InvalidReservationException;
import com.psgitech.library.exception.ReservationNotFoundException;
import com.psgitech.library.model.AllocationResult;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet handling cancellation of WAITING book reservations.
 * Accessible to the owning student or staff (Librarian/Admin).
 * URL Mapping: /reservations/cancel
 */
@WebServlet(name = "CancelReservationServlet", urlPatterns = {"/reservations/cancel"})
public class CancelReservationServlet extends HttpServlet {
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

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
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

        Integer reservationId = null;
        String contentType = req.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            try {
                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                if (json.has("reservationId")) {
                    reservationId = json.get("reservationId").getAsInt();
                }
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Invalid JSON request: " + e.getMessage());
                resp.getWriter().write(gson.toJson(err));
                return;
            }
        } else {
            String resIdStr = req.getParameter("reservationId");
            if (resIdStr != null && !resIdStr.trim().isEmpty()) {
                try {
                    reservationId = Integer.parseInt(resIdStr.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<String, Object> responseData = new HashMap<>();

        if (reservationId == null || reservationId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("success", false);
            responseData.put("message", "Valid reservation ID is required.");
            resp.getWriter().write(gson.toJson(responseData));
            return;
        }

        try {
            int actingUserId = currentUser.getUserId();
            Role actingRole = currentUser.getRole();

            Reservation cancelled = reservationService.cancelReservation(reservationId, actingUserId, actingRole);

            resp.setStatus(HttpServletResponse.SC_OK);
            responseData.put("success", true);
            responseData.put("message", "Reservation cancelled successfully.");

            Map<String, Object> data = new HashMap<>();
            data.put("reservationId", cancelled.getReservationId());
            data.put("status", cancelled.getStatus().name());
            data.put("cancelledAt", cancelled.getCancelledAt() != null ? cancelled.getCancelledAt().toString() : null);

            // Feature 5 (Section 9.5): Include copyOutcome if an ALLOCATED copy was released
            AllocationResult releaseOutcome = cancelled.getReleaseOutcome();
            if (releaseOutcome != null) {
                String copyOutcome = releaseOutcome.isAllocated() ? "REALLOCATED" : "MADE_AVAILABLE";
                data.put("copyOutcome", copyOutcome);

                // Staff callers additionally receive next student's details when reallocated
                if (actingRole != Role.STUDENT && releaseOutcome.isAllocated() && releaseOutcome.getAllocation() != null) {
                    data.put("nextStudentName", releaseOutcome.getAllocation().getStudentName());
                    data.put("nextStudentRegisterNumber", releaseOutcome.getAllocation().getRegisterNumber());
                }
            }

            responseData.put("data", data);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (ReservationNotFoundException | AllocationNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (InvalidReservationException | InvalidAllocationException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseData.put("success", false);
            responseData.put("message", "An unexpected error occurred while cancelling reservation.");
            resp.getWriter().write(gson.toJson(responseData));
        }
    }
}

