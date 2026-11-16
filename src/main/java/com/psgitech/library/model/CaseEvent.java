package com.psgitech.library.model;

import java.sql.Timestamp;

/**
 * Append-only audit trail event for Loss & Damage lifecycle transitions.
 */
public class CaseEvent {
    private Integer eventId;
    private int caseId;
    private String eventType;
    private String oldStatus;
    private String newStatus;
    private int actorUserId;
    private String actorRole;
    private String details;
    private Timestamp createdAt;

    public CaseEvent() {}

    public CaseEvent(int caseId, String eventType, String oldStatus, String newStatus,
                     int actorUserId, String actorRole, String details) {
        this.caseId = caseId;
        this.eventType = eventType;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.details = details;
    }

    public Integer getEventId() { return eventId; }
    public void setEventId(Integer eventId) { this.eventId = eventId; }

    public int getCaseId() { return caseId; }
    public void setCaseId(int caseId) { this.caseId = caseId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public int getActorUserId() { return actorUserId; }
    public void setActorUserId(int actorUserId) { this.actorUserId = actorUserId; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
