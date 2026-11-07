package com.psgitech.library.service;

import com.psgitech.library.config.FineConfig;
import com.psgitech.library.exception.InvalidPaymentException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Service for generating UPI payment URIs, deep links, and validating transaction references.
 */
public class UpiService {

    /**
     * Builds a standard UPI deep-link URI:
     * upi://pay?pa={upiId}&pn={payeeName}&am={amount}&cu=INR&tn={paymentCode}
     */
    public String buildUpiUri(BigDecimal amount, String paymentCode) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        if (paymentCode == null || paymentCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment code is required for UPI note.");
        }

        String upiId = FineConfig.getUpiId();
        String payeeName = FineConfig.getPayeeName();
        String formattedAmount = amount.setScale(2).toPlainString();

        return "upi://pay?pa=" + urlEncode(upiId) +
                "&pn=" + urlEncode(payeeName) +
                "&am=" + urlEncode(formattedAmount) +
                "&cu=INR" +
                "&tn=" + urlEncode(paymentCode.trim());
    }

    /**
     * Validates and normalizes student-provided UPI transaction reference ID / UTR.
     * Standard UPI UTRs are 12 digits, but banks occasionally issue alphanumeric 10-18 char reference codes.
     */
    public String validateAndNormalizeReference(String rawReference) {
        if (rawReference == null || rawReference.trim().isEmpty()) {
            throw new InvalidPaymentException("UPI transaction reference ID (UTR) cannot be blank.");
        }
        String clean = rawReference.trim().toUpperCase();

        // Enforce safe alphanumeric string of 8-32 characters
        if (!clean.matches("^[A-Z0-9]{8,32}$")) {
            throw new InvalidPaymentException("Invalid UPI reference ID format. Enter a valid 8-32 character alphanumeric UTR / transaction ID.");
        }
        return clean;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

