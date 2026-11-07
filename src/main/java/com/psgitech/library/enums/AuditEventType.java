package com.psgitech.library.enums;

/**
 * Audit event types recorded in the immutable payment_audit_log table.
 */
public enum AuditEventType {
    FINE_CREATED,
    PAYMENT_INITIATED,
    REFERENCE_SUBMITTED,
    PAYMENT_VERIFIED,
    PAYMENT_REJECTED,
    PAYMENT_CANCELLED,
    WAIVER_APPLIED,
    REFUND_RECORDED,
    DISPUTE_RAISED,
    DISPUTE_RESOLVED;

    public static AuditEventType fromString(String value) {
        if (value == null) return null;
        for (AuditEventType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}

