package com.psgitech.library.config;

import java.time.LocalDate;

/**
 * Configurable Borrowing Policy for PSG iTech Library System.
 * Encapsulates the borrowing period and active quota rules.
 * Modular design facilitates future integration with Feature 12 (Dynamic Exam-Season Policy).
 */
public class BorrowingPolicy {
    public static final int DEFAULT_BORROWING_DAYS = 14;
    public static final int DEFAULT_MAX_ACTIVE_BORROWINGS = 3;
    public static final int DEFAULT_MAX_ACTIVE_RESERVATIONS = 3;
    public static final int DEFAULT_ALLOCATION_COLLECTION_HOURS = 24;

    private static int borrowingDurationDays = DEFAULT_BORROWING_DAYS;
    private static int maxActiveBorrowingLimit = DEFAULT_MAX_ACTIVE_BORROWINGS;
    private static int maxActiveReservationLimit = DEFAULT_MAX_ACTIVE_RESERVATIONS;
    private static int allocationCollectionHours = DEFAULT_ALLOCATION_COLLECTION_HOURS;

    public static int getAllocationCollectionHours() {
        return allocationCollectionHours;
    }

    public static void setAllocationCollectionHours(int hours) {
        if (hours < 1) {
            throw new IllegalArgumentException("Allocation collection hours must be at least 1.");
        }
        allocationCollectionHours = hours;
    }

    public static int getStandardBorrowingDurationDays() {
        return borrowingDurationDays;
    }

    public static void setBorrowingDurationDays(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Borrowing duration must be at least 1 day.");
        }
        borrowingDurationDays = days;
    }

    public static int getMaxActiveBorrowingLimit() {
        return maxActiveBorrowingLimit;
    }

    public static void setMaxActiveBorrowingLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Borrowing limit must be at least 1.");
        }
        maxActiveBorrowingLimit = limit;
    }

    public static int getMaxActiveReservationLimit() {
        return maxActiveReservationLimit;
    }

    public static void setMaxActiveReservationLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Reservation limit must be at least 1.");
        }
        maxActiveReservationLimit = limit;
    }

    /**
     * Resets policies to institutional standard defaults.
     */
    public static void resetToDefaults() {
        borrowingDurationDays = DEFAULT_BORROWING_DAYS;
        maxActiveBorrowingLimit = DEFAULT_MAX_ACTIVE_BORROWINGS;
        maxActiveReservationLimit = DEFAULT_MAX_ACTIVE_RESERVATIONS;
        allocationCollectionHours = DEFAULT_ALLOCATION_COLLECTION_HOURS;
    }

    /**
     * Checks if a student has outstanding unpaid fines.
     * Integrates with Feature 6 FineRepository to enforce borrowing block threshold (Decision D9).
     */
    public static boolean hasOutstandingFines(int studentId) {
        return new com.psgitech.library.repository.FineRepository().hasOutstandingFines(studentId);
    }

    public static boolean hasOutstandingFines(int studentId, java.sql.Connection conn) {
        if (conn == null) {
            return hasOutstandingFines(studentId);
        }
        try {
            com.psgitech.library.repository.FineRepository fineRepo = new com.psgitech.library.repository.FineRepository();
            return fineRepo.hasOutstandingFines(studentId, conn);
        } catch (java.sql.SQLException e) {
            java.util.logging.Logger.getLogger(BorrowingPolicy.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Error checking outstanding fines for student #" + studentId, e);
            throw new RuntimeException("Database error checking student fine dues: " + e.getMessage(), e);
        }
    }

    public static boolean hasUnresolvedCase(int studentId) {
        try (java.sql.Connection conn = com.psgitech.library.util.DBConnection.getConnection()) {
            return hasUnresolvedCase(studentId, conn);
        } catch (java.sql.SQLException e) {
            java.util.logging.Logger.getLogger(BorrowingPolicy.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Error checking unresolved cases for student #" + studentId, e);
            throw new RuntimeException("Database error checking student case dues: " + e.getMessage(), e);
        }
    }

    public static boolean hasUnresolvedCase(int studentId, java.sql.Connection conn) {
        if (conn == null) {
            return hasUnresolvedCase(studentId);
        }
        try {
            return new com.psgitech.library.repository.LossDamageCaseRepository().hasUnresolvedCase(studentId, conn);
        } catch (java.sql.SQLException e) {
            java.util.logging.Logger.getLogger(BorrowingPolicy.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Error checking unresolved cases for student #" + studentId, e);
            throw new RuntimeException("Database error checking student case dues: " + e.getMessage(), e);
        }
    }

    public static boolean hasBlockingObligation(int studentId, java.sql.Connection conn) {
        return hasOutstandingFines(studentId, conn) || hasUnresolvedCase(studentId, conn);
    }

    /**
     * Calculates the calculated due date from an issue date.
     */
    public static LocalDate calculateDueDate(LocalDate issueDate) {
        if (issueDate == null) {
            issueDate = LocalDate.now();
        }
        return issueDate.plusDays(borrowingDurationDays);
    }
}

