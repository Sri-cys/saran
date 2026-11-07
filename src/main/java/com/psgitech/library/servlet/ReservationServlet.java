package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.model.ReservationQueue;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.BorrowRepository;
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
import java.util.*;

/**
 * Servlet handling Book Queue Reservations.
 * URL Mapping: /reservations
 */
@WebServlet(name = "ReservationServlet", urlPatterns = {"/reservations", "/reservations/"})
public class ReservationServlet extends HttpServlet {
    private ReservationService reservationService;
    private BorrowRepository borrowRepository;
    private Gson gson;

    @Override
    public void init() {
        this.reservationService = new ReservationService();
        this.borrowRepository = new BorrowRepository();
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

        if (currentUser.getRole() != Role.STUDENT) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access denied. Only registered students may reserve books.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        Integer bookId = null;
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
                if (json.has("bookId")) {
                    bookId = json.get("bookId").getAsInt();
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
            String bookIdStr = req.getParameter("bookId");
            if (bookIdStr != null && !bookIdStr.trim().isEmpty()) {
                try {
                    bookId = Integer.parseInt(bookIdStr.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<String, Object> responseData = new HashMap<>();

        if (bookId == null || bookId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("success", false);
            responseData.put("message", "Valid book ID is required.");
            resp.getWriter().write(gson.toJson(responseData));
            return;
        }

        try {
            // studentId comes ONLY from authenticated session (Security Check)
            int studentId = currentUser.getUserId();
            Reservation res = reservationService.createReservation(studentId, bookId);

            resp.setStatus(HttpServletResponse.SC_CREATED);
            responseData.put("success", true);
            responseData.put("message", "Reservation placed successfully. You are #" + res.getQueuePosition() + " in queue.");

            Map<String, Object> data = new HashMap<>();
            data.put("reservationId", res.getReservationId());
            data.put("bookId", res.getBookId());
            data.put("bookTitle", res.getBookTitle());
            data.put("reservationDate", res.getReservationDate() != null ? res.getReservationDate().toString() : null);
            data.put("status", res.getStatus().name());
            data.put("queuePosition", res.getQueuePosition());
            data.put("totalWaitingInQueue", res.getTotalWaitingInQueue());

            responseData.put("data", data);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (DuplicateReservationException | ReservationLimitExceededException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (InvalidReservationException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (BookNotFoundException | StudentNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (StudentNotEligibleException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseData.put("success", false);
            responseData.put("message", "An unexpected error occurred while placing reservation.");
            resp.getWriter().write(gson.toJson(responseData));
        }
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

        String bookIdStr = req.getParameter("bookId");
        Map<String, Object> responseData = new HashMap<>();

        if (bookIdStr != null && !bookIdStr.trim().isEmpty()) {
            // GET /reservations?bookId=...
            int bookId;
            try {
                bookId = Integer.parseInt(bookIdStr.trim());
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                responseData.put("success", false);
                responseData.put("message", "Invalid book ID format.");
                resp.getWriter().write(gson.toJson(responseData));
                return;
            }

            ReservationQueue queue = reservationService.getQueueForBook(bookId);
            int waitingCount = queue.size();

            responseData.put("success", true);
            responseData.put("bookId", bookId);
            responseData.put("waitingCount", waitingCount);

            if (isStaff(currentUser)) {
                // Librarian / Admin also receives the full ordered queue
                List<Map<String, Object>> queueRows = new ArrayList<>();
                int pos = 1;
                for (Reservation r : queue.getAsList()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("reservationId", r.getReservationId());
                    row.put("queuePosition", pos++);
                    row.put("studentName", r.getStudentName());
                    row.put("registerNumber", r.getRegisterNumber());
                    row.put("reservationDate", r.getReservationDate() != null ? r.getReservationDate().toString() : null);
                    row.put("status", r.getStatus().name());
                    queueRows.add(row);
                }
                responseData.put("queue", queueRows);
            } else if (currentUser.getRole() == Role.STUDENT) {
                // Student receives "myReservation" and "currentlyBorrowedByMe"
                int studentId = currentUser.getUserId();
                Reservation myRes = reservationService.getActiveReservationForStudentAndBook(studentId, bookId);
                if (myRes != null) {
                    Map<String, Object> myResData = new HashMap<>();
                    myResData.put("reservationId", myRes.getReservationId());
                    myResData.put("queuePosition", myRes.getQueuePosition());
                    myResData.put("status", myRes.getStatus().name());
                    responseData.put("myReservation", myResData);
                } else {
                    responseData.put("myReservation", null);
                }

                boolean currentlyBorrowed = borrowRepository.hasActiveBorrowingForBook(studentId, bookId);
                responseData.put("currentlyBorrowedByMe", currentlyBorrowed);
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(responseData));

        } else {
            // GET /reservations (without bookId) - Librarian/Admin only
            if (!isStaff(currentUser)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                responseData.put("success", false);
                responseData.put("message", "Access denied. Only Librarians and Administrators can view all library queues.");
                resp.getWriter().write(gson.toJson(responseData));
                return;
            }

            int page = 1;
            int pageSize = 50;
            String pageStr = req.getParameter("page");
            String pageSizeStr = req.getParameter("pageSize");
            String filterBookIdStr = req.getParameter("filterBookId");

            if (pageStr != null && !pageStr.trim().isEmpty()) {
                try { page = Integer.parseInt(pageStr.trim()); } catch (NumberFormatException ignored) {}
            }
            if (pageSizeStr != null && !pageSizeStr.trim().isEmpty()) {
                try { pageSize = Integer.parseInt(pageSizeStr.trim()); } catch (NumberFormatException ignored) {}
            }
            Integer filterBookId = null;
            if (filterBookIdStr != null && !filterBookIdStr.trim().isEmpty()) {
                try { filterBookId = Integer.parseInt(filterBookIdStr.trim()); } catch (NumberFormatException ignored) {}
            }

            List<Reservation> activeList = reservationService.getAllActiveReservations(page, pageSize, filterBookId);

            responseData.put("success", true);
            responseData.put("page", page);
            responseData.put("pageSize", pageSize);
            responseData.put("count", activeList.size());
            responseData.put("reservations", activeList);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(responseData));
        }
    }
}

