package com.psgitech.library.model;

import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.enums.NotificationType;

import java.sql.Timestamp;

/**
 * Domain model representing an in-app notification ledger entry.
 */
public class Notification {
    private Long notificationId;
    private int recipientUserId;
    private NotificationType notificationType;
    private NotificationCategory category;
    private String title;
    private String message;
    private String entityType;
    private Integer entityId;
    private String actionUrl;
    private String deduplicationKey;
    private boolean read;
    private Timestamp createdAt;
    private Timestamp readAt;

    public Notification() {}

    public Notification(Long notificationId, int recipientUserId, NotificationType notificationType,
                        NotificationCategory category, String title, String message,
                        String entityType, Integer entityId, String actionUrl,
                        String deduplicationKey, boolean read, Timestamp createdAt, Timestamp readAt) {
        this.notificationId = notificationId;
        this.recipientUserId = recipientUserId;
        this.notificationType = notificationType;
        this.category = category;
        this.title = title;
        this.message = message;
        this.entityType = entityType;
        this.entityId = entityId;
        this.actionUrl = actionUrl;
        this.deduplicationKey = deduplicationKey;
        this.read = read;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public int getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(int recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public void setCategory(NotificationCategory category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public String getDeduplicationKey() {
        return deduplicationKey;
    }

    public void setDeduplicationKey(String deduplicationKey) {
        this.deduplicationKey = deduplicationKey;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getReadAt() {
        return readAt;
    }

    public void setReadAt(Timestamp readAt) {
        this.readAt = readAt;
    }
}
