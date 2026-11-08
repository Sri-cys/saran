package com.psgitech.library.enums;

/**
 * Functional categories for user grouping, tabs, and preference toggles.
 */
public enum NotificationCategory {
    RESERVATION("Reservations & Hold Shelf", "Updates on queue positions, hold arrivals, and cancellations."),
    ALLOCATION_REMINDERS("Allocation Hold Reminders", "Urgent reminders for books awaiting physical collection."),
    BORROWING("Borrowings & Renewals", "Confirmations for book issues, desk returns, and loan extensions."),
    DUE_REMINDERS("Due Date & Overdue Reminders", "Courtesy notices before due dates and overdue loan alerts."),
    FINES_PAYMENTS("Fines & UPI Payments", "Overdue fine assessments, UPI payment verifications, and refunds."),
    INTERCHANGE("Peer Book Transfers", "Incoming requests, acceptance confirmations, and handover updates."),
    SYSTEM("Security & System Alerts", "Password changes, account security, and critical system alerts."),
    STAFF("Staff Operations", "Circulation desk alerts for librarians and administrators."),
    DISCOVERY("Catalog Discovery", "Opt-in alerts for new books matching your interests.");

    private final String displayName;
    private final String description;

    NotificationCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static NotificationCategory fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return NotificationCategory.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
