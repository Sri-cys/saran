package com.psgitech.library;

import com.psgitech.library.model.OtpPurpose;
import com.psgitech.library.repository.OtpRepository;
import com.psgitech.library.service.OtpService;
import com.psgitech.library.util.DBConnection;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.Assert.*;

public class OtpServiceTest {

    private OtpService otpService;
    private OtpRepository otpRepository;

    @Before
    public void setUp() {
        DBConnection.initializeDatabase();
        this.otpRepository = new OtpRepository();
        this.otpService = new OtpService(otpRepository, "test-pepper-secret-2026");
        cleanTestOtps();
    }

    private void cleanTestOtps() {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM otp_codes WHERE destination_email LIKE '%@test-otp.com'")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            // ignore if table empty
        }
    }

    @Test
    public void testHashOtpProducesDeterministic64HexChars() {
        String hash1 = otpService.hashOtp("123456");
        String hash2 = otpService.hashOtp("123456");
        String hashDifferent = otpService.hashOtp("654321");

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hashDifferent);
    }

    @Test
    public void testGenerateAndVerifySuccessfulFlow() throws Exception {
        int testUserId = 99991;
        String email = "student1@test-otp.com";

        String code = otpService.generateAndSaveOtp(OtpPurpose.EMAIL_VERIFICATION, testUserId, email);
        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));

        // Verify with correct code
        boolean verified = otpService.verifyOtp(OtpPurpose.EMAIL_VERIFICATION, testUserId, code);
        assertTrue(verified);

        // Code is now consumed, verifying again should fail
        try {
            otpService.verifyOtp(OtpPurpose.EMAIL_VERIFICATION, testUserId, code);
            fail("Expected exception for already consumed OTP");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No active verification code found"));
        }
    }

    @Test
    public void testCooldownPreventsImmediateResend() throws Exception {
        int testUserId = 99992;
        String email = "student2@test-otp.com";

        String code1 = otpService.generateAndSaveOtp(OtpPurpose.EMAIL_VERIFICATION, testUserId, email);
        assertNotNull(code1);

        try {
            otpService.generateAndSaveOtp(OtpPurpose.EMAIL_VERIFICATION, testUserId, email);
            fail("Expected cooldown restriction");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Please wait"));
        }
    }

    @Test
    public void testInvalidAttemptsDecrementAndExhaustLimit() throws Exception {
        int testUserId = 99993;
        String email = "student3@test-otp.com";

        String code = otpService.generateAndSaveOtp(OtpPurpose.EMAIL_VERIFICATION, testUserId, email);
        assertNotNull(code);

        String wrongCode = code.equals("000000") ? "111111" : "000000";

        // Attempts 1 to 4 should report remaining attempts
        for (int i = 1; i <= 4; i++) {
            try {
                otpService.verifyOtp(OtpPurpose.EMAIL_VERIFICATION, testUserId, wrongCode);
                fail("Expected verification failure on attempt " + i);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("attempt(s) remaining"));
            }
        }

        // Attempt 5 reaches max attempts (limit reached)
        try {
            otpService.verifyOtp(OtpPurpose.EMAIL_VERIFICATION, testUserId, wrongCode);
            fail("Expected limit reached on attempt 5");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Maximum attempts reached"));
        }
    }
}
