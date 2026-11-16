package com.psgitech.library.policy;

import com.psgitech.library.model.LossDamageCase;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstract Template Method defining the fixed invariant sequence for resolving
 * a Lost or Damaged book penalty case:
 * 1. validate()
 * 2. computeOrConfirmAmount()
 * 3. applyResolutionStep()
 * 4. updateRecords()
 * 5. auditAndNotify()
 *
 * Implemented by MonetaryCompensation and PhysicalReplacement (Runtime Polymorphism).
 */
public abstract class PenaltyResolution {

    /**
     * Context parameter object encapsulating actor and input arguments for resolution.
     */
    public static class ResolutionContext {
        private final int actorUserId;
        private final String actorRole;
        private String scannedIsbn;
        private boolean titleConfirmed;
        private boolean conditionOk;
        private String rejectionReason;

        public ResolutionContext(int actorUserId, String actorRole) {
            this.actorUserId = actorUserId;
            this.actorRole = actorRole;
        }

        public int getActorUserId() { return actorUserId; }
        public String getActorRole() { return actorRole; }
        public String getScannedIsbn() { return scannedIsbn; }
        public void setScannedIsbn(String scannedIsbn) { this.scannedIsbn = scannedIsbn; }
        public boolean isTitleConfirmed() { return titleConfirmed; }
        public void setTitleConfirmed(boolean titleConfirmed) { this.titleConfirmed = titleConfirmed; }
        public boolean isConditionOk() { return conditionOk; }
        public void setConditionOk(boolean conditionOk) { this.conditionOk = conditionOk; }
        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    }

    /**
     * Template Method orchestrating penalty resolution.
     */
    public final void resolve(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException {
        validate(conn, caseRecord, context);
        computeOrConfirmAmount(conn, caseRecord, context);
        applyResolutionStep(conn, caseRecord, context);
        updateRecords(conn, caseRecord, context);
        auditAndNotify(conn, caseRecord, context);
    }

    protected abstract void validate(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException;
    protected abstract void computeOrConfirmAmount(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException;
    protected abstract void applyResolutionStep(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException;
    protected abstract void updateRecords(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException;
    protected abstract void auditAndNotify(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException;
}
