package com.psgitech.library.service;

import com.psgitech.library.config.FineConfig;
import com.psgitech.library.enums.AuditEventType;
import com.psgitech.library.enums.CaseStatus;
import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.enums.PaymentMethodType;
import com.psgitech.library.enums.PaymentStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.interfaces.PaymentMethod;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.FineRepository;
import com.psgitech.library.repository.LossDamageCaseRepository;
import com.psgitech.library.repository.PaymentAuditRepository;
import com.psgitech.library.repository.PaymentRepository;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.TransactionRetryHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating fine payments, manual UPI reference submissions,
 * staff verifications, waivers, dispute resolutions, and receipts.
 */
public class PaymentService {
    private static final Logger LOGGER = Logger.getLogger(PaymentService.class.getName());

    private final PaymentRepository paymentRepository;
    private final FineRepository fineRepository;
    private final PaymentAuditRepository auditRepository;
    private final LossDamageCaseRepository lossDamageCaseRepository;
    private final PaymentMethod manualUpiMethod;
    private final UpiService upiService;

    public PaymentService() {
        this.paymentRepository = new PaymentRepository();
        this.fineRepository = new FineRepository();
        this.auditRepository = new PaymentAuditRepository();
        this.lossDamageCaseRepository = new LossDamageCaseRepository();
        this.upiService = new UpiService();
        this.manualUpiMethod = new ManualUpiPaymentMethod(this.upiService);
    }

    public PaymentService(PaymentRepository paymentRepo, FineRepository fineRepo,
                          PaymentAuditRepository auditRepo, PaymentMethod paymentMethod,
                          UpiService upiService) {
        this.paymentRepository = paymentRepo;
        this.fineRepository = fineRepo;
        this.auditRepository = auditRepo;
        this.manualUpiMethod = paymentMethod;
        this.upiService = upiService;
        this.lossDamageCaseRepository = new LossDamageCaseRepository();
    }

    /**
     * Initiates a new payment attempt for an unpaid fine.
     * Enforces rate limiting, idempotency key replays, 30-minute stale attempt supersession,
     * and generated single-in-flight constraint.
     */
    public Map<String, Object> initiatePayment(int studentId, int fineId, BigDecimal requestedAmount, String idempotencyKey) {
        // 1. Enforce rate limit (D16: max 5 attempts per student per hour)
        int recentAttempts = paymentRepository.countRecentInitiations(studentId, 60);
        if (recentAttempts >= FineConfig.getMaxAttemptsPerHour()) {
            throw new PaymentRateLimitExceededException("Too many payment attempts. You can initiate at most " +
                    FineConfig.getMaxAttemptsPerHour() + " payment attempts per hour. Please try again later.");
        }

        // 2. Handle Idempotency Key Replay (Clarification 2)
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Payment existing = paymentRepository.findByIdempotencyKey(studentId, idempotencyKey.trim());
            if (existing != null) {
                if (existing.getFineId() == fineId) {
                    LOGGER.info("Idempotent replay detected for student #" + studentId + ", fine #" + fineId);
                    FineRecord fine = fineRepository.findById(fineId);
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("payment", existing);
                    resp.put("instructions", manualUpiMethod.preparePaymentInstructions(existing, fine));
                    resp.put("idempotentReplay", true);
                    return resp;
                } else {
                    throw new InvalidPaymentException("Idempotency key has already been used for another payment request.");
                }
            }
        }

        // 3. Execute initiation inside transaction
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // Lock fine row FOR UPDATE
                FineRecord fine = fineRepository.lockFineById(conn, fineId);
                if (fine == null) {
                    throw new FineNotFoundException("Fine record #" + fineId + " not found.");
                }
                if (fine.getStudentId() != studentId) {
                    throw new FineNotFoundException("Fine record #" + fineId + " does not belong to your account.");
                }
                if (fine.getStatus() == FineStatus.PAID || fine.getStatus() == FineStatus.WAIVED ||
                        fine.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new InvalidPaymentException("Fine #" + fineId + " is already fully settled. No payment required.");
                }

                // Supersede stale INITIATED attempts older than configured TTL (D13: 30 mins)
                paymentRepository.cancelStaleInitiations(conn, fineId, FineConfig.getInitiationTtlMinutes());

                // Check active in-flight payment
                Payment activePayment = paymentRepository.findActiveByFineId(conn, fineId);
                if (activePayment != null) {
                    throw new PaymentInProgressException("An active payment attempt is already in progress for this fine (Code: " +
                            activePayment.getPaymentCode() + ", Status: " + activePayment.getStatus() +
                            "). Submit your UPI reference or cancel it before starting a new payment.");
                }

                // Determine and validate accepted amount
                BigDecimal acceptedAmount;
                if (requestedAmount == null) {
                    acceptedAmount = fine.getOutstandingAmount();
                } else {
                    if (!FineConfig.isAllowPartialPayments()) {
                        acceptedAmount = fine.getOutstandingAmount();
                    } else {
                        if (requestedAmount.compareTo(FineConfig.getMinPartialPayment()) < 0) {
                            throw new InvalidPaymentException("Minimum payment amount is INR " +
                                    FineConfig.getMinPartialPayment().toPlainString() + ".");
                        }
                        if (requestedAmount.compareTo(fine.getOutstandingAmount()) > 0) {
                            throw new InvalidPaymentException("Payment amount (INR " + requestedAmount +
                                    ") exceeds current outstanding balance (INR " + fine.getOutstandingAmount() + ").");
                        }
                        acceptedAmount = requestedAmount.setScale(2, RoundingMode.HALF_UP);
                    }
                }

                // Generate payment code
                long timestamp = System.currentTimeMillis() % 1000000;
                String paymentCode = "FINE-" + fineId + "-" + timestamp;

                Payment payment = new Payment(fineId, studentId, acceptedAmount, paymentCode, idempotencyKey);
                Payment saved = paymentRepository.createPayment(conn, payment);

                // Record audit
                PaymentAuditEntry audit = new PaymentAuditEntry(
                        fineId,
                        saved.getPaymentId(),
                        AuditEventType.PAYMENT_INITIATED,
                        null,
                        PaymentStatus.INITIATED.name(),
                        acceptedAmount,
                        studentId,
                        "STUDENT",
                        "Payment initiated for INR " + acceptedAmount.toPlainString() + " (Code: " + paymentCode + ")"
                );
                auditRepository.recordAuditEvent(conn, audit);

                conn.commit();

                Map<String, Object> resp = new HashMap<>();
                resp.put("payment", saved);
                resp.put("instructions", manualUpiMethod.preparePaymentInstructions(saved, fine));
                resp.put("idempotentReplay", false);
                return resp;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                // Handle concurrent idempotency race condition cleanly
                if (e.getErrorCode() == 1062 && idempotencyKey != null) {
                    Payment existing = paymentRepository.findByIdempotencyKey(studentId, idempotencyKey.trim());
                    if (existing != null && existing.getFineId() == fineId) {
                        FineRecord fine = fineRepository.findById(fineId);
                        Map<String, Object> resp = new HashMap<>();
                        resp.put("payment", existing);
                        resp.put("instructions", manualUpiMethod.preparePaymentInstructions(existing, fine));
                        resp.put("idempotentReplay", true);
                        return resp;
                    }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "initiatePayment");
    }

    /**
     * Cancels an INITIATED payment attempt (STUDENT only).
     */
    public Payment cancelPayment(int paymentId, int studentId) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                Payment payment = paymentRepository.lockPaymentById(conn, paymentId);
                if (payment == null) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
                }
                if (payment.getStudentId() != studentId) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " does not belong to your account.");
                }
                if (payment.getStatus() != PaymentStatus.INITIATED) {
                    throw new InvalidPaymentException("Only payments in INITIATED status can be cancelled. Current status: " + payment.getStatus());
                }

                String oldStatus = payment.getStatus().name();
                payment.markCancelled();
                paymentRepository.updatePayment(conn, payment);

                PaymentAuditEntry audit = new PaymentAuditEntry(
                        payment.getFineId(),
                        paymentId,
                        AuditEventType.PAYMENT_CANCELLED,
                        oldStatus,
                        PaymentStatus.CANCELLED.name(),
                        payment.getAmount(),
                        studentId,
                        "STUDENT",
                        "Payment cancelled by student."
                );
                auditRepository.recordAuditEvent(conn, audit);

                conn.commit();
                return payment;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "cancelPayment");
    }

    /**
     * Submits student-provided UPI transaction reference ID / UTR.
     * Transitions payment: INITIATED -> PENDING_VERIFICATION.
     */
    public Payment submitReference(int paymentId, int studentId, String rawReference) {
        String cleanRef = upiService.validateAndNormalizeReference(rawReference);

        // Pre-check for duplicate reference across other payments
        Payment duplicate = paymentRepository.findByReference("UPI_MANUAL", cleanRef);
        if (duplicate != null && !duplicate.getPaymentId().equals(paymentId)) {
            throw new DuplicatePaymentReferenceException("UPI transaction reference ID '" + cleanRef +
                    "' has already been recorded on another payment. Each UPI reference can only be submitted once.");
        }

        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                Payment payment = paymentRepository.lockPaymentById(conn, paymentId);
                if (payment == null) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
                }
                if (payment.getStudentId() != studentId) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " does not belong to your account.");
                }
                if (payment.getStatus() != PaymentStatus.INITIATED) {
                    throw new InvalidPaymentException("Payment #" + paymentId + " is not in INITIATED status (" + payment.getStatus() + ").");
                }

                String oldStatus = payment.getStatus().name();
                payment.submitReference(cleanRef);

                int updated = paymentRepository.updatePayment(conn, payment);
                if (updated == 0) {
                    throw new InvalidPaymentException("Concurrent modification detected while submitting payment reference.");
                }

                PaymentAuditEntry audit = new PaymentAuditEntry(
                        payment.getFineId(),
                        paymentId,
                        AuditEventType.REFERENCE_SUBMITTED,
                        oldStatus,
                        PaymentStatus.PENDING_VERIFICATION.name(),
                        payment.getAmount(),
                        studentId,
                        "STUDENT",
                        "UPI reference submitted: " + cleanRef + ". Awaiting staff verification."
                );
                auditRepository.recordAuditEvent(conn, audit);

                // Record Feature 11: PAYMENT_SUBMITTED and Staff PAYMENT_PENDING_VERIFICATION
                try {
                    com.psgitech.library.service.NotificationRecorder recorder = new com.psgitech.library.service.NotificationRecorder();
                    com.psgitech.library.model.NotificationDraft studentDraft = com.psgitech.library.model.NotificationDraft
                            .builder(com.psgitech.library.enums.NotificationType.PAYMENT_SUBMITTED, studentId, "PAYMENT_SUBMITTED:" + paymentId)
                            .entity("PAYMENT", paymentId)
                            .param("amount", payment.getAmount() != null ? payment.getAmount().toPlainString() : "0.00")
                            .param("utr", cleanRef)
                            .build();
                    recorder.record(conn, studentDraft);

                    recorder.recordForStaff(conn, com.psgitech.library.enums.NotificationType.PAYMENT_PENDING_VERIFICATION,
                            "Payment Verification Pending",
                            "Student submitted payment of Rs. " + (payment.getAmount() != null ? payment.getAmount().toPlainString() : "0.00") +
                                    " (ref ending in " + com.psgitech.library.service.NotificationTemplateService.maskUtr(cleanRef) + ")",
                            "borrowing-management.html",
                            "STAFF_PAYMENT_PENDING:" + paymentId);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record payment submission notifications", e);
                }

                conn.commit();
                return payment;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                if (e.getErrorCode() == 1062) {
                    throw new DuplicatePaymentReferenceException("UPI reference ID '" + cleanRef + "' is already recorded in the system.");
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "submitReference");
    }

    /**
     * Verifies payment against bank statement. (LIBRARIAN / ADMIN only per D12).
     * Follows canonical lock sequence: lock fines FOR UPDATE, then lock payments FOR UPDATE.
     */
    public Payment verifyPayment(int paymentId, int staffUserId, Role staffRole) {
        if (staffRole != Role.LIBRARIAN && staffRole != Role.ADMIN) {
            throw new InvalidPaymentException("Access Denied: Only Librarians and Administrators can verify payments.");
        }

        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // 1. Plain-read payment to get fine_id
                Payment initial = paymentRepository.findById(conn, paymentId);
                if (initial == null) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
                }

                // 2. Lock fines row FIRST
                FineRecord fine = fineRepository.lockFineById(conn, initial.getFineId());
                if (fine == null) {
                    throw new FineNotFoundException("Associated fine #" + initial.getFineId() + " not found.");
                }

                // 3. Lock payments row SECOND
                Payment payment = paymentRepository.lockPaymentById(conn, paymentId);
                if (payment == null) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
                }

                if (payment.getStatus() != PaymentStatus.PENDING_VERIFICATION) {
                    throw new InvalidPaymentException("Payment #" + paymentId + " is not in PENDING_VERIFICATION status (" + payment.getStatus() + ").");
                }

                // Check for overpayment against current outstanding balance
                if (payment.getAmount().compareTo(fine.getOutstandingAmount()) > 0) {
                    throw new InvalidPaymentException("Payment amount (INR " + payment.getAmount() +
                            ") exceeds current outstanding balance (INR " + fine.getOutstandingAmount() +
                            "). A waiver or adjustment may have reduced the balance. Staff review required.");
                }

                // Generate unique receipt number: RCPT-YYYY-XXXX
                int currentYear = Year.now().getValue();
                String receiptNumber = String.format("RCPT-%d-%05d", currentYear, paymentId);

                String oldPaymentStatus = payment.getStatus().name();
                payment.markVerified(staffUserId, receiptNumber);
                fine.applyPayment(payment.getAmount());

                // Update payment and fine records
                paymentRepository.updatePayment(conn, payment);
                fineRepository.updateFineAmountsAndStatus(conn, fine.getFineId(),
                        fine.getPaidAmount(), fine.getWaivedAmount(), fine.getOutstandingAmount(), fine.getStatus());

                if (fine.isCompensation() && fine.getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0) {
                    new LossDamageService().handleFineSettled(conn, fine.getFineId(), fine.getPaidAmount(), fine.getWaivedAmount());
                }

                // Record audit log
                PaymentAuditEntry audit = new PaymentAuditEntry(
                        fine.getFineId(),
                        paymentId,
                        AuditEventType.PAYMENT_VERIFIED,
                        oldPaymentStatus,
                        PaymentStatus.SUCCESS.name(),
                        payment.getAmount(),
                        staffUserId,
                        staffRole.name(),
                        "Payment verified by " + staffRole.name() + " #" + staffUserId + ". Receipt: " + receiptNumber
                );
                auditRepository.recordAuditEvent(conn, audit);

                // Record Feature 11: PAYMENT_VERIFIED
                try {
                    com.psgitech.library.model.NotificationDraft notifDraft = com.psgitech.library.model.NotificationDraft
                            .builder(com.psgitech.library.enums.NotificationType.PAYMENT_VERIFIED, payment.getStudentId(), "PAYMENT_VERIFIED:" + paymentId)
                            .entity("PAYMENT", paymentId)
                            .param("amount", payment.getAmount() != null ? payment.getAmount().toPlainString() : "0.00")
                            .build();
                    new com.psgitech.library.service.NotificationRecorder().record(conn, notifDraft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record payment verification notification", e);
                }

                conn.commit();
                return payment;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "verifyPayment");
    }

    /**
     * Rejects payment with a mandatory reason. (LIBRARIAN / ADMIN only per D12).
     */
    public Payment rejectPayment(int paymentId, int staffUserId, Role staffRole, String reason) {
        if (staffRole != Role.LIBRARIAN && staffRole != Role.ADMIN) {
            throw new InvalidPaymentException("Access Denied: Only Librarians and Administrators can reject payments.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidPaymentException("Rejection reason is mandatory.");
        }

        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                Payment payment = paymentRepository.lockPaymentById(conn, paymentId);
                if (payment == null) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
                }
                if (payment.getStatus() != PaymentStatus.PENDING_VERIFICATION) {
                    throw new InvalidPaymentException("Only payments in PENDING_VERIFICATION can be rejected. Current status: " + payment.getStatus());
                }

                String oldStatus = payment.getStatus().name();
                payment.markRejected(staffUserId, reason.trim());
                paymentRepository.updatePayment(conn, payment);

                PaymentAuditEntry audit = new PaymentAuditEntry(
                        payment.getFineId(),
                        paymentId,
                        AuditEventType.PAYMENT_REJECTED,
                        oldStatus,
                        PaymentStatus.REJECTED.name(),
                        payment.getAmount(),
                        staffUserId,
                        staffRole.name(),
                        "Payment rejected by " + staffRole.name() + " #" + staffUserId + ". Reason: " + reason.trim()
                );
                auditRepository.recordAuditEvent(conn, audit);

                // Record Feature 11: PAYMENT_REJECTED
                try {
                    com.psgitech.library.model.NotificationDraft notifDraft = com.psgitech.library.model.NotificationDraft
                            .builder(com.psgitech.library.enums.NotificationType.PAYMENT_REJECTED, payment.getStudentId(), "PAYMENT_REJECTED:" + paymentId)
                            .entity("PAYMENT", paymentId)
                            .param("amount", payment.getAmount() != null ? payment.getAmount().toPlainString() : "0.00")
                            .param("reason", reason.trim())
                            .build();
                    new com.psgitech.library.service.NotificationRecorder().record(conn, notifDraft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record payment rejection notification", e);
                }

                conn.commit();
                return payment;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "rejectPayment");
    }

    /**
     * Raises a dispute on a REJECTED payment (STUDENT only).
     */
    public Payment raiseDispute(int paymentId, int studentId, String note) {
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                Payment payment = paymentRepository.lockPaymentById(conn, paymentId);
                if (payment == null) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
                }
                if (payment.getStudentId() != studentId) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " does not belong to your account.");
                }

                payment.raiseDispute(note);
                paymentRepository.updatePayment(conn, payment);

                PaymentAuditEntry audit = new PaymentAuditEntry(
                        payment.getFineId(),
                        paymentId,
                        AuditEventType.DISPUTE_RAISED,
                        PaymentStatus.REJECTED.name(),
                        PaymentStatus.REJECTED.name(),
                        payment.getAmount(),
                        studentId,
                        "STUDENT",
                        "Dispute raised by student. Note: " + note.trim()
                );
                auditRepository.recordAuditEvent(conn, audit);

                conn.commit();
                return payment;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "raiseDispute");
    }

    /**
     * Resolves a payment dispute. (ADMIN ONLY per D12).
     */
    public Payment resolveDispute(int paymentId, int adminUserId, Role adminRole, boolean reopen, String adminNotes) {
        if (adminRole != Role.ADMIN) {
            throw new InvalidPaymentException("Access Denied: Only Administrators can resolve payment disputes.");
        }

        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                Payment payment = paymentRepository.lockPaymentById(conn, paymentId);
                if (payment == null) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
                }
                if (!payment.isDisputed()) {
                    throw new InvalidPaymentException("Payment #" + paymentId + " is not flagged as disputed.");
                }

                String oldStatus = payment.getStatus().name();
                payment.setDisputed(false);

                if (reopen) {
                    // Admin accepts dispute and re-opens for verification
                    payment.setStatus(PaymentStatus.PENDING_VERIFICATION);
                }

                paymentRepository.updatePayment(conn, payment);

                PaymentAuditEntry audit = new PaymentAuditEntry(
                        payment.getFineId(),
                        paymentId,
                        AuditEventType.DISPUTE_RESOLVED,
                        oldStatus,
                        payment.getStatus().name(),
                        payment.getAmount(),
                        adminUserId,
                        adminRole.name(),
                        "Dispute resolved by Admin. Reopened: " + reopen + ". Notes: " + (adminNotes != null ? adminNotes.trim() : "")
                );
                auditRepository.recordAuditEvent(conn, audit);

                conn.commit();
                return payment;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "resolveDispute");
    }

    /**
     * Records manual external bank refund/reversal. (ADMIN ONLY per D8).
     * Follows canonical lock sequence: lock fines FOR UPDATE, then lock payments FOR UPDATE.
     */
    public Payment recordRefund(int paymentId, int adminUserId, Role adminRole, String reason) {
        if (adminRole != Role.ADMIN) {
            throw new InvalidPaymentException("Access Denied: Only Administrators can record payment refunds/reversals.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidPaymentException("Refund reason is mandatory.");
        }

        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean originalAutoCommit = true;

            try {
                conn = DBConnection.getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                Payment initial = paymentRepository.findById(conn, paymentId);
                if (initial == null) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
                }

                // Lock fines row FIRST
                FineRecord fine = fineRepository.lockFineById(conn, initial.getFineId());
                if (fine == null) {
                    throw new FineNotFoundException("Associated fine #" + initial.getFineId() + " not found.");
                }

                // Lock payments row SECOND
                Payment payment = paymentRepository.lockPaymentById(conn, paymentId);
                if (payment == null) {
                    throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
                }

                if (payment.getStatus() != PaymentStatus.SUCCESS) {
                    throw new InvalidPaymentException("Only SUCCESS payments can be marked as refunded. Current status: " + payment.getStatus());
                }

                if (fine.isCompensation()) {
                    LossDamageCase ldCase = lossDamageCaseRepository.findCaseByFineId(conn, fine.getFineId());
                    if (ldCase != null && (ldCase.getStatus() == CaseStatus.RESOLVED_PAID || ldCase.getStatus() == CaseStatus.WAIVED)) {
                        throw new InvalidPaymentException("Compensation payment cannot be refunded once the loss/damage case is settled.");
                    }
                }

                String oldStatus = payment.getStatus().name();
                payment.markRefunded(adminUserId, reason.trim());
                fine.reversePayment(payment.getAmount());

                paymentRepository.updatePayment(conn, payment);
                fineRepository.updateFineAmountsAndStatus(conn, fine.getFineId(),
                        fine.getPaidAmount(), fine.getWaivedAmount(), fine.getOutstandingAmount(), fine.getStatus());

                PaymentAuditEntry audit = new PaymentAuditEntry(
                        fine.getFineId(),
                        paymentId,
                        AuditEventType.REFUND_RECORDED,
                        oldStatus,
                        PaymentStatus.REFUNDED.name(),
                        payment.getAmount(),
                        adminUserId,
                        adminRole.name(),
                        "Refund of INR " + payment.getAmount() + " recorded by Admin #" + adminUserId + ". Reason: " + reason.trim()
                );
                auditRepository.recordAuditEvent(conn, audit);

                // Record Feature 11: REFUND_RECORDED
                try {
                    com.psgitech.library.model.NotificationDraft notifDraft = com.psgitech.library.model.NotificationDraft
                            .builder(com.psgitech.library.enums.NotificationType.REFUND_RECORDED, payment.getStudentId(), "REFUND_RECORDED:" + paymentId)
                            .entity("PAYMENT", paymentId)
                            .param("amount", payment.getAmount() != null ? payment.getAmount().toPlainString() : "0.00")
                            .build();
                    new com.psgitech.library.service.NotificationRecorder().record(conn, notifDraft);
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                    LOGGER.log(Level.WARNING, "Failed to record refund notification", e);
                }

                conn.commit();
                return payment;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(originalAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "recordRefund");
    }

    /**
     * Retrieves receipt data for printable receipt. (D14).
     */
    public Map<String, Object> getReceiptData(int paymentId, int requestingUserId, Role requestingRole) {
        Payment payment = paymentRepository.findById(paymentId);
        if (payment == null) {
            throw new PaymentNotFoundException("Payment #" + paymentId + " not found.");
        }
        if (requestingRole == Role.STUDENT && payment.getStudentId() != requestingUserId) {
            throw new PaymentNotFoundException("Receipt #" + paymentId + " does not belong to your account.");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new InvalidPaymentException("Receipt is only generated for verified successful payments. Current status: " + payment.getStatus());
        }

        FineRecord fine = fineRepository.findById(payment.getFineId());
        Map<String, Object> data = new HashMap<>();
        data.put("fineType", fine.getFineType());
        data.put("receiptNumber", payment.getReceiptNumber());
        data.put("paymentId", payment.getPaymentId());
        data.put("paymentCode", payment.getPaymentCode());
        data.put("fineId", fine.getFineId());
        data.put("borrowTransactionId", fine.getBorrowTransactionId());
        data.put("studentName", payment.getStudentName());
        data.put("registerNumber", payment.getRegisterNumber());
        data.put("bookTitle", payment.getBookTitle());
        data.put("originalFineAmount", fine.getOriginalAmount().setScale(2).toPlainString());
        data.put("amountPaidThisPayment", payment.getAmount().setScale(2).toPlainString());
        data.put("totalPaidSoFar", fine.getPaidAmount().setScale(2).toPlainString());
        data.put("totalWaived", fine.getWaivedAmount().setScale(2).toPlainString());
        data.put("remainingOutstanding", fine.getOutstandingAmount().setScale(2).toPlainString());
        data.put("paymentMethod", payment.getMethod().name());
        data.put("referenceId", payment.getReferenceId());
        data.put("paymentDate", payment.getVerifiedAt() != null ? payment.getVerifiedAt().toString() : payment.getCreatedAt().toString());
        data.put("status", payment.getStatus().name());
        data.put("isRefunded", payment.getStatus() == PaymentStatus.REFUNDED);
        data.put("refundReason", payment.getRefundReason());
        return data;
    }

    public List<Payment> getPaymentsByStudent(int studentId) {
        return paymentRepository.findPaymentsByStudent(studentId);
    }

    public List<Payment> getPendingVerifications() {
        return paymentRepository.findPendingVerifications();
    }

    public List<Payment> getAllPaymentsPaginated(String regNoFilter, PaymentStatus statusFilter, int page, int pageSize) {
        return paymentRepository.findAllPaymentsPaginated(regNoFilter, statusFilter, page, pageSize);
    }

    public int countPayments(String regNoFilter, PaymentStatus statusFilter) {
        return paymentRepository.countPayments(regNoFilter, statusFilter);
    }
}

