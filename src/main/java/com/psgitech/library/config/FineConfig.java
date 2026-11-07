package com.psgitech.library.config;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * Configuration settings for Feature 6: Fine Calculation and UPI Payment.
 * Adheres to decisions D1-D16 approved by the institution.
 */
public class FineConfig {
    public static final BigDecimal DEFAULT_FINE_PER_DAY = new BigDecimal("2.00");
    public static final int DEFAULT_GRACE_PERIOD_DAYS = 0;
    public static final BigDecimal DEFAULT_MAX_FINE_CAP = null; // Uncapped by default (D3)
    public static final String DEFAULT_CURRENCY = "INR";
    public static final ZoneId DEFAULT_LIBRARY_ZONE = ZoneId.of("Asia/Kolkata");
    public static final boolean DEFAULT_ALLOW_PARTIAL_PAYMENTS = true; // D6
    public static final BigDecimal DEFAULT_MIN_PARTIAL_PAYMENT = new BigDecimal("1.00");
    public static final BigDecimal DEFAULT_BORROW_BLOCK_THRESHOLD = BigDecimal.ZERO; // D9
    public static final int DEFAULT_INITIATION_TTL_MINUTES = 30; // D13
    public static final int DEFAULT_MAX_ATTEMPTS_PER_HOUR = 5; // D16

    public static final String DEFAULT_UPI_ID = "psgitech.library@icici";
    public static final String DEFAULT_PAYEE_NAME = "PSG iTech Library";
    public static final String DEFAULT_QR_IMAGE_PATH = "images/library-upi-qr.png";

    private static BigDecimal finePerDay = DEFAULT_FINE_PER_DAY;
    private static int gracePeriodDays = DEFAULT_GRACE_PERIOD_DAYS;
    private static BigDecimal maxFineCap = DEFAULT_MAX_FINE_CAP;
    private static ZoneId libraryZone = DEFAULT_LIBRARY_ZONE;
    private static boolean allowPartialPayments = DEFAULT_ALLOW_PARTIAL_PAYMENTS;
    private static BigDecimal minPartialPayment = DEFAULT_MIN_PARTIAL_PAYMENT;
    private static BigDecimal borrowBlockThreshold = DEFAULT_BORROW_BLOCK_THRESHOLD;
    private static int initiationTtlMinutes = DEFAULT_INITIATION_TTL_MINUTES;
    private static int maxAttemptsPerHour = DEFAULT_MAX_ATTEMPTS_PER_HOUR;
    private static String upiId = DEFAULT_UPI_ID;
    private static String payeeName = DEFAULT_PAYEE_NAME;
    private static String qrImagePath = DEFAULT_QR_IMAGE_PATH;

    public static BigDecimal getFinePerDay() {
        return finePerDay;
    }

    public static void setFinePerDay(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fine rate cannot be negative.");
        }
        finePerDay = rate;
    }

    public static int getGracePeriodDays() {
        return gracePeriodDays;
    }

    public static void setGracePeriodDays(int days) {
        if (days < 0) {
            throw new IllegalArgumentException("Grace period cannot be negative.");
        }
        gracePeriodDays = days;
    }

    public static BigDecimal getMaxFineCap() {
        return maxFineCap;
    }

    public static void setMaxFineCap(BigDecimal cap) {
        if (cap != null && cap.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Maximum fine cap cannot be negative.");
        }
        maxFineCap = cap;
    }

    public static ZoneId getLibraryZone() {
        return libraryZone;
    }

    public static void setLibraryZone(ZoneId zone) {
        if (zone == null) throw new IllegalArgumentException("Zone cannot be null.");
        libraryZone = zone;
    }

    public static boolean isAllowPartialPayments() {
        return allowPartialPayments;
    }

    public static void setAllowPartialPayments(boolean allow) {
        allowPartialPayments = allow;
    }

    public static BigDecimal getMinPartialPayment() {
        return minPartialPayment;
    }

    public static void setMinPartialPayment(BigDecimal min) {
        if (min == null || min.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Minimum payment must be greater than zero.");
        }
        minPartialPayment = min;
    }

    public static BigDecimal getBorrowBlockThreshold() {
        return borrowBlockThreshold;
    }

    public static void setBorrowBlockThreshold(BigDecimal threshold) {
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Threshold cannot be negative.");
        }
        borrowBlockThreshold = threshold;
    }

    public static int getInitiationTtlMinutes() {
        return initiationTtlMinutes;
    }

    public static void setInitiationTtlMinutes(int ttl) {
        if (ttl < 1) throw new IllegalArgumentException("TTL must be at least 1 minute.");
        initiationTtlMinutes = ttl;
    }

    public static int getMaxAttemptsPerHour() {
        return maxAttemptsPerHour;
    }

    public static void setMaxAttemptsPerHour(int max) {
        if (max < 1) throw new IllegalArgumentException("Max attempts must be at least 1.");
        maxAttemptsPerHour = max;
    }

    public static String getUpiId() {
        return upiId;
    }

    public static void setUpiId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("UPI ID cannot be blank.");
        }
        upiId = id.trim();
    }

    public static String getPayeeName() {
        return payeeName;
    }

    public static void setPayeeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Payee name cannot be blank.");
        }
        payeeName = name.trim();
    }

    public static String getQrImagePath() {
        return qrImagePath;
    }

    public static void setQrImagePath(String path) {
        qrImagePath = path;
    }

    public static void resetToDefaults() {
        finePerDay = DEFAULT_FINE_PER_DAY;
        gracePeriodDays = DEFAULT_GRACE_PERIOD_DAYS;
        maxFineCap = DEFAULT_MAX_FINE_CAP;
        libraryZone = DEFAULT_LIBRARY_ZONE;
        allowPartialPayments = DEFAULT_ALLOW_PARTIAL_PAYMENTS;
        minPartialPayment = DEFAULT_MIN_PARTIAL_PAYMENT;
        borrowBlockThreshold = DEFAULT_BORROW_BLOCK_THRESHOLD;
        initiationTtlMinutes = DEFAULT_INITIATION_TTL_MINUTES;
        maxAttemptsPerHour = DEFAULT_MAX_ATTEMPTS_PER_HOUR;
        upiId = DEFAULT_UPI_ID;
        payeeName = DEFAULT_PAYEE_NAME;
        qrImagePath = DEFAULT_QR_IMAGE_PATH;
    }
}

