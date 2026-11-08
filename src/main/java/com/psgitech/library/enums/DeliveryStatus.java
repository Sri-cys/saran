package com.psgitech.library.enums;

/**
 * State machine for email delivery outbox records.
 */
public enum DeliveryStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    SKIPPED;

    public static DeliveryStatus fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return DeliveryStatus.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
