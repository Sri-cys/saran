package com.psgitech.library.model;

import com.psgitech.library.enums.InterchangeStatus;
import com.psgitech.library.exception.InvalidInterchangeException;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Domain entity representing a Student-to-Student Book Interchange.
 * Demonstrates strict OOP Encapsulation:
 * - State mutations (complete, reject, cancel) require business rule validation.
 * - Status cannot be altered via an unrestricted public setter.
 */
public class BookInterchange {
    private Integer interchangeId;
    private int transactionId;
    private int bookId;
    private int copyId;
    private int fromStudentId;
    private int toStudentId;
    private int initiatedBy;
    private Integer acceptedBy;
    private Integer rejectedBy;
    private Integer cancelledBy;
    private InterchangeStatus status;
    private LocalDate originalIssueDate;
    private LocalDate originalDueDate;
    private String requestNote;
    private String rejectionReason;
    private String cancellationReason;
    private Timestamp requestedAt;
    private Timestamp respondedAt;
    private Timestamp completedAt;
    private Timestamp cancelledAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Joined presentation fields
    private String fromStudentName;
    private String fromStudentRegNo;
    private String toStudentName;
    private String toStudentRegNo;
    private String bookTitle;
    private String bookAuthor;
    private String copyCode;
    private String coverImageUrl;

    public BookInterchange() {
        this.status = InterchangeStatus.REQUESTED;
    }

    public BookInterchange(int transactionId, int bookId, int copyId, int fromStudentId, int toStudentId,
                           int initiatedBy, LocalDate originalIssueDate, LocalDate originalDueDate, String requestNote) {
        this();
        this.transactionId = transactionId;
        this.bookId = bookId;
        this.copyId = copyId;
        this.fromStudentId = fromStudentId;
        this.toStudentId = toStudentId;
        this.initiatedBy = initiatedBy;
        this.originalIssueDate = originalIssueDate;
        this.originalDueDate = originalDueDate;
        this.requestNote = requestNote;
        this.requestedAt = new Timestamp(System.currentTimeMillis());
    }

    // --- Domain State Transition Methods (Encapsulation) ---

    public boolean isPending() {
        return this.status == InterchangeStatus.REQUESTED;
    }

    public void complete(int acceptedByUserId) {
        if (!isPending()) {
            throw new InvalidInterchangeException("Cannot complete interchange in status: " + this.status);
        }
        this.status = InterchangeStatus.COMPLETED;
        this.acceptedBy = acceptedByUserId;
        Timestamp now = new Timestamp(System.currentTimeMillis());
        this.respondedAt = now;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void reject(int rejectedByUserId, String reason) {
        if (!isPending()) {
            throw new InvalidInterchangeException("Cannot reject interchange in status: " + this.status);
        }
        this.status = InterchangeStatus.REJECTED;
        this.rejectedBy = rejectedByUserId;
        this.rejectionReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Rejected by recipient";
        Timestamp now = new Timestamp(System.currentTimeMillis());
        this.respondedAt = now;
        this.updatedAt = now;
    }

    public void cancel(int cancelledByUserId, String reason) {
        if (!isPending()) {
            throw new InvalidInterchangeException("Cannot cancel interchange in status: " + this.status);
        }
        this.status = InterchangeStatus.CANCELLED;
        this.cancelledBy = cancelledByUserId;
        this.cancellationReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Cancelled by sender";
        Timestamp now = new Timestamp(System.currentTimeMillis());
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    // --- Getters & Setters ---

    public Integer getInterchangeId() {
        return interchangeId;
    }

    public void setInterchangeId(Integer interchangeId) {
        this.interchangeId = interchangeId;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
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

    public int getFromStudentId() {
        return fromStudentId;
    }

    public void setFromStudentId(int fromStudentId) {
        this.fromStudentId = fromStudentId;
    }

    public int getToStudentId() {
        return toStudentId;
    }

    public void setToStudentId(int toStudentId) {
        this.toStudentId = toStudentId;
    }

    public int getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(int initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public Integer getAcceptedBy() {
        return acceptedBy;
    }

    public void setAcceptedBy(Integer acceptedBy) {
        this.acceptedBy = acceptedBy;
    }

    public Integer getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(Integer rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public Integer getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(Integer cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public InterchangeStatus getStatus() {
        return status;
    }

    public void setStatus(InterchangeStatus status) {
        this.status = status;
    }

    public LocalDate getOriginalIssueDate() {
        return originalIssueDate;
    }

    public void setOriginalIssueDate(LocalDate originalIssueDate) {
        this.originalIssueDate = originalIssueDate;
    }

    public LocalDate getOriginalDueDate() {
        return originalDueDate;
    }

    public void setOriginalDueDate(LocalDate originalDueDate) {
        this.originalDueDate = originalDueDate;
    }

    public String getRequestNote() {
        return requestNote;
    }

    public void setRequestNote(String requestNote) {
        this.requestNote = requestNote;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Timestamp getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Timestamp requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Timestamp getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Timestamp respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Timestamp getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }

    public Timestamp getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Timestamp cancelledAt) {
        this.cancelledAt = cancelledAt;
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

    public String getFromStudentName() {
        return fromStudentName;
    }

    public void setFromStudentName(String fromStudentName) {
        this.fromStudentName = fromStudentName;
    }

    public String getFromStudentRegNo() {
        return fromStudentRegNo;
    }

    public void setFromStudentRegNo(String fromStudentRegNo) {
        this.fromStudentRegNo = fromStudentRegNo;
    }

    public String getToStudentName() {
        return toStudentName;
    }

    public void setToStudentName(String toStudentName) {
        this.toStudentName = toStudentName;
    }

    public String getToStudentRegNo() {
        return toStudentRegNo;
    }

    public void setToStudentRegNo(String toStudentRegNo) {
        this.toStudentRegNo = toStudentRegNo;
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

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }
}

