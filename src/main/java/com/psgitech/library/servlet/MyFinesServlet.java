package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.psgitech.library.enums.Role;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.model.Payment;
import com.psgitech.library.model.User;
import com.psgitech.library.service.FineService;
import com.psgitech.library.service.PaymentService;
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

/**
 * Servlet providing fine summary, fine records, projected fines, and payment history for students.
 * URL Mapping: /my-fines, /fines/my
 */
@WebServlet(name = "MyFinesServlet", urlPatterns = {"/my-fines", "/fines/my"})
public class MyFinesServlet extends HttpServlet {
    private FineService fineService;
    private PaymentService paymentService;
    private Gson gson;

    @Override
    public void init() {
        this.fineService = new FineService();
        this.paymentService = new PaymentService();
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
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required. Please log in.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        int targetStudentId = currentUser.getUserId();
        if (currentUser.getRole() == Role.LIBRARIAN || currentUser.getRole() == Role.ADMIN) {
            String studentIdParam = req.getParameter("studentId");
            if (studentIdParam != null && !studentIdParam.trim().isEmpty()) {
                try {
                    targetStudentId = Integer.parseInt(studentIdParam.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        try {
            Map<String, Object> summary = fineService.getStudentFineSummary(targetStudentId);
            List<FineRecord> fines = fineService.getFinesByStudent(targetStudentId);
            List<Map<String, Object>> projectedFines = fineService.getProjectedFinesForStudent(targetStudentId);
            List<Payment> payments = paymentService.getPaymentsByStudent(targetStudentId);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("summary", summary);
            responseData.put("fines", fines);
            responseData.put("projectedFines", projectedFines);
            responseData.put("payments", payments);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Error loading fine details: " + e.getMessage());
            resp.getWriter().write(gson.toJson(err));
        }
    }
}

