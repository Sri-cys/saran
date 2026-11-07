package com.psgitech.library.service;

import com.psgitech.library.model.OtpCode;
import com.psgitech.library.model.OtpPurpose;
import com.psgitech.library.model.OtpStatus;
import com.psgitech.library.repository.OtpRepository;
import com.psgitech.library.util.DBConnection;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OtpService {
    private static final Logger LOGGER = Logger.getLogger(OtpService.class.getName());

    public static final int OTP_LENGTH = 6;
    public static final int OTP_EXPIRY_MINUTES = 10;
    public static final int RESEND_COOLDOWN_SECONDS = 60;
    public static final int MAX_CODES_PER_HOUR = 5;
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    private static final String DEFAULT_PEPPER = "psgitech-library-otp-secret-pepper-2026";
    private final String pepperSecret;
    private final OtpRepository otpRepository;
    private final SecureRandom secureRandom;

    public OtpService() {
        this(new OtpRepository(), loadPepperSecret());
    }

    public OtpService(OtpRepository otpRepository, String pepperSecret) {
        this.otpRepository = otpRepository;
        this.pepperSecret = (pepperSecret != null && !pepperSecret.isEmpty()) ? pepperSecret : DEFAULT_PEPPER;
        this.secureRandom = new SecureRandom();
    }

    private static String loadPepperSecret() {
        String env = System.getenv("OTP_PEPPER_SECRET");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        Properties props = new Properties();
        try (InputStream in = OtpService.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {}
        return props.getProperty("otp.pepper.secret", DEFAULT_PEPPER).trim();
    }

    /**
     * Generates a 6-digit numeric OTP and stores its HMAC-SHA256 hash.
     * Enforces resend cooldown (60s) and hourly quota (max 5).
     * Returns the raw 6-digit string so callers can dispatch it to the user.
     */
    public String generateAndSaveOtp(OtpPurpose purpose, int subjectId, String destinationEmail) throws SQLException {
        if (destinationEmail == null || destinationEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination email cannot be blank.");
        }
        destinationEmail = destinationEmail.trim().toLowerCase();

        // 1. Check hourly rate limit
        int hourlyCount = otpRepository.countCodesInLastHour(destinationEmail);
        if (hourlyCount >= MAX_CODES_PER_HOUR) {
            throw new IllegalStateException("Maximum hourly verification code requests exceeded (max " + MAX_CODES_PER_HOUR + " per hour). Please try again later.");
        }

        // 2. Check 60-second cooldown
        Timestamp recentCreated = otpRepository.findMostRecentCreatedAt(purpose, subjectId);
        if (recentCreated != null) {
            long diffSeconds = (System.currentTimeMillis() - recentCreated.getTime()) / 1000;
            if (diffSeconds < RESEND_COOLDOWN_SECONDS) {
                long waitTime = RESEND_COOLDOWN_SECONDS - diffSeconds;
                throw new IllegalStateException("Please wait " + waitTime + " seconds before requesting another code.");
            }
        }

        // 3. Generate 6-digit code
        int num = secureRandom.nextInt(1_000_000);
        String rawCode = String.format("%06d", num);
        String codeHash = hashOtp(rawCode);

        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + (OTP_EXPIRY_MINUTES * 60 * 1000L));
        OtpCode code = new OtpCode(purpose, subjectId, destinationEmail, codeHash, expiresAt, DEFAULT_MAX_ATTEMPTS);

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Invalidate any currently active OTP for this purpose & subject
                otpRepository.supersedeExisting(conn, purpose, subjectId);
                otpRepository.createOtp(conn, code);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        return rawCode;
    }

    /**
     * Verifies the provided raw OTP against the active record for (purpose, subjectId).
     * Atomically increments attempt counter on failure.
     * Consumes the code on success.
     */
    public boolean verifyOtp(OtpPurpose purpose, int subjectId, String rawCode) throws SQLException {
        if (rawCode == null || rawCode.trim().length() != OTP_LENGTH) {
            return false;
        }
        rawCode = rawCode.trim();

        OtpCode activeCode = otpRepository.findActive(purpose, subjectId);
        if (activeCode == null) {
            throw new IllegalArgumentException("No active verification code found or code has expired.");
        }

        // Check if code has expired by deadline
        if (activeCode.getExpiresAt().getTime() <= System.currentTimeMillis()) {
            try (Connection conn = DBConnection.getConnection()) {
                otpRepository.markExpired(conn, activeCode.getOtpId());
            }
            throw new IllegalArgumentException("Verification code has expired. Please request a new code.");
        }

        // Check if max attempts already reached
        if (activeCode.getAttempts() >= activeCode.getMaxAttempts()) {
            try (Connection conn = DBConnection.getConnection()) {
                otpRepository.markExpired(conn, activeCode.getOtpId());
            }
            throw new IllegalArgumentException("Maximum verification attempts exceeded. Please request a new code.");
        }

        String inputHash = hashOtp(rawCode);
        boolean matches = MessageDigest.isEqual(
                inputHash.getBytes(StandardCharsets.UTF_8),
                activeCode.getCodeHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            // Increment attempts in separate transaction
            int currentAttempts = otpRepository.incrementAttempts(activeCode.getOtpId());
            int remaining = Math.max(0, activeCode.getMaxAttempts() - currentAttempts);
            if (remaining == 0) {
                throw new IllegalArgumentException("Invalid verification code. Maximum attempts reached. Code invalidated.");
            } else {
                throw new IllegalArgumentException("Invalid verification code. " + remaining + " attempt(s) remaining.");
            }
        }

        // Success: consume code
        try (Connection conn = DBConnection.getConnection()) {
            otpRepository.markConsumed(conn, activeCode.getOtpId());
        }
        return true;
    }

    public String hashOtp(String rawCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(pepperSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(rawCode.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hmacBytes.length * 2);
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error computing HMAC-SHA256: " + e.getMessage(), e);
        }
    }
}
