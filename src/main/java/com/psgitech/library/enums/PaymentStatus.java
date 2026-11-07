package com.psgitech.library.enums;

/**
 * Lifecycle states of a fine payment attempt.
 */
public enum PaymentStatus {
    INITIATED,
    PENDING_VERIFICATION,
    SUCCESS,
    REJECTED,
    CANCELLED,
    REFUNDED;

    public static PaymentStatus fromString(String value) {
        if (value == null) return INITIATED;
        for (PaymentStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return INITIATED;
    }
}

