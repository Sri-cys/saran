package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.BookInterchange;
import com.psgitech.library.model.User;
import com.psgitech.library.service.BookInterchangeService;
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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet handling Student-to-Student Book Interchange queries and initiation.
 * URL Mapping: /interchanges
 */
@WebServlet(name = "InterchangeServlet", urlPatterns = {"/interchanges"})
public class InterchangeServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InterchangeServlet.class.getName());
    private BookInterchangeService interchangeService;
    private Gson gson;

    @Override
    public void init() {
        this.interchangeService = new BookInterchangeService();
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
            resp.getWriter().write(gson.toJson(buildResponse(false, "Authentication required. Please log in.", null)));
            return;
        }

        String type = req.getParameter("type");
        String idParam = req.getParameter("id");

        try {
            if (idParam != null && !idParam.trim().isEmpty()) {
                int id = Integer.parseInt(idParam.trim());
                boolean isStaff = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.LIBRARIAN;
                BookInterchange bi = interchangeService.getInterchangeById(id, currentUser.getUserId(), isStaff);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(buildResponse(true, "Interchange record retrieved.", bi)));
                return;
            }

            if ("count".equalsIgnoreCase(type)) {
                int count = interchangeService.getPendingIncomingCount(currentUser.getUserId());
                Map<String, Object> data = new HashMap<>();
                data.put("pendingIncomingCount", count);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(buildResponse(true, "Pending count retrieved.", data)));
                return;
            }

            if ("incoming".equalsIgnoreCase(type)) {
                List<BookInterchange> incoming = interchangeService.getIncomingRequests(currentUser.getUserId());
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(buildResponse(true, "Incoming interchange requests retrieved.", incoming)));
                return;
            }

            if ("outgoing".equalsIgnoreCase(type)) {
                List<BookInterchange> outgoing = interchangeService.getOutgoingRequests(currentUser.getUserId());
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(buildResponse(true, "Outgoing interchange requests retrieved.", outgoing)));
                return;
            }

            // Default: Full interchange history involving this student
            List<BookInterchange> history = interchangeService.getInterchangeHistory(currentUser.getUserId());
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(buildResponse(true, "Interchange history retrieved.", history)));

        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(buildResponse(false, "Invalid ID parameter.", null)));
        } catch (InterchangeNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(buildResponse(false, e.getMessage(), null)));
        } catch (InterchangeNotAllowedException e) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write(gson.toJson(buildResponse(false, e.getMessage(), null)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in InterchangeServlet GET", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(buildResponse(false, "An unexpected server error occurred.", null)));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(gson.toJson(buildResponse(false, "Authentication required. Please log in.", null)));
            return;
        }

        if (currentUser.getRole() != Role.STUDENT && currentUser.getRole() != Role.ADMIN) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write(gson.toJson(buildResponse(false, "Only registered students can initiate book interchanges.", null)));
            return;
        }

        int transactionId = 0;
        String recipientRegisterNumber = null;
        String requestNote = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            try (BufferedReader reader = req.getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("transactionId")) transactionId = json.get("transactionId").getAsInt();
                if (json.has("recipientRegisterNumber")) recipientRegisterNumber = json.get("recipientRegisterNumber").getAsString();
                if (json.has("requestNote") && !json.get("requestNote").isJsonNull()) requestNote = json.get("requestNote").getAsString();
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(gson.toJson(buildResponse(false, "Malformed JSON request body.", null)));
                return;
            }
        } else {
            String txParam = req.getParameter("transactionId");
            if (txParam != null && !txParam.trim().isEmpty()) {
                try { transactionId = Integer.parseInt(txParam.trim()); } catch (NumberFormatException ignored) {}
            }
            recipientRegisterNumber = req.getParameter("recipientRegisterNumber");
            requestNote = req.getParameter("requestNote");
        }

        if (transactionId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(buildResponse(false, "Valid borrowing transactionId is required.", null)));
            return;
        }
        if (recipientRegisterNumber == null || recipientRegisterNumber.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(buildResponse(false, "Recipient student register number is required.", null)));
            return;
        }

        try {
            BookInterchange interchange = interchangeService.initiateInterchange(
                    currentUser.getUserId(), transactionId, recipientRegisterNumber.trim(), requestNote
            );
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write(gson.toJson(buildResponse(true,
                    "Interchange request sent. The borrowing remains yours until the recipient accepts.", interchange)));

        } catch (StudentNotFoundException | BorrowTransactionNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(buildResponse(false, e.getMessage(), null)));
        } catch (RecipientNotEligibleException | InterchangeNotAllowedException | ActiveReservationQueueException | InvalidInterchangeException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(gson.toJson(buildResponse(false, e.getMessage(), null)));
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(buildResponse(false, e.getMessage(), null)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error initiating interchange", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(buildResponse(false, "An error occurred while initiating the interchange request.", null)));
        }
    }

    private Map<String, Object> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        map.put("message", message);
        if (data != null) {
            map.put("data", data);
        }
        return map;
    }
}

