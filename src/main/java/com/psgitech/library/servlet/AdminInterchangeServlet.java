package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.Role;
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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Staff-only monitoring endpoint for book interchange history.
 * URL Mapping: /api/admin/interchanges
 */
@WebServlet(name = "AdminInterchangeServlet", urlPatterns = {"/api/admin/interchanges"})
public class AdminInterchangeServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(AdminInterchangeServlet.class.getName());
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
            resp.getWriter().write(gson.toJson(buildResponse(false, "Authentication required.", null)));
            return;
        }

        if (currentUser.getRole() != Role.LIBRARIAN && currentUser.getRole() != Role.ADMIN) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write(gson.toJson(buildResponse(false, "Access Denied: Staff role required.", null)));
            return;
        }

        int limit = 50;
        int offset = 0;
        String limitParam = req.getParameter("limit");
        String offsetParam = req.getParameter("offset");
        String status = req.getParameter("status");

        if (limitParam != null && !limitParam.trim().isEmpty()) {
            try { limit = Integer.parseInt(limitParam.trim()); } catch (NumberFormatException ignored) {}
        }
        if (offsetParam != null && !offsetParam.trim().isEmpty()) {
            try { offset = Integer.parseInt(offsetParam.trim()); } catch (NumberFormatException ignored) {}
        }

        try {
            List<BookInterchange> list = interchangeService.getAllInterchanges(limit, offset, status);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(buildResponse(true, "Interchange records retrieved.", list)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving staff interchange list", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(buildResponse(false, "An error occurred fetching records.", null)));
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

