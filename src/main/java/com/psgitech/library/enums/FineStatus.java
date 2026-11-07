package com.psgitech.library.enums;

/**
 * Lifecycle states of an overdue book fine record.
 * Invariant: original_amount = paid_amount + waived_amount + outstanding_amount
 */
public enum FineStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    WAIVED;

    public static FineStatus fromString(String value) {
        if (value == null) return UNPAID;
        for (FineStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return UNPAID;
    }
}

