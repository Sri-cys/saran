package com.psgitech.library.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Domain Model for an administrative fine adjustment (e.g. waiver approved by an Admin).
 */
public class FineAdjustment {
    private Integer adjustmentId;
    private int fineId;
    private String type; // "WAIVER"
    private BigDecimal amount;
    private String reason;
    private int approvedBy;
    private Timestamp createdAt;

    // Joined presentation field
    private String approvedByName;

    public FineAdjustment() {
        this.type = "WAIVER";
    }

    public FineAdjustment(int fineId, BigDecimal amount, String reason, int approvedBy) {
        this();
        this.fineId = fineId;
        this.amount = amount;
        this.reason = reason;
        this.approvedBy = approvedBy;
    }

    public Integer getAdjustmentId() {
        return adjustmentId;
    }

    public void setAdjustmentId(Integer adjustmentId) {
        this.adjustmentId = adjustmentId;
    }

    public int getFineId() {
        return fineId;
    }

    public void setFineId(int fineId) {
        this.fineId = fineId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(int approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getApprovedByName() {
        return approvedByName;
    }

    public void setApprovedByName(String approvedByName) {
        this.approvedByName = approvedByName;
    }
}

