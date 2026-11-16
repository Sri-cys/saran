package com.psgitech.library.servlet;

import com.google.gson.Gson;
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

@WebServlet(name = "MyCasesServlet", urlPatterns = {"/cases/my"})
public class MyCasesServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(MyCasesServlet.class.getName());

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

        try {
            List<LossDamageCase> cases = lossDamageService.getMyCases(currentUser.getUserId());
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("cases", cases);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(res));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving cases for student #" + currentUser.getUserId(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Failed to load cases: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}
