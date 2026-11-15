-- ==============================================================================
-- PSG iTech Library - Feature 12: Exam-Season Dynamic Borrowing Policy
-- Migration Script: 12_loan_policy.sql
-- Database: psgitech_library / psgitech_library_test (MySQL 8.0+ / InnoDB)
-- Safe, idempotent execution with INFORMATION_SCHEMA verification
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Table: loan_policy_settings (Single-row configuration with optimistic versioning)
CREATE TABLE IF NOT EXISTS loan_policy_settings (
    id INT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    enter_threshold INT NOT NULL DEFAULT 4,
    exit_threshold INT NOT NULL DEFAULT 1,
    high_demand_days INT NOT NULL DEFAULT 5,
    version INT NOT NULL DEFAULT 1,
    updated_by INT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_enter_threshold CHECK (enter_threshold >= 2),
    CONSTRAINT chk_exit_threshold CHECK (exit_threshold >= 0),
    CONSTRAINT chk_hysteresis CHECK (exit_threshold < enter_threshold),
    CONSTRAINT chk_high_demand_days CHECK (high_demand_days >= 2 AND high_demand_days < 14)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initialize single configuration row if missing
INSERT INTO loan_policy_settings (id, enabled, enter_threshold, exit_threshold, high_demand_days, version)
SELECT 1, TRUE, 4, 1, 5, 1
WHERE NOT EXISTS (SELECT 1 FROM loan_policy_settings WHERE id = 1);

-- 2. Table: loan_policy_changes (Append-only configuration change audit history)
CREATE TABLE IF NOT EXISTS loan_policy_changes (
    change_id INT AUTO_INCREMENT PRIMARY KEY,
    changed_by INT NULL,
    changed_by_name VARCHAR(100) NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_enabled BOOLEAN NOT NULL,
    new_enabled BOOLEAN NOT NULL,
    old_enter_threshold INT NOT NULL,
    new_enter_threshold INT NOT NULL,
    old_exit_threshold INT NOT NULL,
    new_exit_threshold INT NOT NULL,
    old_high_demand_days INT NOT NULL,
    new_high_demand_days INT NOT NULL,
    version INT NOT NULL,
    INDEX idx_policy_changes_time (changed_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Table: book_loan_policy_state (Per-book persistent hysteresis state)
CREATE TABLE IF NOT EXISTS book_loan_policy_state (
    book_id INT PRIMARY KEY,
    policy_code VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    entered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_demand INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_policy_state_book FOREIGN KEY (book_id) 
        REFERENCES books (book_id) ON DELETE CASCADE,
    INDEX idx_policy_state_code (policy_code, last_evaluated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Table: loan_policy_windows (Stretch D12: Exam-season lower enter threshold windows)
CREATE TABLE IF NOT EXISTS loan_policy_windows (
    window_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    enter_threshold_override INT NOT NULL DEFAULT 2,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_window_dates CHECK (start_date <= end_date),
    CONSTRAINT chk_window_override CHECK (enter_threshold_override >= 2),
    INDEX idx_policy_windows_dates (active, start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Additive Columns to borrow_transactions: policy_code and loan_days (Decision D8)
SET @col_policy_code := (SELECT COUNT(*) FROM information_schema.columns 
                         WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND column_name = 'policy_code');
SET @sql_policy_code := IF(@col_policy_code = 0, 
    'ALTER TABLE borrow_transactions ADD COLUMN policy_code VARCHAR(30) NULL AFTER status', 
    'SELECT "Column policy_code already exists on borrow_transactions" AS notice');
PREPARE stmt1 FROM @sql_policy_code; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

SET @col_loan_days := (SELECT COUNT(*) FROM information_schema.columns 
                       WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND column_name = 'loan_days');
SET @sql_loan_days := IF(@col_loan_days = 0, 
    'ALTER TABLE borrow_transactions ADD COLUMN loan_days INT NULL AFTER policy_code', 
    'SELECT "Column loan_days already exists on borrow_transactions" AS notice');
PREPARE stmt2 FROM @sql_loan_days; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- Index on borrow_transactions for policy reporting
SET @idx_policy := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND index_name = 'idx_borrow_policy');
SET @sql_idx_policy := IF(@idx_policy = 0,
    'ALTER TABLE borrow_transactions ADD INDEX idx_borrow_policy (policy_code, issue_date)',
    'SELECT "Index idx_borrow_policy already exists" AS notice');
PREPARE stmt3 FROM @sql_idx_policy; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

-- Covering Index on reservations for high-speed demand counting
SET @idx_demand := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'reservations' AND index_name = 'idx_res_demand');
SET @sql_idx_demand := IF(@idx_demand = 0,
    'ALTER TABLE reservations ADD INDEX idx_res_demand (book_id, status, student_id)',
    'SELECT "Index idx_res_demand already exists" AS notice');
PREPARE stmt4 FROM @sql_idx_demand; EXECUTE stmt4; DEALLOCATE PREPARE stmt4;

SET FOREIGN_KEY_CHECKS = 1;
