package com.psgitech.library.enums;

/**
 * State machine statuses for Loss and Damage cases.
 * Open states block borrowing/interchange eligibility.
 * Terminal states close the obligation.
 */
public enum CaseStatus {
    REPORTED,
    ASSESSED,
    PAYMENT_PENDING,
    REPLACEMENT_PENDING,
    REPLACEMENT_SUBMITTED,
    RESOLVED_PAID,
    RESOLVED_REPLACED,
    WAIVED,
    CANCELLED;

    public boolean isOpen() {
        return this == REPORTED || this == ASSESSED || this == PAYMENT_PENDING 
                || this == REPLACEMENT_PENDING || this == REPLACEMENT_SUBMITTED;
    }

    public boolean isTerminal() {
        return this == RESOLVED_PAID || this == RESOLVED_REPLACED || this == WAIVED || this == CANCELLED;
    }

    public boolean isBlockingObligation() {
        // REPORTED is unconfirmed so it does not block; ASSESSED or later in-progress cases block
        return this == ASSESSED || this == PAYMENT_PENDING 
                || this == REPLACEMENT_PENDING || this == REPLACEMENT_SUBMITTED;
    }

    public static CaseStatus fromString(String value) {
        if (value == null) return null;
        for (CaseStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return null;
    }
}
