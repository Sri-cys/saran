package com.psgitech.library.model;

import com.psgitech.library.enums.AuditEventType;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Domain model representing an immutable entry in the financial payment audit log.
 */
public class PaymentAuditEntry {
    private Integer auditId;
    private int fineId;
    private Integer paymentId;
    private AuditEventType eventType;
    private String oldStatus;
    private String newStatus;
    private BigDecimal amount;
    private Integer actorUserId;
    private String actorRole;
    private String details;
    private Timestamp createdAt;

    public PaymentAuditEntry() {}

    public PaymentAuditEntry(int fineId, Integer paymentId, AuditEventType eventType,
                             String oldStatus, String newStatus, BigDecimal amount,
                             Integer actorUserId, String actorRole, String details) {
        this.fineId = fineId;
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.amount = amount;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.details = details;
    }

    public Integer getAuditId() {
        return auditId;
    }

    public void setAuditId(Integer auditId) {
        this.auditId = auditId;
    }

    public int getFineId() {
        return fineId;
    }

    public void setFineId(int fineId) {
        this.fineId = fineId;
    }

    public Integer getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Integer paymentId) {
        this.paymentId = paymentId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(AuditEventType eventType) {
        this.eventType = eventType;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(String oldStatus) {
        this.oldStatus = oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Integer actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}

