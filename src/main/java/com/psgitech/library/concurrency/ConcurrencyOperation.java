package com.psgitech.library.concurrency;

/**
 * Enumeration of classified state-changing business operations.
 * Used for route classification, metric tracking, and concurrency governor statistics.
 */
public enum ConcurrencyOperation {
    BOOK_ISSUE("Issue physical copy or collect allocated reservation"),
    BOOK_RETURN("Return physical copy, assess fine, auto-allocate to reservation"),
    RESERVATION_CREATE("Place student book reservation in queue"),
    RESERVATION_CANCEL("Cancel queue reservation or release allocated copy"),
    PAYMENT_MUTATION("Student payment initiation, cancellation, or UTR submission"),
    PAYMENT_VERIFICATION("Librarian/Admin payment verification, rejection, dispute, or refund"),
    FINE_WAIVER("Admin fine waiver adjustment"),
    BOOK_CATALOG_MUTATION("Add, update, or archive book titles or physical copies"),
    BOOK_COPY_MUTATION("Add additional physical book copies"),
    USER_ADMIN_MUTATION("Admin user account creation, status change, or role update"),
    USER_PROFILE_MUTATION("User profile update or password change"),
    BOOK_INTERCHANGE("Student-to-student book interchange request, acceptance, rejection, or cancellation");

    private final String description;

    ConcurrencyOperation(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

