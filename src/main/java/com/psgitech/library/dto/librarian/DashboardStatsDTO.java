package com.psgitech.library.dto.librarian;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for Librarian Dashboard real-time KPIs and invariant tracking.
 * Encapsulation: private fields with getters.
 */
public class DashboardStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int totalCopies;
    private int availableCopies;
    private int borrowedCopies;
    private int overdueCopies;
    private int pendingReservations;

    private int allocatedCopies;
    private int lostCopies;
    private int damagedCopies;
    private int transferPendingCopies;
    private int retiredCopies;

    private boolean invariantSatisfied;
    private String inconsistencyNotice;
    private String lastUpdated;

    private List<Map<String, Object>> bookDepartmentStats;
    private List<Map<String, Object>> studentDepartmentStats;

    public DashboardStatsDTO() {}

    public int getTotalCopies() { return totalCopies; }
    public void setTotalCopies(int totalCopies) { this.totalCopies = totalCopies; }

    public int getAvailableCopies() { return availableCopies; }
    public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }

    public int getBorrowedCopies() { return borrowedCopies; }
    public void setBorrowedCopies(int borrowedCopies) { this.borrowedCopies = borrowedCopies; }

    public int getOverdueCopies() { return overdueCopies; }
    public void setOverdueCopies(int overdueCopies) { this.overdueCopies = overdueCopies; }

    public int getPendingReservations() { return pendingReservations; }
    public void setPendingReservations(int pendingReservations) { this.pendingReservations = pendingReservations; }

    public int getAllocatedCopies() { return allocatedCopies; }
    public void setAllocatedCopies(int allocatedCopies) { this.allocatedCopies = allocatedCopies; }

    public int getLostCopies() { return lostCopies; }
    public void setLostCopies(int lostCopies) { this.lostCopies = lostCopies; }

    public int getDamagedCopies() { return damagedCopies; }
    public void setDamagedCopies(int damagedCopies) { this.damagedCopies = damagedCopies; }

    public int getTransferPendingCopies() { return transferPendingCopies; }
    public void setTransferPendingCopies(int transferPendingCopies) { this.transferPendingCopies = transferPendingCopies; }

    public int getRetiredCopies() { return retiredCopies; }
    public void setRetiredCopies(int retiredCopies) { this.retiredCopies = retiredCopies; }

    public boolean isInvariantSatisfied() { return invariantSatisfied; }
    public void setInvariantSatisfied(boolean invariantSatisfied) { this.invariantSatisfied = invariantSatisfied; }

    public String getInconsistencyNotice() { return inconsistencyNotice; }
    public void setInconsistencyNotice(String inconsistencyNotice) { this.inconsistencyNotice = inconsistencyNotice; }

    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }

    public List<Map<String, Object>> getBookDepartmentStats() { return bookDepartmentStats; }
    public void setBookDepartmentStats(List<Map<String, Object>> bookDepartmentStats) { this.bookDepartmentStats = bookDepartmentStats; }

    public List<Map<String, Object>> getStudentDepartmentStats() { return studentDepartmentStats; }
    public void setStudentDepartmentStats(List<Map<String, Object>> studentDepartmentStats) { this.studentDepartmentStats = studentDepartmentStats; }
}
