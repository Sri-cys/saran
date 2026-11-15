package com.psgitech.library.model;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Model representing an exam-season date window during which a lower enter threshold applies (Stretch D12).
 */
public class LoanPolicyWindow {
    private int windowId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private int enterThresholdOverride;
    private boolean active;
    private Integer createdBy;
    private Timestamp createdAt;

    public LoanPolicyWindow() {
        this.active = true;
        this.enterThresholdOverride = 2;
    }

    public LoanPolicyWindow(int windowId, String name, LocalDate startDate, LocalDate endDate,
                            int enterThresholdOverride, boolean active, Integer createdBy, Timestamp createdAt) {
        this.windowId = windowId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.enterThresholdOverride = enterThresholdOverride;
        this.active = active;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public boolean isCurrentlyActive(LocalDate date) {
        if (!active || startDate == null || endDate == null) return false;
        LocalDate d = (date != null) ? date : LocalDate.now();
        return !d.isBefore(startDate) && !d.isAfter(endDate);
    }

    public int getWindowId() {
        return windowId;
    }

    public void setWindowId(int windowId) {
        this.windowId = windowId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getEnterThresholdOverride() {
        return enterThresholdOverride;
    }

    public void setEnterThresholdOverride(int enterThresholdOverride) {
        this.enterThresholdOverride = enterThresholdOverride;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
