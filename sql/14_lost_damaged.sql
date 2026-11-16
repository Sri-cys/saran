-- ==============================================================================
-- PSG iTech Library - Feature 14: Lost or Damaged Book Replacement Migration
-- Additive, safe to re-run, with schema checks and canonical foreign keys.
-- ==============================================================================

-- 1. Add replacement_price to books table if not exists
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
               WHERE TABLE_SCHEMA = DATABASE() 
               AND TABLE_NAME = 'books' 
               AND COLUMN_NAME = 'replacement_price');
SET @sqlstmt := IF(@exist = 0, 
    'ALTER TABLE books ADD COLUMN replacement_price DECIMAL(10,2) NULL AFTER cover_image_url', 
    'SELECT "books.replacement_price already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Add fine_type to fines table if not exists
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
               WHERE TABLE_SCHEMA = DATABASE() 
               AND TABLE_NAME = 'fines' 
               AND COLUMN_NAME = 'fine_type');
SET @sqlstmt := IF(@exist = 0, 
    'ALTER TABLE fines ADD COLUMN fine_type VARCHAR(20) NOT NULL DEFAULT "OVERDUE" AFTER status', 
    'SELECT "fines.fine_type already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Replace UNIQUE KEY on borrow_transaction_id with composite (borrow_transaction_id, fine_type)
-- First add composite unique index if not exists
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS 
               WHERE TABLE_SCHEMA = DATABASE() 
               AND TABLE_NAME = 'fines' 
               AND INDEX_NAME = 'uq_fine_borrow_type');
SET @sqlstmt := IF(@exist = 0, 
    'ALTER TABLE fines ADD UNIQUE KEY uq_fine_borrow_type (borrow_transaction_id, fine_type)', 
    'SELECT "uq_fine_borrow_type already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Now drop old uq_fine_borrow_tx if it still exists
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS 
               WHERE TABLE_SCHEMA = DATABASE() 
               AND TABLE_NAME = 'fines' 
               AND INDEX_NAME = 'uq_fine_borrow_tx');
SET @sqlstmt := IF(@exist > 0, 
    'ALTER TABLE fines DROP KEY uq_fine_borrow_tx', 
    'SELECT "uq_fine_borrow_tx already dropped"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. Extend borrow_transactions.status ENUM to include 'LOST'
ALTER TABLE borrow_transactions 
MODIFY COLUMN status ENUM('ACTIVE','RETURNED','LOST') NOT NULL DEFAULT 'ACTIVE';

-- 5. Create loss_damage_cases table
CREATE TABLE IF NOT EXISTS loss_damage_cases (
    case_id INT NOT NULL AUTO_INCREMENT,
    case_type ENUM('LOST','DAMAGED') NOT NULL,
    status ENUM('REPORTED','ASSESSED','PAYMENT_PENDING','REPLACEMENT_PENDING','REPLACEMENT_SUBMITTED','RESOLVED_PAID','RESOLVED_REPLACED','WAIVED','CANCELLED') NOT NULL DEFAULT 'REPORTED',
    borrow_transaction_id INT NOT NULL,
    student_id INT NOT NULL,
    book_id INT NOT NULL,
    copy_id INT NOT NULL,
    reported_by INT NOT NULL,
    reported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    report_note VARCHAR(300) NULL,
    severity ENUM('MINOR','MAJOR','UNUSABLE') NULL,
    copy_disposition ENUM('WITHDRAWN','RETURNED_TO_CIRCULATION') NULL,
    assessed_by INT NULL,
    assessed_at TIMESTAMP NULL DEFAULT NULL,
    price_used DECIMAL(10,2) NULL,
    age_days INT NULL,
    age_flag TINYINT(1) NOT NULL DEFAULT 0,
    depreciation_applied DECIMAL(6,4) NULL,
    processing_fee DECIMAL(10,2) NULL,
    severity_fraction DECIMAL(4,2) NULL,
    computed_amount DECIMAL(10,2) NULL,
    final_amount DECIMAL(10,2) NULL,
    override_by INT NULL,
    override_reason VARCHAR(200) NULL,
    resolution_choice ENUM('PAYMENT','REPLACEMENT') NULL,
    fine_id INT NULL,
    replacement_deadline DATE NULL,
    replacement_copy_id INT NULL,
    rejection_reason VARCHAR(200) NULL,
    resolved_at TIMESTAMP NULL DEFAULT NULL,
    resolved_by INT NULL,
    active_loan_key INT GENERATED ALWAYS AS (
        IF(status IN ('REPORTED','ASSESSED','PAYMENT_PENDING','REPLACEMENT_PENDING','REPLACEMENT_SUBMITTED'), borrow_transaction_id, NULL)
    ) STORED,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (case_id),
    UNIQUE KEY uq_active_loan_case (active_loan_key),
    KEY idx_cases_student_status (student_id, status),
    KEY idx_cases_status_reported (status, reported_at),
    KEY idx_cases_borrow_tx (borrow_transaction_id),
    KEY idx_cases_book (book_id),
    KEY idx_cases_fine (fine_id),
    CONSTRAINT fk_cases_tx FOREIGN KEY (borrow_transaction_id) REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cases_student FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cases_book FOREIGN KEY (book_id) REFERENCES books (book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cases_copy FOREIGN KEY (copy_id) REFERENCES book_copies (copy_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cases_reported_by FOREIGN KEY (reported_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cases_assessed_by FOREIGN KEY (assessed_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cases_fine FOREIGN KEY (fine_id) REFERENCES fines (fine_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cases_rep_copy FOREIGN KEY (replacement_copy_id) REFERENCES book_copies (copy_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Create loss_damage_case_events table (Append-only audit trail)
CREATE TABLE IF NOT EXISTS loss_damage_case_events (
    event_id INT NOT NULL AUTO_INCREMENT,
    case_id INT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    old_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    actor_user_id INT NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    details VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id),
    KEY idx_events_case (case_id),
    KEY idx_events_created (created_at),
    CONSTRAINT fk_events_case FOREIGN KEY (case_id) REFERENCES loss_damage_cases (case_id) ON DELETE RESTRICT,
    CONSTRAINT fk_events_actor FOREIGN KEY (actor_user_id) REFERENCES users (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
