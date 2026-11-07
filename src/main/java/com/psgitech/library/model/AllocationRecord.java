package com.psgitech.library.model;

import com.psgitech.library.enums.AllocationStatus;
import com.psgitech.library.exception.InvalidAllocationException;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Domain model representing an automated book allocation record.
 * Demonstrates strict Encapsulation:
 * - State cannot be altered via arbitrary public setters.
 * - Transitions (COLLECTED, CANCELLED, EXPIRED) must pass business validation rules.
 */
public class AllocationRecord {
    private int allocationId;
    private int reservationId;
    private int studentId;
    private int bookId;
    private int copyId;
    private Integer returnTransactionId;
    private Integer borrowTransactionId;
    private AllocationStatus status;
    private Timestamp allocatedAt;
    private Timestamp expiresAt;
    private Timestamp collectedAt;
    private Timestamp cancelledAt;
    private Integer cancelledBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Display fields joined for convenience and presentation
    private String studentName;
    private String registerNumber;
    private String bookTitle;
    private String bookIsbn;
    private String copyCode;
    private String bookCoverUrl;

    public AllocationRecord() {
        this.status = AllocationStatus.ALLOCATED;
    }

    public AllocationRecord(int reservationId, int studentId, int bookId, int copyId,
                            Integer returnTransactionId, Timestamp expiresAt) {
        this.reservationId = reservationId;
        this.studentId = studentId;
        this.bookId = bookId;
        this.copyId = copyId;
        this.returnTransactionId = returnTransactionId;
        this.status = AllocationStatus.ALLOCATED;
        this.allocatedAt = new Timestamp(System.currentTimeMillis());
        this.expiresAt = expiresAt;
    }

    // --- Domain Transition Methods (Encapsulation & Business Rules) ---

    public boolean canBeCollected() {
        return this.status == AllocationStatus.ALLOCATED;
    }

    public boolean canBeReleased() {
        return this.status == AllocationStatus.ALLOCATED;
    }

    public boolean isExpired(Instant now) {
        if (this.expiresAt == null) return false;
        return now.isAfter(this.expiresAt.toInstant());
    }

    public void markCollected(int borrowTransactionId) {
        if (!canBeCollected()) {
            throw new InvalidAllocationException("Allocation #" + allocationId + " cannot be collected because status is " + status + ".");
        }
        this.status = AllocationStatus.COLLECTED;
        this.borrowTransactionId = borrowTransactionId;
        this.collectedAt = new Timestamp(System.currentTimeMillis());
    }

    public void markCancelled(int actingUserId) {
        if (!canBeReleased()) {
            throw new InvalidAllocationException("Allocation #" + allocationId + " cannot be released because status is " + status + ".");
        }
        this.status = AllocationStatus.CANCELLED;
        this.cancelledBy = actingUserId;
        this.cancelledAt = new Timestamp(System.currentTimeMillis());
    }

    public void markExpired() {
        if (this.status != AllocationStatus.ALLOCATED) {
            throw new InvalidAllocationException("Allocation #" + allocationId + " cannot be marked expired because status is " + status + ".");
        }
        this.status = AllocationStatus.EXPIRED;
    }

    // --- Getters and Controlled Setters ---

    public int getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(int allocationId) {
        this.allocationId = allocationId;
    }

    public int getReservationId() {
        return reservationId;
    }

    public void setReservationId(int reservationId) {
        this.reservationId = reservationId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getBookId() {
        return bookId;
    }

    public void setBookId(int bookId) {
        this.bookId = bookId;
    }

    public int getCopyId() {
        return copyId;
    }

    public void setCopyId(int copyId) {
        this.copyId = copyId;
    }

    public Integer getReturnTransactionId() {
        return returnTransactionId;
    }

    public void setReturnTransactionId(Integer returnTransactionId) {
        this.returnTransactionId = returnTransactionId;
    }

    public Integer getBorrowTransactionId() {
        return borrowTransactionId;
    }

    public void setBorrowTransactionId(Integer borrowTransactionId) {
        this.borrowTransactionId = borrowTransactionId;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public void setStatusDirectlyForHydration(AllocationStatus status) {
        this.status = status;
    }

    public Timestamp getAllocatedAt() {
        return allocatedAt;
    }

    public void setAllocatedAt(Timestamp allocatedAt) {
        this.allocatedAt = allocatedAt;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Timestamp getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Timestamp collectedAt) {
        this.collectedAt = collectedAt;
    }

    public Timestamp getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Timestamp cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Integer getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(Integer cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getRegisterNumber() {
        return registerNumber;
    }

    public void setRegisterNumber(String registerNumber) {
        this.registerNumber = registerNumber;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getBookIsbn() {
        return bookIsbn;
    }

    public void setBookIsbn(String bookIsbn) {
        this.bookIsbn = bookIsbn;
    }

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
    }

    public String getBookCoverUrl() {
        return bookCoverUrl;
    }

    public void setBookCoverUrl(String bookCoverUrl) {
        this.bookCoverUrl = bookCoverUrl;
    }
}

