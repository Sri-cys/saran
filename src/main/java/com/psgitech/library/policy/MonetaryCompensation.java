package com.psgitech.library.policy;

import com.psgitech.library.enums.CaseStatus;
import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.exception.InvalidCaseStateException;
import com.psgitech.library.model.CaseEvent;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.model.LossDamageCase;
import com.psgitech.library.repository.FineRepository;
import com.psgitech.library.repository.LossDamageCaseRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Concrete PenaltyResolution strategy resolving through monetary compensation.
 * Creates an outstanding fine row (fine_type COMPENSATION) and initiates payment pending state.
 */
public class MonetaryCompensation extends PenaltyResolution {
    private final LossDamageCaseRepository caseRepository;
    private final FineRepository fineRepository;
    private int generatedFineId = 0;

    public MonetaryCompensation(LossDamageCaseRepository caseRepository, FineRepository fineRepository) {
        this.caseRepository = caseRepository;
        this.fineRepository = fineRepository;
    }

    @Override
    protected void validate(Connection conn, LossDamageCase caseRecord, ResolutionContext context) {
        CaseStatus current = caseRecord.getStatus();
        if (current != CaseStatus.ASSESSED && current != CaseStatus.REPLACEMENT_PENDING) {
            throw new InvalidCaseStateException("Cannot resolve via payment: case #" + caseRecord.getCaseId() +
                    " is in status " + current + " (expected ASSESSED or REPLACEMENT_PENDING).");
        }
        if (caseRecord.getFinalAmount() == null || caseRecord.getFinalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Final compensation amount is not established for case #" + caseRecord.getCaseId());
        }
    }

    @Override
    protected void computeOrConfirmAmount(Connection conn, LossDamageCase caseRecord, ResolutionContext context) {
        // Confirmation step: finalAmount is validated
    }

    @Override
    protected void applyResolutionStep(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException {
        // Idempotent creation: check if compensation fine already exists for this transaction
        String findFineSql = "SELECT fine_id FROM fines WHERE borrow_transaction_id = ? AND fine_type = 'COMPENSATION'";
        try (PreparedStatement ps = conn.prepareStatement(findFineSql)) {
            ps.setInt(1, caseRecord.getBorrowTransactionId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    generatedFineId = rs.getInt("fine_id");
                    return;
                }
            }
        }

        FineRecord fine = new FineRecord();
        fine.setBorrowTransactionId(caseRecord.getBorrowTransactionId());
        fine.setStudentId(caseRecord.getStudentId());
        fine.setBookId(caseRecord.getBookId());
        fine.setCopyId(caseRecord.getCopyId());
        fine.setOverdueDays(0);
        fine.setChargeableDays(0);
        fine.setFinePerDay(BigDecimal.ZERO);
        fine.setGracePeriodDays(0);
        fine.setMaxFineApplied(null);
        fine.setOriginalAmount(caseRecord.getFinalAmount());
        fine.setOutstandingAmount(caseRecord.getFinalAmount());
        fine.setPaidAmount(BigDecimal.ZERO);
        fine.setWaivedAmount(BigDecimal.ZERO);
        fine.setStatus(FineStatus.UNPAID);
        fine.setFineType("COMPENSATION");

        FineRecord saved = fineRepository.createFine(conn, fine);
        generatedFineId = saved.getFineId();
    }

    @Override
    protected void updateRecords(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException {
        CaseStatus oldStatus = caseRecord.getStatus();
        caseRecord.choosePayment(generatedFineId);
        caseRepository.updateCase(conn, caseRecord, oldStatus);
    }

    @Override
    protected void auditAndNotify(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException {
        CaseEvent event = new CaseEvent(
                caseRecord.getCaseId(),
                "CHOSE_PAYMENT",
                CaseStatus.ASSESSED.name(),
                CaseStatus.PAYMENT_PENDING.name(),
                context.getActorUserId(),
                context.getActorRole(),
                "Student selected monetary compensation payment. Fine #" + generatedFineId + " created."
        );
        caseRepository.recordEvent(conn, event);
    }

    public int getGeneratedFineId() {
        return generatedFineId;
    }
}
