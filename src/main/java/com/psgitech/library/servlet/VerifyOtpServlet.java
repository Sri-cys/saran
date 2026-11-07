package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.model.OtpPurpose;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.service.OtpService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "VerifyOtpServlet", urlPatterns = {"/register/verify-otp"})
public class VerifyOtpServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(VerifyOtpServlet.class.getName());
    private OtpService otpService;
    private UserRepository userRepository;
    private Gson gson;

    @Override
    public void init() {
        this.otpService = new OtpService();
        this.userRepository = new UserRepository();
        this.gson = new Gson();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Integer userId = null;
        String email = null;
        String otp = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            try {
                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                if (json.has("userId")) userId = json.get("userId").getAsInt();
                if (json.has("email")) email = json.get("email").getAsString();
                if (json.has("otp")) otp = json.get("otp").getAsString();
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Invalid JSON format: " + e.getMessage());
                resp.getWriter().write(gson.toJson(err));
                return;
            }
        } else {
            String uIdStr = req.getParameter("userId");
            if (uIdStr != null && !uIdStr.trim().isEmpty()) {
                try { userId = Integer.parseInt(uIdStr.trim()); } catch (NumberFormatException ignored) {}
            }
            email = req.getParameter("email");
            otp = req.getParameter("otp");
        }

        Map<String, Object> result = new HashMap<>();

        if (userId == null || userId <= 0) {
            if (email != null && !email.trim().isEmpty()) {
                User u = userRepository.findByEmail(email.trim());
                if (u != null) {
                    userId = u.getUserId();
                }
            }
        }

        if (userId == null || userId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "Valid user ID or registered email is required.");
            resp.getWriter().write(gson.toJson(result));
            return;
        }

        if (otp == null || otp.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "Verification code is required.");
            resp.getWriter().write(gson.toJson(result));
            return;
        }

        try {
            boolean verified = otpService.verifyOtp(OtpPurpose.EMAIL_VERIFICATION, userId, otp.trim());
            if (verified) {
                userRepository.markEmailVerified(userId);
                resp.setStatus(HttpServletResponse.SC_OK);
                result.put("success", true);
                result.put("message", "Email verified successfully! You may now log in.");
                result.put("redirect", req.getContextPath() + "/login.html?verified=true");
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("success", false);
                result.put("message", "Invalid verification code.");
            }
            resp.getWriter().write(gson.toJson(result));

        } catch (IllegalArgumentException | IllegalStateException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error verifying OTP for userId=" + userId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            result.put("message", "An unexpected error occurred during verification. Please try again.");
            resp.getWriter().write(gson.toJson(result));
        }
    }
}

