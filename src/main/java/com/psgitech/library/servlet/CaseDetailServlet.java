package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.CaseEvent;
import com.psgitech.library.model.LossDamageCase;
import com.psgitech.library.model.User;
import com.psgitech.library.service.LossDamageService;
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

@WebServlet(name = "CaseDetailServlet", urlPatterns = {"/cases/detail"})
public class CaseDetailServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(CaseDetailServlet.class.getName());

    private LossDamageService lossDamageService;
    private Gson gson;

    @Override
    public void init() {
        this.lossDamageService = new LossDamageService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute("user") : null;
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required. Please sign in.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        String idStr = req.getParameter("caseId");
        if (idStr == null || idStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "caseId parameter is required.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        int caseId;
        try {
            caseId = Integer.parseInt(idStr.trim());
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Invalid caseId parameter.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        try {
            LossDamageCase c = lossDamageService.getCaseById(caseId);
            if (c == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Case #" + caseId + " not found.");
                resp.getWriter().write(gson.toJson(err));
                return;
            }

            if (currentUser.getRole() == Role.STUDENT && c.getStudentId() != currentUser.getUserId()) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Access Denied: You may only view your own cases.");
                resp.getWriter().write(gson.toJson(err));
                return;
            }

            List<CaseEvent> events = lossDamageService.getCaseEvents(caseId);

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("case", c);
            res.put("events", events);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(res));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving case detail #" + caseId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Failed to retrieve case details: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}
