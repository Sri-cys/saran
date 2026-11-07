package com.psgitech.library.enums;

/**
 * Enumeration representing the lifecycle state of a book reservation.
 * Values:
 * - WAITING: Student is in the FIFO queue waiting for a copy.
 * - ALLOCATED: A returned copy has been set aside for collection (Feature 5).
 * - FULFILLED: The student collected and borrowed the physical copy.
 * - CANCELLED: The reservation was cancelled by student or staff.
 * - EXPIRED: The allocation window lapsed without collection.
 */
public enum ReservationStatus {
    WAITING,
    ALLOCATED,
    FULFILLED,
    CANCELLED,
    EXPIRED;

    public static ReservationStatus fromString(String value) {
        if (value == null) return WAITING;
        for (ReservationStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return WAITING;
    }
}

