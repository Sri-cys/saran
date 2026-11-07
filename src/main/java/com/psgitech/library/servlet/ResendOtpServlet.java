package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.model.OtpPurpose;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.service.MailService;
import com.psgitech.library.service.MailServiceFactory;
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

@WebServlet(name = "ResendOtpServlet", urlPatterns = {"/register/resend-otp"})
public class ResendOtpServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(ResendOtpServlet.class.getName());
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
        }

        Map<String, Object> result = new HashMap<>();

        User user = null;
        if (userId != null && userId > 0) {
            user = userRepository.findById(userId);
        } else if (email != null && !email.trim().isEmpty()) {
            user = userRepository.findByEmail(email.trim());
        }

        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            result.put("success", false);
            result.put("message", "User account not found.");
            resp.getWriter().write(gson.toJson(result));
            return;
        }

        if (user.isEmailVerified()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "This email is already verified. You can log in.");
            resp.getWriter().write(gson.toJson(result));
            return;
        }

        try {
            String rawOtp = otpService.generateAndSaveOtp(OtpPurpose.EMAIL_VERIFICATION, user.getUserId(), user.getEmail());
            MailService mailService = MailServiceFactory.getMailService();
            mailService.send(user.getEmail(), "PSG iTech Library - New Verification Code",
                    "Dear " + user.getName() + ",\n\n" +
                    "Here is your new 6-digit verification code: " + rawOtp + "\n\n" +
                    "This code will expire in 10 minutes.\n\n" +
                    "Regards,\nPSG iTech Library Administration");

            resp.setStatus(HttpServletResponse.SC_OK);
            result.put("success", true);
            result.put("message", "A new verification code has been sent to " + user.getEmail() + ".");
            resp.getWriter().write(gson.toJson(result));

        } catch (IllegalStateException e) {
            // Rate limit or cooldown triggered
            resp.setStatus(429); // Too Many Requests
            result.put("success", false);
            result.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error resending OTP for userId=" + user.getUserId(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            result.put("message", "An unexpected error occurred while resending verification code.");
            resp.getWriter().write(gson.toJson(result));
        }
    }
}
