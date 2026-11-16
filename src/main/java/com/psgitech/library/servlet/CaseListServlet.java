package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.LossDamageCase;
import com.psgitech.library.model.PagedResult;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "CaseListServlet", urlPatterns = {"/cases"})
public class CaseListServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(CaseListServlet.class.getName());

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

        if (currentUser.getRole() != Role.LIBRARIAN && currentUser.getRole() != Role.ADMIN) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access Denied: Staff role required to view loss/damage case list.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        String status = req.getParameter("status");
        String type = req.getParameter("type");
        String registerNumber = req.getParameter("registerNumber");

        LocalDate fromDate = null;
        String fromStr = req.getParameter("fromDate");
        if (fromStr != null && !fromStr.trim().isEmpty()) {
            try { fromDate = LocalDate.parse(fromStr.trim()); } catch (Exception ignored) {}
        }

        LocalDate toDate = null;
        String toStr = req.getParameter("toDate");
        if (toStr != null && !toStr.trim().isEmpty()) {
            try { toDate = LocalDate.parse(toStr.trim()); } catch (Exception ignored) {}
        }

        int page = 1;
        String pageStr = req.getParameter("page");
        if (pageStr != null && !pageStr.trim().isEmpty()) {
            try { page = Math.max(1, Integer.parseInt(pageStr.trim())); } catch (NumberFormatException ignored) {}
        }

        int pageSize = 20;
        String sizeStr = req.getParameter("pageSize");
        if (sizeStr != null && !sizeStr.trim().isEmpty()) {
            try { pageSize = Math.max(1, Math.min(100, Integer.parseInt(sizeStr.trim()))); } catch (NumberFormatException ignored) {}
        }

        try {
            PagedResult<LossDamageCase> result = lossDamageService.getCases(
                    status, type, registerNumber, fromDate, toDate, page, pageSize
            );

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("cases", result.getItems());
            res.put("total", result.getTotalCount());
            res.put("page", result.getPage());
            res.put("pageSize", result.getPageSize());
            res.put("totalPages", result.getTotalPages());

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(res));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving cases list", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Failed to retrieve cases: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}
