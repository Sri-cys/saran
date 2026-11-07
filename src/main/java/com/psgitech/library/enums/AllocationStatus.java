package com.psgitech.library.enums;

/**
 * Enumeration representing the lifecycle state of an automatic book allocation.
 *
 * Status Lifecycle:
 * - ALLOCATED: The physical copy is currently held for an eligible student awaiting collection.
 * - COLLECTED: The student has checked out the allocated copy at the desk (transitions to BORROWED).
 * - EXPIRED: The student failed to collect within the pickup window (Feature 9).
 * - CANCELLED: The allocation was released by the student or library staff.
 */
public enum AllocationStatus {
    ALLOCATED,
    COLLECTED,
    EXPIRED,
    CANCELLED;

    public static AllocationStatus fromString(String text) {
        if (text == null) return ALLOCATED;
        for (AllocationStatus status : AllocationStatus.values()) {
            if (status.name().equalsIgnoreCase(text.trim())) {
                return status;
            }
        }
        return ALLOCATED;
    }
}

