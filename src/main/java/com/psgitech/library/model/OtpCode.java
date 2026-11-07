package com.psgitech.library.model;

import java.sql.Timestamp;

public class OtpCode {
    private int otpId;
    private OtpPurpose purpose;
    private int subjectId;
    private String destinationEmail;
    private String codeHash;
    private OtpStatus status;
    private int attempts;
    private int maxAttempts;
    private Timestamp createdAt;
    private Timestamp expiresAt;
    private Timestamp consumedAt;

    public OtpCode() {}

    public OtpCode(OtpPurpose purpose, int subjectId, String destinationEmail, String codeHash,
                   Timestamp expiresAt, int maxAttempts) {
        this.purpose = purpose;
        this.subjectId = subjectId;
        this.destinationEmail = destinationEmail;
        this.codeHash = codeHash;
        this.status = OtpStatus.ACTIVE;
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        this.expiresAt = expiresAt;
    }

    public int getOtpId() {
        return otpId;
    }

    public void setOtpId(int otpId) {
        this.otpId = otpId;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public String getDestinationEmail() {
        return destinationEmail;
    }

    public void setDestinationEmail(String destinationEmail) {
        this.destinationEmail = destinationEmail;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public OtpStatus getStatus() {
        return status;
    }

    public void setStatus(OtpStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Timestamp getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Timestamp consumedAt) {
        this.consumedAt = consumedAt;
    }
}

