package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.InterchangeStatus;
import com.psgitech.library.model.BookInterchange;
import com.psgitech.library.model.OtpPurpose;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.BookInterchangeRepository;
import com.psgitech.library.repository.UserRepository;
import com.psgitech.library.service.EmailService;
import com.psgitech.library.service.OtpService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "InterchangeOtpServlet", urlPatterns = {"/interchanges/otp/generate", "/api/transfers/otp/generate"})
public class InterchangeOtpServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InterchangeOtpServlet.class.getName());

    private OtpService otpService;
    private BookInterchangeRepository interchangeRepository;
    private UserRepository userRepository;
    private Gson gson;

    @Override
    public void init() {
        this.otpService = new OtpService();
        this.interchangeRepository = new BookInterchangeRepository();
        this.userRepository = new UserRepository();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute("user") : null;
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        Integer interchangeId = null;
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
                if (json.has("interchangeId")) interchangeId = json.get("interchangeId").getAsInt();
            } catch (Exception ignored) {}
        } else {
            String idStr = req.getParameter("interchangeId");
            if (idStr != null && !idStr.trim().isEmpty()) {
                try { interchangeId = Integer.parseInt(idStr.trim()); } catch (NumberFormatException ignored) {}
            }
        }

        Map<String, Object> result = new HashMap<>();

        if (interchangeId == null || interchangeId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("success", false);
            result.put("message", "Valid interchangeId is required.");
            resp.getWriter().write(gson.toJson(result));
            return;
        }

        try {
            BookInterchange interchange = interchangeRepository.findById(interchangeId);
            if (interchange == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                result.put("success", false);
                result.put("message", "Interchange request #" + interchangeId + " not found.");
                resp.getWriter().write(gson.toJson(result));
                return;
            }

            if (interchange.getStatus() != InterchangeStatus.REQUESTED) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("success", false);
                result.put("message", "Interchange is in " + interchange.getStatus() + " status. Cannot generate handover code.");
                resp.getWriter().write(gson.toJson(result));
                return;
            }

            if (currentUser.getUserId() != interchange.getFromStudentId() && currentUser.getUserId() != interchange.getToStudentId()) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                result.put("success", false);
                result.put("message", "You are not a participant in this interchange transfer.");
                resp.getWriter().write(gson.toJson(result));
                return;
            }

            User sender = userRepository.findById(interchange.getFromStudentId());
            if (sender == null || sender.getEmail() == null || sender.getEmail().trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("success", false);
                result.put("message", "Sender institutional email could not be resolved.");
                resp.getWriter().write(gson.toJson(result));
                return;
            }

            String rawOtp = otpService.generateAndSaveOtp(OtpPurpose.INTERCHANGE_HANDOVER, interchangeId, sender.getEmail());

            EmailService emailService = EmailService.getInstance();
            emailService.sendEmail(sender.getEmail(), "PSG iTech Library - Handover Verification Code",
                    "Dear " + sender.getName() + ",\n\n" +
                    "Your physical book handover verification code for transfer #" + interchangeId + " is: " + rawOtp + "\n\n" +
                    "Please provide this 6-digit code to the recipient student (" + (interchange.getToStudentName() != null ? interchange.getToStudentName() : "recipient") + ") only when you physically hand over the book copy.\n" +
                    "This code will expire in 10 minutes.\n\n" +
                    "Regards,\nPSG iTech Library Administration");

            resp.setStatus(HttpServletResponse.SC_OK);
            result.put("success", true);
            result.put("otp", rawOtp);
            result.put("message", "Handover verification code has been dispatched to sender's institutional email address.");
            resp.getWriter().write(gson.toJson(result));

        } catch (IllegalStateException e) {
            resp.setStatus(429);
            result.put("success", false);
            result.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating handover OTP for interchange #" + interchangeId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("success", false);
            result.put("message", "Failed to generate handover code: " + e.getMessage());
            resp.getWriter().write(gson.toJson(result));
        }
    }
}
