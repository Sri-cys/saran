package com.psgitech.library.model;

import java.sql.Timestamp;

/**
 * Audit record representing a historical configuration modification to loan_policy_settings.
 * Stored in append-only table loan_policy_changes.
 */
public class LoanPolicyChangeRecord {
    private int changeId;
    private Integer changedBy;
    private String changedByName;
    private Timestamp changedAt;
    private boolean oldEnabled;
    private boolean newEnabled;
    private int oldEnterThreshold;
    private int newEnterThreshold;
    private int oldExitThreshold;
    private int newExitThreshold;
    private int oldHighDemandDays;
    private int newHighDemandDays;
    private int version;

    public LoanPolicyChangeRecord() {
    }

    public LoanPolicyChangeRecord(Integer changedBy, String changedByName,
                                  boolean oldEnabled, boolean newEnabled,
                                  int oldEnterThreshold, int newEnterThreshold,
                                  int oldExitThreshold, int newExitThreshold,
                                  int oldHighDemandDays, int newHighDemandDays,
                                  int version) {
        this.changedBy = changedBy;
        this.changedByName = changedByName;
        this.changedAt = new Timestamp(System.currentTimeMillis());
        this.oldEnabled = oldEnabled;
        this.newEnabled = newEnabled;
        this.oldEnterThreshold = oldEnterThreshold;
        this.newEnterThreshold = newEnterThreshold;
        this.oldExitThreshold = oldExitThreshold;
        this.newExitThreshold = newExitThreshold;
        this.oldHighDemandDays = oldHighDemandDays;
        this.newHighDemandDays = newHighDemandDays;
        this.version = version;
    }

    public int getChangeId() {
        return changeId;
    }

    public void setChangeId(int changeId) {
        this.changeId = changeId;
    }

    public Integer getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Integer changedBy) {
        this.changedBy = changedBy;
    }

    public String getChangedByName() {
        return changedByName;
    }

    public void setChangedByName(String changedByName) {
        this.changedByName = changedByName;
    }

    public Timestamp getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Timestamp changedAt) {
        this.changedAt = changedAt;
    }

    public boolean isOldEnabled() {
        return oldEnabled;
    }

    public void setOldEnabled(boolean oldEnabled) {
        this.oldEnabled = oldEnabled;
    }

    public boolean isNewEnabled() {
        return newEnabled;
    }

    public void setNewEnabled(boolean newEnabled) {
        this.newEnabled = newEnabled;
    }

    public int getOldEnterThreshold() {
        return oldEnterThreshold;
    }

    public void setOldEnterThreshold(int oldEnterThreshold) {
        this.oldEnterThreshold = oldEnterThreshold;
    }

    public int getNewEnterThreshold() {
        return newEnterThreshold;
    }

    public void setNewEnterThreshold(int newEnterThreshold) {
        this.newEnterThreshold = newEnterThreshold;
    }

    public int getOldExitThreshold() {
        return oldExitThreshold;
    }

    public void setOldExitThreshold(int oldExitThreshold) {
        this.oldExitThreshold = oldExitThreshold;
    }

    public int getNewExitThreshold() {
        return newExitThreshold;
    }

    public void setNewExitThreshold(int newExitThreshold) {
        this.newExitThreshold = newExitThreshold;
    }

    public int getOldHighDemandDays() {
        return oldHighDemandDays;
    }

    public void setOldHighDemandDays(int oldHighDemandDays) {
        this.oldHighDemandDays = oldHighDemandDays;
    }

    public int getNewHighDemandDays() {
        return newHighDemandDays;
    }

    public void setNewHighDemandDays(int newHighDemandDays) {
        this.newHighDemandDays = newHighDemandDays;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
