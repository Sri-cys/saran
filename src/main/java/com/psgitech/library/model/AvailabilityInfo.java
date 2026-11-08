package com.psgitech.library.model;

/**
 * Immutable/encapsulated value object representing live physical copy availability
 * and reservation queue depth for a book in the catalog.
 */
public class AvailabilityInfo {
    private final int totalActiveCopies;
    private final int availableCopies;
    private final int borrowedCopies;
    private final int allocatedCopies;
    private final int transferPendingCopies;
    private final int waitingReservations;
    private final String status; // AVAILABLE, UNAVAILABLE, WAITING, NO_COPIES

    public AvailabilityInfo(int totalActiveCopies, int availableCopies, int borrowedCopies,
                            int allocatedCopies, int transferPendingCopies, int waitingReservations) {
        this.totalActiveCopies = totalActiveCopies;
        this.availableCopies = availableCopies;
        this.borrowedCopies = borrowedCopies;
        this.allocatedCopies = allocatedCopies;
        this.transferPendingCopies = transferPendingCopies;
        this.waitingReservations = waitingReservations;

        if (totalActiveCopies == 0) {
            this.status = "NO_COPIES";
        } else if (availableCopies > 0) {
            this.status = "AVAILABLE";
        } else if (waitingReservations > 0) {
            this.status = "WAITING";
        } else {
            this.status = "UNAVAILABLE";
        }
    }

    public int getTotalActiveCopies() {
        return totalActiveCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    public int getBorrowedCopies() {
        return borrowedCopies;
    }

    public int getAllocatedCopies() {
        return allocatedCopies;
    }

    public int getTransferPendingCopies() {
        return transferPendingCopies;
    }

    public int getWaitingReservations() {
        return waitingReservations;
    }

    public String getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return availableCopies > 0;
    }
}

