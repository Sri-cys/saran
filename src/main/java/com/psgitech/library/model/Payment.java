package com.psgitech.library.model;

import com.psgitech.library.enums.PaymentMethodType;
import com.psgitech.library.enums.PaymentStatus;
import com.psgitech.library.exception.InvalidPaymentException;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Domain Model representing a student fine payment attempt.
 * Enforces valid state transitions and encapsulated mutations.
 */
public class Payment {
    private Integer paymentId;
    private int fineId;
    private int studentId;
    private BigDecimal amount;
    private PaymentMethodType method;
    private PaymentStatus status;
    private String paymentCode;
    private String referenceId;
    private String idempotencyKey;
    private String receiptNumber;
    private Timestamp submittedAt;
    private Integer verifiedBy;
    private Timestamp verifiedAt;
    private String rejectionReason;
    private String studentNote;
    private boolean disputed;
    private Integer refundedBy;
    private Timestamp refundedAt;
    private String refundReason;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Joined presentation metadata
    private String studentName;
    private String registerNumber;
    private String bookTitle;
    private String paymentType = "INDIVIDUAL";
    private int finesSettledCount = 1;
    private java.util.List<Integer> settledFineIds = new java.util.ArrayList<>();

    public Payment() {
        this.method = PaymentMethodType.UPI_MANUAL;
        this.status = PaymentStatus.INITIATED;
        this.disputed = false;
    }

    public Payment(int fineId, int studentId, BigDecimal amount, String paymentCode, String idempotencyKey) {
        this();
        this.fineId = fineId;
        this.studentId = studentId;
        this.amount = amount;
        this.paymentCode = paymentCode;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.INITIATED;
    }

    /**
     * Domain Transition: Student submits UPI transaction reference ID / UTR.
     * Allowed from INITIATED -> PENDING_VERIFICATION.
     */
    public void submitReference(String refId) {
        if (this.status != PaymentStatus.INITIATED) {
            throw new InvalidPaymentException("Cannot submit reference for payment in status: " + this.status);
        }
        if (refId == null || refId.trim().isEmpty()) {
            throw new InvalidPaymentException("Valid UPI transaction reference ID / UTR is required.");
        }
        this.referenceId = refId.trim().toUpperCase();
        this.submittedAt = new Timestamp(System.currentTimeMillis());
        this.status = PaymentStatus.PENDING_VERIFICATION;
    }

    /**
     * Domain Transition: Staff member verifies payment against institutional bank/UPI statement.
     * Allowed from PENDING_VERIFICATION -> SUCCESS.
     */
    public void markVerified(int verifierUserId, String receiptNumber) {
        if (this.status != PaymentStatus.PENDING_VERIFICATION) {
            throw new InvalidPaymentException("Only payments in PENDING_VERIFICATION can be verified. Current: " + this.status);
        }
        if (receiptNumber == null || receiptNumber.trim().isEmpty()) {
            throw new InvalidPaymentException("Receipt number is required for verification.");
        }
        this.verifiedBy = verifierUserId;
        this.verifiedAt = new Timestamp(System.currentTimeMillis());
        this.receiptNumber = receiptNumber.trim();
        this.status = PaymentStatus.SUCCESS;
    }

    /**
     * Domain Transition: Staff member rejects payment with a mandatory reason.
     * Allowed from PENDING_VERIFICATION -> REJECTED.
     */
    public void markRejected(int verifierUserId, String reason) {
        if (this.status != PaymentStatus.PENDING_VERIFICATION) {
            throw new InvalidPaymentException("Only payments in PENDING_VERIFICATION can be rejected. Current: " + this.status);
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidPaymentException("Rejection reason is mandatory.");
        }
        this.verifiedBy = verifierUserId;
        this.verifiedAt = new Timestamp(System.currentTimeMillis());
        this.rejectionReason = reason.trim();
        this.status = PaymentStatus.REJECTED;
    }

    /**
     * Domain Transition: Student cancels own payment attempt while INITIATED.
     */
    public void markCancelled() {
        if (this.status != PaymentStatus.INITIATED) {
            throw new InvalidPaymentException("Only INITIATED payments can be cancelled. Current: " + this.status);
        }
        this.status = PaymentStatus.CANCELLED;
    }

    /**
     * Domain Transition: Automatic settlement upon authentic Razorpay signature verification.
     */
    public void markRazorpaySuccess(String razorpayPaymentId, String receiptNumber) {
        if (this.status != PaymentStatus.INITIATED && this.status != PaymentStatus.PENDING_VERIFICATION) {
            throw new InvalidPaymentException("Payment cannot transition to SUCCESS from status: " + this.status);
        }
        if (razorpayPaymentId == null || razorpayPaymentId.trim().isEmpty()) {
            throw new InvalidPaymentException("Razorpay payment reference ID is required.");
        }
        if (receiptNumber == null || receiptNumber.trim().isEmpty()) {
            throw new InvalidPaymentException("Receipt number is required.");
        }
        this.referenceId = razorpayPaymentId.trim();
        this.receiptNumber = receiptNumber.trim();
        this.submittedAt = new Timestamp(System.currentTimeMillis());
        this.verifiedAt = new Timestamp(System.currentTimeMillis());
        this.status = PaymentStatus.SUCCESS;
    }

    /**
     * Domain Transition: Mark payment attempt as failed (e.g. gateway error, signature mismatch).
     */
    public void markFailed(String reason) {
        if (this.status == PaymentStatus.SUCCESS) {
            throw new InvalidPaymentException("A successful payment cannot be marked failed.");
        }
        this.status = PaymentStatus.FAILED;
        this.rejectionReason = reason;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * Domain Transition: Admin records manual reversal / refund of a verified payment.
     * Allowed from SUCCESS -> REFUNDED.
     */
    public void markRefunded(int adminUserId, String reason) {
        if (this.status != PaymentStatus.SUCCESS) {
            throw new InvalidPaymentException("Only SUCCESS payments can be marked refunded. Current: " + this.status);
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidPaymentException("Refund reason is mandatory.");
        }
        this.refundedBy = adminUserId;
        this.refundedAt = new Timestamp(System.currentTimeMillis());
        this.refundReason = reason.trim();
        this.status = PaymentStatus.REFUNDED;
    }

    /**
     * Student raises dispute on rejected payment.
     */
    public void raiseDispute(String note) {
        if (this.status != PaymentStatus.REJECTED) {
            throw new InvalidPaymentException("Disputes can only be raised on REJECTED payments.");
        }
        if (note == null || note.trim().isEmpty()) {
            throw new InvalidPaymentException("Dispute explanation note is required.");
        }
        this.disputed = true;
        this.studentNote = note.trim();
    }

    public Integer getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Integer paymentId) {
        this.paymentId = paymentId;
    }

    public int getFineId() {
        return fineId;
    }

    public void setFineId(int fineId) {
        this.fineId = fineId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethodType getMethod() {
        return method;
    }

    public void setMethod(PaymentMethodType method) {
        this.method = method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public void setPaymentCode(String paymentCode) {
        this.paymentCode = paymentCode;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public Timestamp getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Timestamp submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Integer getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Integer verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public Timestamp getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Timestamp verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getStudentNote() {
        return studentNote;
    }

    public void setStudentNote(String studentNote) {
        this.studentNote = studentNote;
    }

    public boolean isDisputed() {
        return disputed;
    }

    public void setDisputed(boolean disputed) {
        this.disputed = disputed;
    }

    public Integer getRefundedBy() {
        return refundedBy;
    }

    public void setRefundedBy(Integer refundedBy) {
        this.refundedBy = refundedBy;
    }

    public Timestamp getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(Timestamp refundedAt) {
        this.refundedAt = refundedAt;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
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

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public int getFinesSettledCount() {
        return finesSettledCount;
    }

    public void setFinesSettledCount(int finesSettledCount) {
        this.finesSettledCount = finesSettledCount;
    }

    public java.util.List<Integer> getSettledFineIds() {
        return settledFineIds;
    }

    public void setSettledFineIds(java.util.List<Integer> settledFineIds) {
        this.settledFineIds = settledFineIds != null ? settledFineIds : new java.util.ArrayList<>();
    }
}

