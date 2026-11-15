package com.psgitech.library.model;

import com.psgitech.library.enums.ReservationStatus;
import com.psgitech.library.exception.InvalidReservationException;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Model representing a book queue reservation in the PSG iTech Library System.
 * Demonstrates OOP Encapsulation and Data Hiding.
 * Maps directly to table: reservations.
 */
public class Reservation {
    private Integer reservationId;
    private int studentId;
    private int bookId;
    private LocalDate reservationDate;
    private ReservationStatus status;
    private Timestamp cancelledAt;
    private Integer cancelledBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Supplementary display / DTO fields
    private String studentName;
    private String registerNumber;
    private String bookTitle;
    private String bookAuthor;
    private String bookIsbn;
    private String bookCoverUrl;
    private String coverImageUrl;
    private int queuePosition;
    private int totalWaitingInQueue;

    // Feature 5 Allocation Details (when status == ALLOCATED)
    private Integer allocationId;
    private Integer copyId;
    private String copyCode;
    private Timestamp allocationExpiresAt;
    private Timestamp allocatedAt;
    private AllocationResult releaseOutcome;

    // Feature Enhancement F4: Estimated Waiting Time
    private Integer estimatedWaitDays;
    private String estimatedWaitMessage;

    // Feature 12 Dynamic Borrowing Policy Preview
    private String policyCode;
    private Integer previewLoanDays;
    private String policyLabel;

    public Reservation() {
        this.status = ReservationStatus.WAITING;
        this.reservationDate = LocalDate.now();
    }

    public Reservation(int studentId, int bookId) {
        this();
        this.studentId = studentId;
        this.bookId = bookId;
    }

    public Reservation(Integer reservationId, int studentId, int bookId, LocalDate reservationDate,
                       ReservationStatus status, Timestamp cancelledAt, Integer cancelledBy,
                       Timestamp createdAt, Timestamp updatedAt) {
        this.reservationId = reservationId;
        this.studentId = studentId;
        this.bookId = bookId;
        this.reservationDate = reservationDate;
        this.status = status;
        this.cancelledAt = cancelledAt;
        this.cancelledBy = cancelledBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Domain Rule: WAITING and ALLOCATED reservations can be cancelled/released.
     */
    public boolean canBeCancelled() {
        return this.status == ReservationStatus.WAITING || this.status == ReservationStatus.ALLOCATED;
    }

    /**
     * Domain Transition: Marks the reservation as CANCELLED with audit metadata.
     * Throws InvalidReservationException if transition is illegal.
     */
    public void markAsCancelled(int cancelledByUserId) {
        if (!canBeCancelled()) {
            throw new InvalidReservationException("Reservation #" + reservationId + " cannot be cancelled because its status is " + status + ".");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = new Timestamp(System.currentTimeMillis());
        this.cancelledBy = cancelledByUserId;
    }

    // Getters and Encapsulated Setters

    public Integer getReservationId() {
        return reservationId;
    }

    public void setReservationId(Integer reservationId) {
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

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(LocalDate reservationDate) {
        this.reservationDate = reservationDate;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    /**
     * Package-private or protected setter for repository hydration only.
     */
    public void setStatus(ReservationStatus status) {
        this.status = status;
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

    public String getBookAuthor() {
        return bookAuthor;
    }

    public void setBookAuthor(String bookAuthor) {
        this.bookAuthor = bookAuthor;
    }

    public String getBookIsbn() {
        return bookIsbn;
    }

    public void setBookIsbn(String bookIsbn) {
        this.bookIsbn = bookIsbn;
    }

    public String getCoverImageUrl() {
        return coverImageUrl != null ? coverImageUrl : bookCoverUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
        this.bookCoverUrl = coverImageUrl;
    }

    public String getBookCoverUrl() {
        return bookCoverUrl != null ? bookCoverUrl : coverImageUrl;
    }

    public void setBookCoverUrl(String bookCoverUrl) {
        this.bookCoverUrl = bookCoverUrl;
        this.coverImageUrl = bookCoverUrl;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    public int getTotalWaitingInQueue() {
        return totalWaitingInQueue;
    }

    public void setTotalWaitingInQueue(int totalWaitingInQueue) {
        this.totalWaitingInQueue = totalWaitingInQueue;
    }

    public Integer getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(Integer allocationId) {
        this.allocationId = allocationId;
    }

    public Integer getCopyId() {
        return copyId;
    }

    public void setCopyId(Integer copyId) {
        this.copyId = copyId;
    }

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
    }

    public Timestamp getAllocationExpiresAt() {
        return allocationExpiresAt;
    }

    public void setAllocationExpiresAt(Timestamp allocationExpiresAt) {
        this.allocationExpiresAt = allocationExpiresAt;
    }

    public Timestamp getAllocatedAt() {
        return allocatedAt;
    }

    public void setAllocatedAt(Timestamp allocatedAt) {
        this.allocatedAt = allocatedAt;
    }

    public boolean isReadyForCollection() {
        return this.status == ReservationStatus.ALLOCATED;
    }

    public String getCopyIdentifier() {
        return this.copyCode;
    }

    public Timestamp getExpiresAt() {
        return this.allocationExpiresAt;
    }

    public AllocationResult getReleaseOutcome() {
        return releaseOutcome;
    }

    public void setReleaseOutcome(AllocationResult releaseOutcome) {
        this.releaseOutcome = releaseOutcome;
    }

    public Integer getEstimatedWaitDays() {
        return estimatedWaitDays;
    }

    public void setEstimatedWaitDays(Integer estimatedWaitDays) {
        this.estimatedWaitDays = estimatedWaitDays;
    }

    public String getEstimatedWaitMessage() {
        return estimatedWaitMessage;
    }

    public void setEstimatedWaitMessage(String estimatedWaitMessage) {
        this.estimatedWaitMessage = estimatedWaitMessage;
    }

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public Integer getPreviewLoanDays() {
        return previewLoanDays;
    }

    public void setPreviewLoanDays(Integer previewLoanDays) {
        this.previewLoanDays = previewLoanDays;
    }

    public String getPolicyLabel() {
        return policyLabel;
    }

    public void setPolicyLabel(String policyLabel) {
        this.policyLabel = policyLabel;
    }

    @Override
    public String toString() {
        return "Reservation{" +
                "reservationId=" + reservationId +
                ", studentId=" + studentId +
                ", bookId=" + bookId +
                ", status=" + status +
                ", queuePosition=" + queuePosition +
                '}';
    }
}
