package com.psgitech.library.enums;

/**
 * Enumeration representing the lifecycle state of a book borrowing transaction.
 * Strictly adheres to Part 6: Overdue transactions remain ACTIVE until the physical copy
 * is actually returned. Overdue status is computed dynamically using the due date.
 */
public enum BorrowStatus {
    ACTIVE,
    RETURNED;

    public static BorrowStatus fromString(String value) {
        if (value == null) return ACTIVE;
        for (BorrowStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return ACTIVE;
    }
}

