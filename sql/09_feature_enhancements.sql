-- =============================================================================
-- PSG iTech Library - Enhancements of Features 1-8
-- Migration Script 09: Security, Consistency, and Feature Enhancements Schema
-- Database: psgitech_library / psgitech_library_test (MySQL 8.0+ / InnoDB)
-- Safe, idempotent execution with INFORMATION_SCHEMA verification
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Users Table Enhancements (F1 Email Verification)
-- -----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns 
                    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'email_verified');
SET @sql := IF(@col_exists = 0, 
    'ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE AFTER active, ADD COLUMN email_verified_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP AFTER email_verified', 
    'SELECT "Columns email_verified already exist on users" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. OTP Codes Table (F1 Email Verification & F8 Handover OTP)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS otp_codes (
    otp_id INT AUTO_INCREMENT PRIMARY KEY,
    purpose ENUM('EMAIL_VERIFICATION', 'INTERCHANGE_HANDOVER', 'PASSWORD_RESET') NOT NULL,
    subject_id INT NOT NULL,
    destination_email VARCHAR(100) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    status ENUM('ACTIVE', 'VERIFIED', 'CONSUMED', 'SUPERSEDED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL DEFAULT NULL,

    -- Generated stored column guaranteeing at most one ACTIVE OTP code per (purpose, subject_id)
    active_subject_lock VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' 
             THEN CONCAT(purpose, '-', subject_id) 
             ELSE NULL 
        END
    ) STORED,

    UNIQUE KEY uq_active_otp_subject (active_subject_lock),
    INDEX idx_otp_lookup (purpose, subject_id, status),
    INDEX idx_otp_email_created (destination_email, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Borrow Transactions Table Enhancements (F3 Loan Renewals)
-- -----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns 
                    WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND column_name = 'renewal_count');
SET @sql := IF(@col_exists = 0, 
    'ALTER TABLE borrow_transactions ADD COLUMN renewal_count INT NOT NULL DEFAULT 0 AFTER returned_by, ADD COLUMN last_renewed_at TIMESTAMP NULL DEFAULT NULL AFTER renewal_count', 
    'SELECT "Columns renewal_count already exist on borrow_transactions" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 4. Loan Renewals Audit Table (F3 Loan Renewals History)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS loan_renewals (
    renewal_id INT AUTO_INCREMENT PRIMARY KEY,
    transaction_id INT NOT NULL,
    student_id INT NOT NULL,
    previous_due_date DATE NOT NULL,
    new_due_date DATE NOT NULL,
    renewal_number INT NOT NULL,
    renewed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    renewed_by INT NOT NULL,

    CONSTRAINT fk_renewal_tx FOREIGN KEY (transaction_id) 
        REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    CONSTRAINT fk_renewal_student FOREIGN KEY (student_id) 
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_renewal_by FOREIGN KEY (renewed_by) 
        REFERENCES users (user_id) ON DELETE RESTRICT,

    INDEX idx_renewal_tx (transaction_id),
    INDEX idx_renewal_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 5. Allocations Table Enhancements (F5 Expiry & Reminders)
-- -----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns 
                    WHERE table_schema = DATABASE() AND table_name = 'allocations' AND column_name = 'expired_at');
SET @sql := IF(@col_exists = 0, 
    'ALTER TABLE allocations ADD COLUMN expired_at TIMESTAMP NULL DEFAULT NULL AFTER expires_at', 
    'SELECT "Column expired_at already exists on allocations" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns 
                    WHERE table_schema = DATABASE() AND table_name = 'allocations' AND column_name = 'successor_allocation_id');
SET @sql := IF(@col_exists = 0, 
    'ALTER TABLE allocations ADD COLUMN successor_allocation_id INT NULL DEFAULT NULL AFTER expired_at', 
    'SELECT "Column successor_allocation_id already exists on allocations" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns 
                    WHERE table_schema = DATABASE() AND table_name = 'allocations' AND column_name = 'reminder_sent_at');
SET @sql := IF(@col_exists = 0, 
    'ALTER TABLE allocations ADD COLUMN reminder_sent_at TIMESTAMP NULL DEFAULT NULL AFTER successor_allocation_id, ADD COLUMN reminder_status VARCHAR(30) NULL DEFAULT NULL AFTER reminder_sent_at', 
    'SELECT "Columns reminder_sent_at already exist on allocations" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists := (SELECT COUNT(*) FROM information_schema.table_constraints 
                   WHERE table_schema = DATABASE() AND table_name = 'allocations' AND constraint_name = 'fk_alloc_successor');
SET @sql := IF(@fk_exists = 0, 
    'ALTER TABLE allocations ADD CONSTRAINT fk_alloc_successor FOREIGN KEY (successor_allocation_id) REFERENCES allocations (allocation_id) ON DELETE RESTRICT', 
    'SELECT "Constraint fk_alloc_successor already exists on allocations" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
