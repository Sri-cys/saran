package com.psgitech.library.model;

import com.psgitech.library.enums.DeliveryStatus;
import com.psgitech.library.enums.NotificationType;

import java.sql.Timestamp;

/**
 * Domain model representing an email queue outbox entry.
 */
public class NotificationOutboxRecord {
    private Long outboxId;
    private long notificationId;
    private int recipientUserId;
    private NotificationType notificationType;
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;
    private int attemptCount = 0;
    private Timestamp nextAttemptAt;
    private Timestamp claimedAt;
    private String claimToken;
    private Timestamp createdAt;
    private Timestamp sentAt;
    private String lastErrorCode;

    // Populated dynamically from joins or templates during dispatch
    private String recipientEmail;
    private String title;
    private String message;
    private String actionUrl;
    private String subject;
    private String bodyHtml;

    public NotificationOutboxRecord() {}

    public NotificationOutboxRecord(long notificationId, int recipientUserId, NotificationType notificationType) {
        this.notificationId = notificationId;
        this.recipientUserId = recipientUserId;
        this.notificationType = notificationType;
        this.deliveryStatus = DeliveryStatus.PENDING;
        this.attemptCount = 0;
    }

    public Long getOutboxId() {
        return outboxId;
    }

    public void setOutboxId(Long outboxId) {
        this.outboxId = outboxId;
    }

    public long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(long notificationId) {
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

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Timestamp getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Timestamp nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Timestamp getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Timestamp claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getClaimToken() {
        return claimToken;
    }

    public void setClaimToken(String claimToken) {
        this.claimToken = claimToken;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getSentAt() {
        return sentAt;
    }

    public void setSentAt(Timestamp sentAt) {
        this.sentAt = sentAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
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

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }
}
