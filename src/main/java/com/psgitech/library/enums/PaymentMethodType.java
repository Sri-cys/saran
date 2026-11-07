package com.psgitech.library.enums;

/**
 * Supported payment method types for Feature 6.
 * Currently supports UPI_MANUAL per decision D5.
 */
public enum PaymentMethodType {
    UPI_MANUAL;

    public static PaymentMethodType fromString(String value) {
        if (value == null) return UPI_MANUAL;
        for (PaymentMethodType method : values()) {
            if (method.name().equalsIgnoreCase(value.trim())) {
                return method;
            }
        }
        return UPI_MANUAL;
    }
}

