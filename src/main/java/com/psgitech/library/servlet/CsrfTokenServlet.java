package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.util.CsrfTokenManager;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint for fetching or refreshing CSRF synchronizer tokens.
 * Restricted to active HTTP sessions.
 * URL Mapping: /csrf-token
 */
@WebServlet(name = "CsrfTokenServlet", urlPatterns = {"/csrf-token"})
public class CsrfTokenServlet extends HttpServlet {
    private Gson gson;

    @Override
    public void init() {
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required to obtain CSRF token.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        String token = CsrfTokenManager.getOrCreateToken(session);

        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("csrfToken", token);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(data));
    }
}
