package com.psgitech.library.service;

import com.google.gson.JsonObject;
import com.psgitech.library.config.FineConfig;
import com.psgitech.library.config.RazorpayConfig;
import com.psgitech.library.enums.AuditEventType;
import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.enums.PaymentMethodType;
import com.psgitech.library.enums.PaymentStatus;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.FineRepository;
import com.psgitech.library.repository.PaymentAuditRepository;
import com.psgitech.library.repository.PaymentRepository;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.TransactionRetryHelper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Year;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-level business logic orchestrating Razorpay checkout integration:
 * - Order creation for single or bulk fine dues
 * - Secure server-side outstanding amount validation
 * - Atomic HMAC-SHA256 signature verification and fine settlement
 * - Discrepancy detection, idempotency, and concurrency controls
 */
public class RazorpayPaymentService {
    private static final Logger LOGGER = Logger.getLogger(RazorpayPaymentService.class.getName());

    private final RazorpayService razorpayService;
    private final PaymentRepository paymentRepository;
    private final FineRepository fineRepository;
    private final PaymentAuditRepository auditRepository;
    private final LossDamageService lossDamageService;

    public RazorpayPaymentService() {
        this(new RazorpayService(), new PaymentRepository(), new FineRepository(), new PaymentAuditRepository());
    }

    public RazorpayPaymentService(RazorpayService razorpayService,
                                  PaymentRepository paymentRepository,
                                  FineRepository fineRepository,
                                  PaymentAuditRepository auditRepository) {
        this.razorpayService = razorpayService;
        this.paymentRepository = paymentRepository;
        this.fineRepository = fineRepository;
        this.auditRepository = auditRepository;
        this.lossDamageService = new LossDamageService();
    }

    /**
     * Creates a Razorpay order for an individual fine.
     */
    public Map<String, Object> createIndividualOrder(int studentUserId, int fineId) {
        FineRecord fine = fineRepository.findById(fineId);
        if (fine == null) {
            throw new FineNotFoundException("Fine record #" + fineId + " not found.");
        }

        if (fine.getStudentId() != studentUserId) {
            throw new InvalidPaymentException("Access Denied: Fine #" + fineId + " belongs to another student account.");
        }

        if (fine.getStatus() == FineStatus.PAID || fine.getStatus() == FineStatus.WAIVED ||
                fine.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException("Fine #" + fineId + " is already settled. Outstanding balance is 0.00.");
        }

        // Clean stale initiations if any
        try (Connection conn = DBConnection.getConnection()) {
            paymentRepository.cancelStaleInitiations(conn, fineId, FineConfig.getInitiationTtlMinutes());
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error cleaning stale initiations for fine #" + fineId, e);
        }

        // Check for active in-flight payment
        if (paymentRepository.isFineInActivePayment(fineId)) {
            throw new PaymentInProgressException("An active payment is currently in progress for Fine #" + fineId +
                    ". Please complete the existing checkout or wait for it to expire.");
        }

        BigDecimal amount = fine.getOutstandingAmount();
        String receipt = "rcpt_fine_" + fineId + "_" + System.currentTimeMillis();
        Map<String, String> notes = new HashMap<>();
        notes.put("student_id", String.valueOf(studentUserId));
        notes.put("fine_id", String.valueOf(fineId));
        notes.put("payment_type", "INDIVIDUAL");

        JsonObject orderJson = razorpayService.createOrder(amount, receipt, notes);
        String orderId = orderJson.get("id").getAsString();

        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean origAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                origAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                Payment payment = new Payment(fineId, studentUserId, amount, orderId, "RZP_ORD_" + orderId);
                payment.setMethod(PaymentMethodType.RAZORPAY);
                payment.setStatus(PaymentStatus.INITIATED);
                paymentRepository.createPayment(conn, payment);

                paymentRepository.linkPaymentFines(conn, payment.getPaymentId(), Collections.singletonList(fine));

                PaymentAuditEntry audit = new PaymentAuditEntry(
                        fineId,
                        payment.getPaymentId(),
                        AuditEventType.PAYMENT_INITIATED,
                        null,
                        PaymentStatus.INITIATED.name(),
                        amount,
                        studentUserId,
                        "STUDENT",
                        "Razorpay order created: " + orderId + " for Fine #" + fineId
                );
                auditRepository.recordAuditEvent(conn, audit);

                conn.commit();

                Map<String, Object> result = new HashMap<>();
                result.put("orderId", orderId);
                result.put("paymentId", payment.getPaymentId());
                result.put("amount", amount);
                result.put("amountPaise", amount.multiply(new BigDecimal(100)).longValue());
                result.put("currency", RazorpayConfig.CURRENCY);
                result.put("keyId", RazorpayConfig.KEY_ID);
                result.put("fineId", fineId);
                result.put("bookTitle", fine.getBookTitle());
                result.put("type", "INDIVIDUAL");

                // Deterministic simulation credentials for sandbox/test fallback
                String testPayId = "pay_test_" + System.currentTimeMillis();
                String testSig = razorpayService.generateSignature(orderId, testPayId);
                result.put("testPaymentId", testPayId);
                result.put("testSignature", testSig);
                return result;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(origAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "createIndividualOrder");
    }

    /**
     * Creates a combined Razorpay order for all outstanding fines ("Pay All").
     */
    public Map<String, Object> createPayAllOrder(int studentUserId, List<Integer> fineIds) {
        List<FineRecord> eligibleFines = new ArrayList<>();

        if (fineIds == null || fineIds.isEmpty()) {
            eligibleFines = fineRepository.findOutstandingFinesByStudent(studentUserId);
        } else {
            for (Integer fId : fineIds) {
                FineRecord fine = fineRepository.findById(fId);
                if (fine == null) {
                    throw new FineNotFoundException("Fine record #" + fId + " not found.");
                }
                if (fine.getStudentId() != studentUserId) {
                    throw new InvalidPaymentException("Access Denied: Fine #" + fId + " does not belong to your account.");
                }
                if ((fine.getStatus() == FineStatus.UNPAID || fine.getStatus() == FineStatus.PARTIALLY_PAID)
                        && fine.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0) {
                    eligibleFines.add(fine);
                }
            }
        }

        if (eligibleFines.isEmpty()) {
            throw new InvalidPaymentException("No outstanding fines found to settle.");
        }

        // Clean stale initiations for each fine
        try (Connection conn = DBConnection.getConnection()) {
            for (FineRecord f : eligibleFines) {
                paymentRepository.cancelStaleInitiations(conn, f.getFineId(), FineConfig.getInitiationTtlMinutes());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error cleaning stale initiations for bulk fines", e);
        }

        // Check active in-flight payments
        for (FineRecord f : eligibleFines) {
            if (paymentRepository.isFineInActivePayment(f.getFineId())) {
                throw new PaymentInProgressException("Fine #" + f.getFineId() + " already has an active payment attempt in progress.");
            }
        }

        // Server-side calculated total
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Integer> finalFineIds = new ArrayList<>();
        for (FineRecord f : eligibleFines) {
            totalAmount = totalAmount.add(f.getOutstandingAmount());
            finalFineIds.add(f.getFineId());
        }

        String receipt = "rcpt_bulk_" + studentUserId + "_" + System.currentTimeMillis();
        Map<String, String> notes = new HashMap<>();
        notes.put("student_id", String.valueOf(studentUserId));
        notes.put("fines_count", String.valueOf(eligibleFines.size()));
        notes.put("payment_type", "BULK");

        JsonObject orderJson = razorpayService.createOrder(totalAmount, receipt, notes);
        String orderId = orderJson.get("id").getAsString();

        List<FineRecord> finesToSave = eligibleFines;
        BigDecimal finalTotal = totalAmount;

        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean origAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                origAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // Use the first fine ID as the foreign key anchor in payments table
                int primaryFineId = finesToSave.get(0).getFineId();
                Payment payment = new Payment(primaryFineId, studentUserId, finalTotal, orderId, "RZP_ORD_" + orderId);
                payment.setMethod(PaymentMethodType.RAZORPAY);
                payment.setStatus(PaymentStatus.INITIATED);
                paymentRepository.createPayment(conn, payment);

                // Link all fines in payment_fines table
                paymentRepository.linkPaymentFines(conn, payment.getPaymentId(), finesToSave);

                for (FineRecord f : finesToSave) {
                    PaymentAuditEntry audit = new PaymentAuditEntry(
                            f.getFineId(),
                            payment.getPaymentId(),
                            AuditEventType.PAYMENT_INITIATED,
                            null,
                            PaymentStatus.INITIATED.name(),
                            f.getOutstandingAmount(),
                            studentUserId,
                            "STUDENT",
                            "Razorpay bulk order created: " + orderId + " (Total: " + finalTotal + ")"
                    );
                    auditRepository.recordAuditEvent(conn, audit);
                }

                conn.commit();

                Map<String, Object> result = new HashMap<>();
                result.put("orderId", orderId);
                result.put("paymentId", payment.getPaymentId());
                result.put("amount", finalTotal);
                result.put("amountPaise", finalTotal.multiply(new BigDecimal(100)).longValue());
                result.put("currency", RazorpayConfig.CURRENCY);
                result.put("keyId", RazorpayConfig.KEY_ID);
                result.put("fineIds", finalFineIds);
                result.put("finesCount", finalFineIds.size());
                result.put("type", "BULK");

                // Deterministic simulation credentials for sandbox/test fallback
                String testPayId = "pay_test_bulk_" + System.currentTimeMillis();
                String testSig = razorpayService.generateSignature(orderId, testPayId);
                result.put("testPaymentId", testPayId);
                result.put("testSignature", testSig);
                return result;

            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(origAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "createPayAllOrder");
    }

    /**
     * Verifies cryptographic signature and atomically settles fines.
     */
    public Payment verifyAndCapturePayment(int studentUserId, String orderId, String paymentIdStr, String signature) {
        if (orderId == null || orderId.trim().isEmpty() ||
                paymentIdStr == null || paymentIdStr.trim().isEmpty() ||
                signature == null || signature.trim().isEmpty()) {
            throw new InvalidPaymentException("Missing required payment verification parameters.");
        }

        String cleanOrderId = orderId.trim();
        String cleanPaymentId = paymentIdStr.trim();
        String cleanSig = signature.trim();

        // 1. Verify authentic cryptographic HMAC-SHA256 signature
        boolean validSignature = razorpayService.verifySignature(cleanOrderId, cleanPaymentId, cleanSig);
        if (!validSignature) {
            LOGGER.warning("Razorpay signature verification failed for order " + cleanOrderId + ", payment " + cleanPaymentId);
            recordFailedPayment(studentUserId, cleanOrderId, "Signature verification failed. Potential tampering detected.");
            throw new InvalidPaymentException("Payment verification failed: Invalid cryptographic signature.");
        }

        // 2. Perform atomic capture inside database transaction
        return TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean origAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                origAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // Lock payment record
                Payment payment = paymentRepository.lockPaymentByCode(conn, cleanOrderId);
                if (payment == null) {
                    throw new PaymentNotFoundException("No payment record found for order ID: " + cleanOrderId);
                }

                // Security check
                if (payment.getStudentId() != studentUserId) {
                    throw new InvalidPaymentException("Access Denied: Payment transaction belongs to another student.");
                }

                // Idempotency: If already marked SUCCESS, return existing record
                if (payment.getStatus() == PaymentStatus.SUCCESS) {
                    LOGGER.info("Payment " + cleanOrderId + " already marked SUCCESS (idempotent callback).");
                    conn.commit();
                    return payment;
                }

                if (payment.getStatus() != PaymentStatus.INITIATED && payment.getStatus() != PaymentStatus.PENDING_VERIFICATION) {
                    throw new InvalidPaymentException("Payment cannot be captured in status: " + payment.getStatus());
                }

                // Retrieve all fines associated with this payment
                List<Integer> fineIds = paymentRepository.findFineIdsByPaymentId(conn, payment.getPaymentId());
                if (fineIds.isEmpty() && payment.getFineId() > 0) {
                    fineIds.add(payment.getFineId());
                }

                int currentYear = Year.now().getValue();
                String receiptNumber = String.format("RCPT-%d-%05d", currentYear, payment.getPaymentId());
                String oldStatus = payment.getStatus().name();

                BigDecimal totalSettled = BigDecimal.ZERO;
                List<Integer> successfullySettledFineIds = new ArrayList<>();

                for (int fId : fineIds) {
                    FineRecord fine = fineRepository.lockFineById(conn, fId);
                    if (fine == null) {
                        LOGGER.warning("Fine #" + fId + " not found during capture. Skipping.");
                        continue;
                    }

                    // Discrepancy detection: check if fine was already settled by another process
                    if (fine.getStatus() == FineStatus.PAID || fine.getStatus() == FineStatus.WAIVED) {
                        LOGGER.warning("Fine #" + fId + " was already marked " + fine.getStatus() + ". Discrepancy detected.");
                        continue;
                    }

                    BigDecimal settleAmount = fine.getOutstandingAmount();
                    fine.applyPayment(settleAmount);
                    fineRepository.updateFineAmountsAndStatus(conn, fine.getFineId(),
                            fine.getPaidAmount(), fine.getWaivedAmount(), fine.getOutstandingAmount(), fine.getStatus());

                    if (fine.isCompensation() && fine.getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0) {
                        lossDamageService.handleFineSettled(conn, fine.getFineId(), fine.getPaidAmount(), fine.getWaivedAmount());
                    }

                    totalSettled = totalSettled.add(settleAmount);
                    successfullySettledFineIds.add(fine.getFineId());

                    PaymentAuditEntry audit = new PaymentAuditEntry(
                            fine.getFineId(),
                            payment.getPaymentId(),
                            AuditEventType.PAYMENT_VERIFIED,
                            oldStatus,
                            PaymentStatus.SUCCESS.name(),
                            settleAmount,
                            studentUserId,
                            "STUDENT",
                            "Razorpay payment verified & captured. Payment ID: " + cleanPaymentId + ", Receipt: " + receiptNumber
                    );
                    auditRepository.recordAuditEvent(conn, audit);
                }

                // Update payment record to SUCCESS
                payment.markRazorpaySuccess(cleanPaymentId, receiptNumber);
                paymentRepository.updatePayment(conn, payment);

                // Populate presentation fields
                payment.setSettledFineIds(successfullySettledFineIds);
                payment.setFinesSettledCount(successfullySettledFineIds.size());
                payment.setPaymentType(successfullySettledFineIds.size() > 1 ? "BULK" : "INDIVIDUAL");

                // Record student notification
                try {
                    NotificationDraft draft = NotificationDraft.builder(
                            NotificationType.PAYMENT_VERIFIED, studentUserId, "PAYMENT_VERIFIED:" + payment.getPaymentId())
                            .entity("PAYMENT", payment.getPaymentId())
                            .param("amount", payment.getAmount() != null ? payment.getAmount().toPlainString() : "0.00")
                            .param("receipt", receiptNumber)
                            .build();
                    new NotificationRecorder().record(conn, draft);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to record payment verification notification: " + ex.getMessage());
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
                    try { conn.setAutoCommit(origAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "verifyAndCapturePayment");
    }

    /**
     * Records payment failure or user cancellation, unlocking active fines for future attempts.
     */
    public void recordFailedPayment(int studentUserId, String orderId, String reason) {
        handleTerminalState(studentUserId, orderId, reason, true);
    }

    public void recordCancelledPayment(int studentUserId, String orderId, String reason) {
        handleTerminalState(studentUserId, orderId, reason, false);
    }

    private void handleTerminalState(int studentUserId, String orderId, String reason, boolean isFailed) {
        if (orderId == null || orderId.trim().isEmpty()) return;

        TransactionRetryHelper.executeWithRetry(() -> {
            Connection conn = null;
            boolean origAutoCommit = true;
            try {
                conn = DBConnection.getConnection();
                origAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                Payment payment = paymentRepository.lockPaymentByCode(conn, orderId.trim());
                if (payment != null && payment.getStudentId() == studentUserId) {
                    if (payment.getStatus() == PaymentStatus.INITIATED || payment.getStatus() == PaymentStatus.PENDING_VERIFICATION) {
                        String oldStatus = payment.getStatus().name();
                        if (isFailed) {
                            payment.markFailed(reason);
                        } else {
                            payment.markCancelled();
                        }
                        paymentRepository.updatePayment(conn, payment);

                        PaymentAuditEntry audit = new PaymentAuditEntry(
                                payment.getFineId(),
                                payment.getPaymentId(),
                                isFailed ? AuditEventType.PAYMENT_REJECTED : AuditEventType.PAYMENT_CANCELLED,
                                oldStatus,
                                payment.getStatus().name(),
                                payment.getAmount(),
                                studentUserId,
                                "STUDENT",
                                (isFailed ? "Razorpay payment failed: " : "Razorpay payment cancelled: ") + (reason != null ? reason : "User dismissed checkout")
                        );
                        auditRepository.recordAuditEvent(conn, audit);
                    }
                }
                conn.commit();
                return null;
            } catch (SQLException e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ex) { e.addSuppressed(ex); }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(origAutoCommit); conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, "handleTerminalState");
    }
}
