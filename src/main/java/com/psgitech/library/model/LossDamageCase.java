package com.psgitech.library.model;

import com.psgitech.library.enums.*;
import com.psgitech.library.exception.InvalidCaseStateException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Domain entity representing a Lost or Damaged book resolution case.
 * Enforces strict OOP Encapsulation and Data Hiding.
 * State changes must go through domain transition methods.
 */
public class LossDamageCase {
    private Integer caseId;
    private CaseType caseType;
    private CaseStatus status;
    private int borrowTransactionId;
    private int studentId;
    private int bookId;
    private int copyId;
    private int reportedBy;
    private Timestamp reportedAt;
    private String reportNote;
    private DamageSeverity severity;
    private CopyDisposition copyDisposition;
    private Integer assessedBy;
    private Timestamp assessedAt;
    private BigDecimal priceUsed;
    private Integer ageDays;
    private boolean ageFlag;
    private BigDecimal depreciationApplied;
    private BigDecimal processingFee;
    private BigDecimal severityFraction;
    private BigDecimal computedAmount;
    private BigDecimal finalAmount;
    private Integer overrideBy;
    private String overrideReason;
    private ResolutionChoice resolutionChoice;
    private Integer fineId;
    private Date replacementDeadline;
    private Integer replacementCopyId;
    private String rejectionReason;
    private Timestamp resolvedAt;
    private Integer resolvedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Presentation joins
    private String studentName;
    private String registerNumber;
    private String bookTitle;
    private String bookIsbn;
    private String copyCode;

    public LossDamageCase() {
        this.status = CaseStatus.REPORTED;
    }

    public static LossDamageCase createReported(int borrowTransactionId, int studentId, int bookId, int copyId,
                                                int reportedBy, String reportNote) {
        LossDamageCase c = new LossDamageCase();
        c.caseType = CaseType.LOST;
        c.status = CaseStatus.REPORTED;
        c.borrowTransactionId = borrowTransactionId;
        c.studentId = studentId;
        c.bookId = bookId;
        c.copyId = copyId;
        c.reportedBy = reportedBy;
        c.reportNote = reportNote;
        c.reportedAt = new Timestamp(System.currentTimeMillis());
        return c;
    }

    public static LossDamageCase createDamagedAssessment(int borrowTransactionId, int studentId, int bookId, int copyId,
                                                         int assessedBy, DamageSeverity severity, CopyDisposition disposition,
                                                         CompensationBreakdown breakdown, String note) {
        LossDamageCase c = new LossDamageCase();
        c.caseType = CaseType.DAMAGED;
        c.status = CaseStatus.ASSESSED;
        c.borrowTransactionId = borrowTransactionId;
        c.studentId = studentId;
        c.bookId = bookId;
        c.copyId = copyId;
        c.reportedBy = assessedBy;
        c.reportedAt = new Timestamp(System.currentTimeMillis());
        c.reportNote = note;
        c.severity = severity;
        c.copyDisposition = disposition;
        c.applyAssessment(assessedBy, breakdown);
        return c;
    }

    // Domain Transitions
    public void assessLost(int assessorId, CompensationBreakdown breakdown) {
        if (this.status != CaseStatus.REPORTED && this.status != null) {
            throw new InvalidCaseStateException("Cannot assess case #" + caseId + ": expected status REPORTED but was " + this.status);
        }
        this.status = CaseStatus.ASSESSED;
        applyAssessment(assessorId, breakdown);
    }

    private void applyAssessment(int assessorId, CompensationBreakdown breakdown) {
        this.assessedBy = assessorId;
        this.assessedAt = new Timestamp(System.currentTimeMillis());
        this.priceUsed = breakdown.getPriceUsed();
        this.ageDays = breakdown.getAgeDays();
        this.ageFlag = breakdown.isAgeFlag();
        this.depreciationApplied = breakdown.getDepreciationApplied();
        this.processingFee = breakdown.getProcessingFee();
        this.severityFraction = breakdown.getSeverityFraction();
        this.computedAmount = breakdown.getComputedAmount();
        this.finalAmount = breakdown.getComputedAmount();
    }

    public void overrideAmount(int adminId, BigDecimal newAmount, String reason) {
        if (this.status != CaseStatus.ASSESSED && this.status != CaseStatus.PAYMENT_PENDING && this.status != CaseStatus.REPLACEMENT_PENDING) {
            throw new InvalidCaseStateException("Override only permitted in ASSESSED or PENDING status (current: " + this.status + ").");
        }
        if (newAmount == null || newAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Override amount must be non-negative.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Override reason is required.");
        }
        this.overrideBy = adminId;
        this.overrideReason = reason.trim();
        this.finalAmount = newAmount.setScale(2, RoundingMode.HALF_UP);
    }

    public void choosePayment(int fineId) {
        if (this.status != CaseStatus.ASSESSED && this.status != CaseStatus.REPLACEMENT_PENDING) {
            throw new InvalidCaseStateException("Cannot choose payment: case is in status " + this.status);
        }
        this.status = CaseStatus.PAYMENT_PENDING;
        this.resolutionChoice = ResolutionChoice.PAYMENT;
        this.fineId = fineId;
    }

    public void chooseReplacement(LocalDate deadline) {
        if (this.status != CaseStatus.ASSESSED) {
            throw new InvalidCaseStateException("Cannot choose replacement: case is in status " + this.status);
        }
        this.status = CaseStatus.REPLACEMENT_PENDING;
        this.resolutionChoice = ResolutionChoice.REPLACEMENT;
        this.replacementDeadline = Date.valueOf(deadline);
    }

    public void receiveReplacement() {
        if (this.status != CaseStatus.REPLACEMENT_PENDING) {
            throw new InvalidCaseStateException("Cannot receive replacement: case is in status " + this.status);
        }
        this.status = CaseStatus.REPLACEMENT_SUBMITTED;
    }

    public void acceptReplacement(int replacementCopyId, int librarianId) {
        if (this.status != CaseStatus.REPLACEMENT_SUBMITTED && this.status != CaseStatus.REPLACEMENT_PENDING) {
            throw new InvalidCaseStateException("Cannot accept replacement: case is in status " + this.status);
        }
        this.status = CaseStatus.RESOLVED_REPLACED;
        this.replacementCopyId = replacementCopyId;
        this.resolvedAt = new Timestamp(System.currentTimeMillis());
        this.resolvedBy = librarianId;
    }

    public void rejectReplacement(String reason) {
        if (this.status != CaseStatus.REPLACEMENT_SUBMITTED && this.status != CaseStatus.REPLACEMENT_PENDING) {
            throw new InvalidCaseStateException("Cannot reject replacement: case is in status " + this.status);
        }
        this.status = CaseStatus.ASSESSED; // returns to ASSESSED so student may pay
        this.rejectionReason = reason;
    }

    public void markResolvedPaid(int resolverId) {
        if (this.status != CaseStatus.PAYMENT_PENDING) {
            throw new InvalidCaseStateException("Cannot mark paid: case is in status " + this.status);
        }
        this.status = CaseStatus.RESOLVED_PAID;
        this.resolvedAt = new Timestamp(System.currentTimeMillis());
        this.resolvedBy = resolverId;
    }

    public void markWaived(int adminId) {
        if (this.status != CaseStatus.PAYMENT_PENDING && this.status != CaseStatus.ASSESSED) {
            throw new InvalidCaseStateException("Cannot mark waived: case is in status " + this.status);
        }
        this.status = CaseStatus.WAIVED;
        this.resolvedAt = new Timestamp(System.currentTimeMillis());
        this.resolvedBy = adminId;
    }

    public void cancel(String reason) {
        if (this.status != CaseStatus.REPORTED) {
            throw new InvalidCaseStateException("Only REPORTED cases can be cancelled (current: " + this.status + ").");
        }
        this.status = CaseStatus.CANCELLED;
        this.rejectionReason = reason;
        this.resolvedAt = new Timestamp(System.currentTimeMillis());
    }

    // Getters and Setters for Repository Mapping
    public Integer getCaseId() { return caseId; }
    public void setCaseId(Integer caseId) { this.caseId = caseId; }

    public CaseType getCaseType() { return caseType; }
    public void setCaseType(CaseType caseType) { this.caseType = caseType; }

    public CaseStatus getStatus() { return status; }
    public void setStatusDirectly(CaseStatus status) { this.status = status; } // For DAO hydration only

    public int getBorrowTransactionId() { return borrowTransactionId; }
    public void setBorrowTransactionId(int borrowTransactionId) { this.borrowTransactionId = borrowTransactionId; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }

    public int getCopyId() { return copyId; }
    public void setCopyId(int copyId) { this.copyId = copyId; }

    public int getReportedBy() { return reportedBy; }
    public void setReportedBy(int reportedBy) { this.reportedBy = reportedBy; }

    public Timestamp getReportedAt() { return reportedAt; }
    public void setReportedAt(Timestamp reportedAt) { this.reportedAt = reportedAt; }

    public String getReportNote() { return reportNote; }
    public void setReportNote(String reportNote) { this.reportNote = reportNote; }

    public DamageSeverity getSeverity() { return severity; }
    public void setSeverity(DamageSeverity severity) { this.severity = severity; }

    public CopyDisposition getCopyDisposition() { return copyDisposition; }
    public void setCopyDisposition(CopyDisposition copyDisposition) { this.copyDisposition = copyDisposition; }

    public Integer getAssessedBy() { return assessedBy; }
    public void setAssessedBy(Integer assessedBy) { this.assessedBy = assessedBy; }

    public Timestamp getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Timestamp assessedAt) { this.assessedAt = assessedAt; }

    public BigDecimal getPriceUsed() { return priceUsed; }
    public void setPriceUsed(BigDecimal priceUsed) { this.priceUsed = priceUsed; }

    public Integer getAgeDays() { return ageDays; }
    public void setAgeDays(Integer ageDays) { this.ageDays = ageDays; }

    public boolean isAgeFlag() { return ageFlag; }
    public void setAgeFlag(boolean ageFlag) { this.ageFlag = ageFlag; }

    public BigDecimal getDepreciationApplied() { return depreciationApplied; }
    public void setDepreciationApplied(BigDecimal depreciationApplied) { this.depreciationApplied = depreciationApplied; }

    public BigDecimal getProcessingFee() { return processingFee; }
    public void setProcessingFee(BigDecimal processingFee) { this.processingFee = processingFee; }

    public BigDecimal getSeverityFraction() { return severityFraction; }
    public void setSeverityFraction(BigDecimal severityFraction) { this.severityFraction = severityFraction; }

    public BigDecimal getComputedAmount() { return computedAmount; }
    public void setComputedAmount(BigDecimal computedAmount) { this.computedAmount = computedAmount; }

    public BigDecimal getFinalAmount() { return finalAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }

    public Integer getOverrideBy() { return overrideBy; }
    public void setOverrideBy(Integer overrideBy) { this.overrideBy = overrideBy; }

    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String overrideReason) { this.overrideReason = overrideReason; }

    public ResolutionChoice getResolutionChoice() { return resolutionChoice; }
    public void setResolutionChoice(ResolutionChoice resolutionChoice) { this.resolutionChoice = resolutionChoice; }

    public Integer getFineId() { return fineId; }
    public void setFineId(Integer fineId) { this.fineId = fineId; }

    public Date getReplacementDeadline() { return replacementDeadline; }
    public void setReplacementDeadline(Date replacementDeadline) { this.replacementDeadline = replacementDeadline; }

    public Integer getReplacementCopyId() { return replacementCopyId; }
    public void setReplacementCopyId(Integer replacementCopyId) { this.replacementCopyId = replacementCopyId; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Timestamp getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }

    public Integer getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Integer resolvedBy) { this.resolvedBy = resolvedBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRegisterNumber() { return registerNumber; }
    public void setRegisterNumber(String registerNumber) { this.registerNumber = registerNumber; }

    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }

    public String getBookIsbn() { return bookIsbn; }
    public void setBookIsbn(String bookIsbn) { this.bookIsbn = bookIsbn; }

    public String getCopyCode() { return copyCode; }
    public void setCopyCode(String copyCode) { this.copyCode = copyCode; }
}
