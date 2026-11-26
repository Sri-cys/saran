package com.psgitech.library.config;

/**
 * Configuration for Razorpay Payment Gateway integration.
 * In production or test environments, Key ID is exposed to client-side Razorpay Checkout,
 * while Key Secret is strictly held on the Java backend for signature verification.
 */
public final class RazorpayConfig {

    public static final String KEY_ID = resolveKeyId();
    public static final String KEY_SECRET = resolveKeySecret();
    public static final String CURRENCY = "INR";
    public static final String MERCHANT_NAME = "PSG iTech Central Library";

    // Payment Mode: 'RAZORPAY' (default live gateway with mock bank Success/Failure) vs 'DEMO'
    public static final String PAYMENT_MODE = resolvePaymentMode();

    private static String resolvePaymentMode() {
        String env = System.getenv("PAYMENT_MODE");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim().toUpperCase();
        }
        return System.getProperty("payment.mode", "RAZORPAY").toUpperCase();
    }

    public static boolean isDemoMode() {
        return "DEMO".equalsIgnoreCase(PAYMENT_MODE);
    }

    private static String resolveKeyId() {
        String env = System.getenv("RAZORPAY_KEY_ID");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return System.getProperty("razorpay.key.id", "rzp_test_U1hOr0s9TYhGRb");
    }

    private static String resolveKeySecret() {
        String env = System.getenv("RAZORPAY_KEY_SECRET");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return System.getProperty("razorpay.key.secret", "2bekkNvTLar3OE2YEEjjP2xn");
    }

    // Switch to enable simulated gateway responses for offline automated tests
    private static volatile boolean mockGatewayEnabled = false;

    private RazorpayConfig() {}

    public static boolean isMockGatewayEnabled() {
        return mockGatewayEnabled;
    }

    public static void setMockGatewayEnabled(boolean enabled) {
        mockGatewayEnabled = enabled;
    }
}
