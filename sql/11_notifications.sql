-- ==============================================================================
-- PSG iTech Library - Feature 11: Notification and Alert Management System
-- Schema Migration: 11_notifications.sql
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Notifications Table (Central in-app notification ledger)
-- Note: Soft-reference on recipient_user_id (no FK to users per Decision D3 to prevent deadlock cycles)
CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id INT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    category VARCHAR(30) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    entity_type VARCHAR(30) NULL,
    entity_id INT NULL,
    action_url VARCHAR(200) NULL,
    deduplication_key VARCHAR(120) NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at TIMESTAMP NULL,
    UNIQUE KEY uq_notification_dedupe (recipient_user_id, deduplication_key),
    KEY idx_notif_user_id (recipient_user_id, notification_id),
    KEY idx_notif_user_read (recipient_user_id, is_read, notification_id),
    KEY idx_notif_user_cat (recipient_user_id, category, notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Notification Preferences Table
-- Note: Rows created only when user overrides code defaults (Decision D5). No FK per D3.
CREATE TABLE IF NOT EXISTS notification_preferences (
    preference_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    category VARCHAR(30) NOT NULL,
    in_app_enabled TINYINT(1) NOT NULL DEFAULT 1,
    email_enabled TINYINT(1) NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_category (user_id, category),
    KEY idx_pref_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Notification Email Outbox Table
-- Stores outbound email delivery queue. Foreign key to notifications is safe (same transaction).
CREATE TABLE IF NOT EXISTS notification_outbox (
    outbox_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL UNIQUE,
    recipient_user_id INT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    delivery_status ENUM('PENDING','PROCESSING','SENT','FAILED','SKIPPED') NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    claimed_at TIMESTAMP NULL,
    claim_token VARCHAR(40) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    last_error_code VARCHAR(40) NULL,
    CONSTRAINT fk_outbox_notification FOREIGN KEY (notification_id) 
        REFERENCES notifications(notification_id) ON DELETE CASCADE,
    KEY idx_outbox_poll (delivery_status, next_attempt_at, outbox_id),
    KEY idx_outbox_recipient (recipient_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Stretch D18 Table: Notification Interests (for opt-in discovery alerts)
CREATE TABLE IF NOT EXISTS notification_interests (
    interest_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    interest_type VARCHAR(30) NOT NULL,
    value VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_interest (user_id, interest_type, value),
    KEY idx_interest_type_val (interest_type, value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
