package com.psgitech.library.config;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration helper for demonstration OTP routing.
 * Allows controlled routing of OTP notifications to accessible demonstration inboxes
 * during vivas/presentations, while keeping real student identities, database integrity,
 * single-use token invalidation, and cryptographic expiry intact.
 */
public final class DemoOtpConfig {
    private static final Logger LOGGER = Logger.getLogger(DemoOtpConfig.class.getName());
    private static final String CONFIG_FILE = "/demo-otp.properties";

    private static boolean enabled = true;
    private static final Properties properties = new Properties();

    static {
        loadProperties();
    }

    private static synchronized void loadProperties() {
        try (InputStream in = DemoOtpConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                properties.load(in);
                String enabledStr = properties.getProperty("demo.otp.enabled", "true");
                enabled = Boolean.parseBoolean(enabledStr.trim());
                LOGGER.info("DemoOtpConfig loaded successfully. Demo mode enabled: " + enabled);
            } else {
                LOGGER.info("No demo-otp.properties found on classpath; demo routing disabled.");
                enabled = false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading demo-otp.properties", e);
            enabled = false;
        }
    }

    public static boolean isDemoEnabled() {
        return enabled;
    }

    /**
     * Resolves the target email address for sending OTPs.
     * If demo routing is enabled and a mapping exists for the student identifier or email,
     * returns the accessible demonstration email; otherwise returns the original email.
     */
    public static String resolveDeliveryEmail(String studentIdentifier, String defaultEmail) {
        if (!enabled) {
            return defaultEmail;
        }

        // 1. Check system property overrides
        if (studentIdentifier != null) {
            String sysProp = System.getProperty("demo.otp.override." + studentIdentifier.trim().toLowerCase());
            if (sysProp != null && !sysProp.trim().isEmpty()) {
                LOGGER.info("Routing OTP for student identifier [" + studentIdentifier + "] to demo inbox: " + sysProp);
                return sysProp.trim();
            }
        }

        if (defaultEmail != null) {
            String sysProp = System.getProperty("demo.otp.override." + defaultEmail.trim().toLowerCase());
            if (sysProp != null && !sysProp.trim().isEmpty()) {
                LOGGER.info("Routing OTP for student email [" + defaultEmail + "] to demo inbox: " + sysProp);
                return sysProp.trim();
            }
        }

        // 2. Check loaded properties file
        if (studentIdentifier != null) {
            String mapped = properties.getProperty("demo.otp.override." + studentIdentifier.trim());
            if (mapped != null && !mapped.trim().isEmpty()) {
                LOGGER.info("Routing OTP for student identifier [" + studentIdentifier + "] to demo inbox: " + mapped.trim());
                return mapped.trim();
            }
        }

        if (defaultEmail != null) {
            String mapped = properties.getProperty("demo.otp.override." + defaultEmail.trim().toLowerCase());
            if (mapped != null && !mapped.trim().isEmpty()) {
                LOGGER.info("Routing OTP for student email [" + defaultEmail + "] to demo inbox: " + mapped.trim());
                return mapped.trim();
            }
        }

        return defaultEmail;
    }

    private DemoOtpConfig() {}
}
