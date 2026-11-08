package com.psgitech.library.model;

import com.psgitech.library.enums.NotificationCategory;

import java.sql.Timestamp;

/**
 * Domain model representing a user's notification preference for a functional category.
 */
public class NotificationPreference {
    private Integer preferenceId;
    private int userId;
    private NotificationCategory category;
    private boolean inAppEnabled;
    private boolean emailEnabled;
    private boolean locked;
    private String lockReason;
    private Timestamp updatedAt;

    public NotificationPreference() {}

    public NotificationPreference(int userId, NotificationCategory category, boolean inAppEnabled, boolean emailEnabled) {
        this.userId = userId;
        this.category = category;
        this.inAppEnabled = inAppEnabled;
        this.emailEnabled = emailEnabled;
    }

    public Integer getPreferenceId() {
        return preferenceId;
    }

    public void setPreferenceId(Integer preferenceId) {
        this.preferenceId = preferenceId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public void setCategory(NotificationCategory category) {
        this.category = category;
    }

    public boolean isInAppEnabled() {
        return inAppEnabled;
    }

    public void setInAppEnabled(boolean inAppEnabled) {
        this.inAppEnabled = inAppEnabled;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getLockReason() {
        return lockReason;
    }

    public void setLockReason(String lockReason) {
        this.lockReason = lockReason;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
