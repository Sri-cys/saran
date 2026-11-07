package com.psgitech.library.servlet;

import com.psgitech.library.enums.Role;
import com.psgitech.library.model.BookCopy;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.service.BarcodeService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet(name = "BarcodeServlet", urlPatterns = {"/books/copies/barcode"})
public class BarcodeServlet extends HttpServlet {
    private BarcodeService barcodeService;
    private BookRepository bookRepository;

    @Override
    public void init() {
        this.barcodeService = new BarcodeService();
        this.bookRepository = new BookRepository();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("user") : null;

        // Verify Staff Authorization (Librarian or Admin)
        if (user == null) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required to generate copy barcodes.");
            return;
        }
        if (user.getRole() != Role.LIBRARIAN && user.getRole() != Role.ADMIN) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Librarian or Administrator role required.");
            return;
        }

        String copyCode = req.getParameter("code");
        String copyIdStr = req.getParameter("copyId");

        if ((copyCode == null || copyCode.trim().isEmpty()) && (copyIdStr != null && !copyIdStr.trim().isEmpty())) {
            try {
                int copyId = Integer.parseInt(copyIdStr.trim());
                BookCopy copy = bookRepository.findCopyById(copyId);
                if (copy != null) {
                    copyCode = copy.getCopyCode();
                }
            } catch (NumberFormatException ignored) {}
        }

        if (copyCode == null || copyCode.trim().isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing copy code or copy ID.");
            return;
        }

        try {
            int moduleWidth = 2;
            int barHeight = 60;
            String widthStr = req.getParameter("moduleWidth");
            if (widthStr != null) {
                try { moduleWidth = Math.max(1, Math.min(5, Integer.parseInt(widthStr.trim()))); } catch (NumberFormatException ignored) {}
            }
            String heightStr = req.getParameter("height");
            if (heightStr != null) {
                try { barHeight = Math.max(20, Math.min(150, Integer.parseInt(heightStr.trim()))); } catch (NumberFormatException ignored) {}
            }

            String svgContent = barcodeService.generateBarcodeSvg(copyCode.trim(), moduleWidth, barHeight);

            resp.setContentType("image/svg+xml; charset=UTF-8");
            resp.setHeader("Cache-Control", "public, max-age=86400");
            resp.getWriter().write(svgContent);

        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to render barcode: " + e.getMessage());
        }
    }
}

