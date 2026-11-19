package com.psgitech.library.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Server-side redirect servlet for legacy Discover URLs to the digital-library Home page (index.html).
 * Ensures bookmarks and external links never 404 when navigating to legacy discovery URLs.
 */
@WebServlet(name = "LegacyDiscoverRedirectServlet", urlPatterns = {"/discover", "/smart-discovery", "/smart-discovery.html"})
public class LegacyDiscoverRedirectServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String queryString = req.getQueryString();
        String target = req.getContextPath() + "/index.html";
        if (queryString != null && !queryString.trim().isEmpty()) {
            target += "?" + queryString;
        }
        resp.sendRedirect(target);
    }
}
