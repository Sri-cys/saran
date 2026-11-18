package com.psgitech.library.config;

/**
 * Configuration for Razorpay Payment Gateway integration.
 * In production or test environments, Key ID is exposed to client-side Razorpay Checkout,
 * while Key Secret is strictly held on the Java backend for signature verification.
 */
public final class RazorpayConfig {

    public static final String KEY_ID = System.getProperty("razorpay.key.id", "rzp_test_U1hOr0s9TYhGRb");
    public static final String KEY_SECRET = System.getProperty("razorpay.key.secret", "2bekkNvTLar3OE2YEEjjP2xn");
    public static final String CURRENCY = "INR";
    public static final String MERCHANT_NAME = "PSG iTech Central Library";

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
