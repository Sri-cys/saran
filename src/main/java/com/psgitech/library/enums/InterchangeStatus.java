package com.psgitech.library.enums;

/**
 * Lifecycle states for student-to-student book interchange.
 *
 * Allowed Transitions:
 * - REQUESTED -> COMPLETED
 * - REQUESTED -> REJECTED
 * - REQUESTED -> CANCELLED
 */
public enum InterchangeStatus {
    REQUESTED,
    COMPLETED,
    REJECTED,
    CANCELLED;

    public static InterchangeStatus fromString(String value) {
        if (value == null) return REQUESTED;
        for (InterchangeStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return REQUESTED;
    }
}

