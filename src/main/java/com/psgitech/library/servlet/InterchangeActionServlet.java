package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet handling state transitions for book interchanges:
 * - /interchanges/accept
 * - /interchanges/reject
 * - /interchanges/cancel
 */
@WebServlet(name = "InterchangeActionServlet", urlPatterns = {
        "/interchanges/accept",
        "/interchanges/reject",
        "/interchanges/cancel"
})
public class InterchangeActionServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InterchangeActionServlet.class.getName());
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

        String path = req.getServletPath();
        if (req.getPathInfo() != null) {
            path += req.getPathInfo();
        }

        int interchangeId = 0;
        String reason = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            try (BufferedReader reader = req.getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("interchangeId")) interchangeId = json.get("interchangeId").getAsInt();
                if (json.has("reason") && !json.get("reason").isJsonNull()) reason = json.get("reason").getAsString();
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(gson.toJson(buildResponse(false, "Malformed JSON request body.", null)));
                return;
            }
        } else {
            String idParam = req.getParameter("interchangeId");
            if (idParam != null && !idParam.trim().isEmpty()) {
                try { interchangeId = Integer.parseInt(idParam.trim()); } catch (NumberFormatException ignored) {}
            }
            reason = req.getParameter("reason");
        }

        if (interchangeId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(buildResponse(false, "Valid interchangeId is required.", null)));
            return;
        }

        try {
            if (path.endsWith("/accept")) {
                BookInterchange completed = interchangeService.acceptInterchange(interchangeId, currentUser.getUserId());
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(buildResponse(true,
                        "Book interchange completed. The borrowing record is now under your account. Your return deadline remains unchanged.", completed)));
                return;
            }

            if (path.endsWith("/reject")) {
                BookInterchange rejected = interchangeService.rejectInterchange(interchangeId, currentUser.getUserId(), reason);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(buildResponse(true,
                        "Interchange request rejected. The original borrower retains responsibility for the book.", rejected)));
                return;
            }

            if (path.endsWith("/cancel")) {
                BookInterchange cancelled = interchangeService.cancelInterchange(interchangeId, currentUser.getUserId(), reason);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(buildResponse(true,
                        "Interchange request cancelled successfully.", cancelled)));
                return;
            }

            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(buildResponse(false, "Unknown action route.", null)));

        } catch (InterchangeNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write(gson.toJson(buildResponse(false, e.getMessage(), null)));
        } catch (InterchangeNotAllowedException e) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write(gson.toJson(buildResponse(false, e.getMessage(), null)));
        } catch (RecipientNotEligibleException | ActiveReservationQueueException | InvalidInterchangeException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(gson.toJson(buildResponse(false, e.getMessage(), null)));
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(buildResponse(false, e.getMessage(), null)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error processing interchange action: " + path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(buildResponse(false, "An error occurred while processing the interchange action.", null)));
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

