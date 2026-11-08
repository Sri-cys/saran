package com.psgitech.library.model;

import com.psgitech.library.enums.NotificationType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable draft object encapsulating the parameters required to construct and persist a notification.
 */
public final class NotificationDraft {
    private final NotificationType type;
    private final int recipientUserId;
    private final String deduplicationKey;
    private final String entityType;
    private final Integer entityId;
    private final Map<String, String> parameters;

    private NotificationDraft(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "NotificationType is required");
        if (builder.recipientUserId <= 0) {
            throw new IllegalArgumentException("Valid recipientUserId is required: " + builder.recipientUserId);
        }
        this.recipientUserId = builder.recipientUserId;
        this.deduplicationKey = Objects.requireNonNull(builder.deduplicationKey, "DeduplicationKey is required");
        this.entityType = builder.entityType;
        this.entityId = builder.entityId;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(builder.parameters));
    }

    public NotificationType getType() {
        return type;
    }

    public int getRecipientUserId() {
        return recipientUserId;
    }

    public String getDeduplicationKey() {
        return deduplicationKey;
    }

    public String getEntityType() {
        return entityType;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getParam(String key) {
        return parameters.get(key);
    }

    public static Builder builder(NotificationType type, int recipientUserId, String deduplicationKey) {
        return new Builder(type, recipientUserId, deduplicationKey);
    }

    public static final class Builder {
        private final NotificationType type;
        private final int recipientUserId;
        private final String deduplicationKey;
        private String entityType;
        private Integer entityId;
        private final Map<String, String> parameters = new HashMap<>();

        public Builder(NotificationType type, int recipientUserId, String deduplicationKey) {
            this.type = type;
            this.recipientUserId = recipientUserId;
            this.deduplicationKey = deduplicationKey;
        }

        public Builder entity(String entityType, Integer entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
            return this;
        }

        public Builder param(String key, String value) {
            if (key != null && value != null) {
                this.parameters.put(key, value);
            }
            return this;
        }

        public Builder params(Map<String, String> params) {
            if (params != null) {
                this.parameters.putAll(params);
            }
            return this;
        }

        public NotificationDraft build() {
            return new NotificationDraft(this);
        }
    }
}
