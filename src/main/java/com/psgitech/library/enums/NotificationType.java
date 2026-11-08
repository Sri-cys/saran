package com.psgitech.library.enums;

/**
 * Enumeration of all discrete event notification types in the PSG iTech Library System.
 */
public enum NotificationType {
    // Reservation & Allocation
    RESERVATION_CREATED,
    ALLOCATION_READY,
    ALLOCATION_REMINDER,
    RESERVATION_CANCELLED,
    RESERVATION_EXPIRED,

    // Borrowing & Loans
    LOAN_ISSUED,
    LOAN_RETURNED,
    LOAN_RENEWED,
    RENEWAL_REJECTED,
    DUE_SOON,
    OVERDUE,

    // Fines & Payments
    FINE_CREATED,
    PAYMENT_SUBMITTED,
    PAYMENT_PENDING_VERIFICATION,
    PAYMENT_VERIFIED,
    PAYMENT_REJECTED,
    FINE_WAIVED,
    REFUND_RECORDED,

    // Interchanges
    INTERCHANGE_REQUEST_RECEIVED,
    INTERCHANGE_COMPLETED,
    INTERCHANGE_REJECTED,
    INTERCHANGE_CANCELLED,
    INTERCHANGE_INVALIDATED,

    // System & Security
    PASSWORD_CHANGED,

    // Stretch Discovery Alerts
    DISCOVERY_NEW_BOOK;

    public static NotificationType fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return NotificationType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
