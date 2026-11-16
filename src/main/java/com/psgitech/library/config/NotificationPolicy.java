package com.psgitech.library.config;

import com.psgitech.library.enums.EmailPolicy;
import com.psgitech.library.enums.InAppPolicy;
import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.enums.NotificationType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Institutional policy table mapping each NotificationType to its Category, Delivery Policy,
 * Default Flags, and Internal Target Action URLs.
 */
public final class NotificationPolicy {

    public static final class PolicyRule {
        private final NotificationCategory category;
        private final InAppPolicy inAppPolicy;
        private final EmailPolicy emailPolicy;
        private final boolean defaultInApp;
        private final boolean defaultEmail;
        private final String actionPath;

        public PolicyRule(NotificationCategory category, InAppPolicy inAppPolicy, EmailPolicy emailPolicy,
                          boolean defaultInApp, boolean defaultEmail, String actionPath) {
            this.category = category;
            this.inAppPolicy = inAppPolicy;
            this.emailPolicy = emailPolicy;
            this.defaultInApp = defaultInApp;
            this.defaultEmail = defaultEmail;
            this.actionPath = actionPath;
        }

        public NotificationCategory getCategory() {
            return category;
        }

        public InAppPolicy getInAppPolicy() {
            return inAppPolicy;
        }

        public EmailPolicy getEmailPolicy() {
            return emailPolicy;
        }

        public boolean isDefaultInApp() {
            return defaultInApp;
        }

        public boolean isDefaultEmail() {
            return defaultEmail;
        }

        public String getActionPath() {
            return actionPath;
        }
    }

    private static final Map<NotificationType, PolicyRule> RULES;

    static {
        Map<NotificationType, PolicyRule> map = new EnumMap<>(NotificationType.class);

        // RESERVATION category
        map.put(NotificationType.RESERVATION_CREATED,
                new PolicyRule(NotificationCategory.RESERVATION, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-reservations.html"));
        map.put(NotificationType.ALLOCATION_READY,
                new PolicyRule(NotificationCategory.RESERVATION, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, true, "my-reservations.html"));
        map.put(NotificationType.ALLOCATION_REMINDER,
                new PolicyRule(NotificationCategory.ALLOCATION_REMINDERS, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, true, "my-reservations.html"));
        map.put(NotificationType.RESERVATION_CANCELLED,
                new PolicyRule(NotificationCategory.RESERVATION, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-reservations.html"));
        map.put(NotificationType.RESERVATION_EXPIRED,
                new PolicyRule(NotificationCategory.RESERVATION, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, true, "my-reservations.html"));

        // BORROWING category
        map.put(NotificationType.LOAN_ISSUED,
                new PolicyRule(NotificationCategory.BORROWING, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-borrowings.html"));
        map.put(NotificationType.LOAN_RETURNED,
                new PolicyRule(NotificationCategory.BORROWING, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-borrowings.html"));
        map.put(NotificationType.LOAN_RENEWED,
                new PolicyRule(NotificationCategory.BORROWING, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-borrowings.html"));
        map.put(NotificationType.RENEWAL_REJECTED,
                new PolicyRule(NotificationCategory.BORROWING, InAppPolicy.MANDATORY, EmailPolicy.NEVER, true, false, "my-borrowings.html"));
        map.put(NotificationType.DUE_SOON,
                new PolicyRule(NotificationCategory.DUE_REMINDERS, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, true, "my-borrowings.html"));
        map.put(NotificationType.OVERDUE,
                new PolicyRule(NotificationCategory.DUE_REMINDERS, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, true, "my-borrowings.html"));

        // FINES_PAYMENTS category (Mandatory emails)
        map.put(NotificationType.FINE_CREATED,
                new PolicyRule(NotificationCategory.FINES_PAYMENTS, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "my-fines.html"));
        map.put(NotificationType.PAYMENT_SUBMITTED,
                new PolicyRule(NotificationCategory.FINES_PAYMENTS, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-fines.html"));
        map.put(NotificationType.PAYMENT_PENDING_VERIFICATION,
                new PolicyRule(NotificationCategory.STAFF, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "borrowing-management.html"));
        map.put(NotificationType.PAYMENT_VERIFIED,
                new PolicyRule(NotificationCategory.FINES_PAYMENTS, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "my-fines.html"));
        map.put(NotificationType.PAYMENT_REJECTED,
                new PolicyRule(NotificationCategory.FINES_PAYMENTS, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "my-fines.html"));
        map.put(NotificationType.FINE_WAIVED,
                new PolicyRule(NotificationCategory.FINES_PAYMENTS, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "my-fines.html"));
        map.put(NotificationType.REFUND_RECORDED,
                new PolicyRule(NotificationCategory.FINES_PAYMENTS, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "my-fines.html"));

        // INTERCHANGE category
        map.put(NotificationType.INTERCHANGE_REQUEST_RECEIVED,
                new PolicyRule(NotificationCategory.INTERCHANGE, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, true, "my-borrowings.html"));
        map.put(NotificationType.INTERCHANGE_COMPLETED,
                new PolicyRule(NotificationCategory.INTERCHANGE, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-borrowings.html"));
        map.put(NotificationType.INTERCHANGE_REJECTED,
                new PolicyRule(NotificationCategory.INTERCHANGE, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-borrowings.html"));
        map.put(NotificationType.INTERCHANGE_CANCELLED,
                new PolicyRule(NotificationCategory.INTERCHANGE, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-borrowings.html"));
        map.put(NotificationType.INTERCHANGE_INVALIDATED,
                new PolicyRule(NotificationCategory.INTERCHANGE, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-borrowings.html"));

        // SYSTEM category (Mandatory security alerts)
        map.put(NotificationType.PASSWORD_CHANGED,
                new PolicyRule(NotificationCategory.SYSTEM, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "profile.html"));

        // DISCOVERY category (Stretch)
        map.put(NotificationType.DISCOVERY_NEW_BOOK,
                new PolicyRule(NotificationCategory.DISCOVERY, InAppPolicy.PREFERENCE, EmailPolicy.PREFERENCE, false, false, "smart-discovery.html"));

        // Feature 14: Loss & Damage Cases
        map.put(NotificationType.CASE_REPORTED,
                new PolicyRule(NotificationCategory.BORROWING, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, true, "my-cases.html"));
        map.put(NotificationType.CASE_ASSESSED,
                new PolicyRule(NotificationCategory.FINES_PAYMENTS, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "my-cases.html"));
        map.put(NotificationType.REPLACEMENT_ACCEPTED,
                new PolicyRule(NotificationCategory.BORROWING, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "my-cases.html"));
        map.put(NotificationType.REPLACEMENT_REJECTED,
                new PolicyRule(NotificationCategory.BORROWING, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "my-cases.html"));
        map.put(NotificationType.CASE_RESOLVED,
                new PolicyRule(NotificationCategory.BORROWING, InAppPolicy.MANDATORY, EmailPolicy.MANDATORY, true, true, "my-cases.html"));
        map.put(NotificationType.CASE_CANCELLED,
                new PolicyRule(NotificationCategory.BORROWING, InAppPolicy.MANDATORY, EmailPolicy.PREFERENCE, true, false, "my-cases.html"));

        RULES = Collections.unmodifiableMap(map);
    }

    public static PolicyRule getRule(NotificationType type) {
        PolicyRule rule = RULES.get(type);
        if (rule == null) {
            return new PolicyRule(NotificationCategory.SYSTEM, InAppPolicy.MANDATORY, EmailPolicy.NEVER, true, false, "notifications.html");
        }
        return rule;
    }

    /**
     * Returns true if email notifications for this category cannot be turned off.
     */
    public static boolean isEmailLocked(NotificationCategory category) {
        return category == NotificationCategory.FINES_PAYMENTS || category == NotificationCategory.SYSTEM;
    }

    /**
     * Returns true if in-app notifications for this category cannot be turned off.
     */
    public static boolean isInAppLocked(NotificationCategory category) {
        return category != NotificationCategory.DISCOVERY;
    }

    public static String getLockReason(NotificationCategory category) {
        if (category == NotificationCategory.FINES_PAYMENTS) {
            return "Mandatory financial accounting and audit trail regulations require email delivery.";
        }
        if (category == NotificationCategory.SYSTEM) {
            return "Critical institutional account security alerts cannot be disabled.";
        }
        return null;
    }

    public static boolean getDefaultEmail(NotificationCategory category) {
        switch (category) {
            case RESERVATION:
            case ALLOCATION_REMINDERS:
            case DUE_REMINDERS:
            case FINES_PAYMENTS:
            case INTERCHANGE:
            case SYSTEM:
                return true;
            case BORROWING:
            case STAFF:
            case DISCOVERY:
            default:
                return false;
        }
    }

    public static boolean getDefaultInApp(NotificationCategory category) {
        return category != NotificationCategory.DISCOVERY;
    }

    private NotificationPolicy() {}
}
