-- =============================================================================
-- PSG iTech Library - Enhancements of Features 1-8
-- Rollback Migration Script 09
-- Database: psgitech_library / psgitech_library_test (MySQL 8.0+ / InnoDB)
-- Safe rollback: refuses to run if any actual enhanced records exist
-- =============================================================================

-- Safety checks
SET @otp_count := (SELECT COUNT(*) FROM otp_codes);
SET @renewal_count := (SELECT COUNT(*) FROM loan_renewals);
SET @alloc_enhanced := (SELECT COUNT(*) FROM allocations WHERE expired_at IS NOT NULL OR successor_allocation_id IS NOT NULL OR reminder_sent_at IS NOT NULL);

SET @can_rollback := IF(@otp_count = 0 AND @renewal_count = 0 AND @alloc_enhanced = 0, 1, 0);

-- Drop loan_renewals table if safe
SET @sql_renew := IF(@can_rollback = 1,
    'DROP TABLE IF EXISTS loan_renewals',
    'SIGNAL SQLSTATE "45000" SET MESSAGE_TEXT = "Rollback aborted: loan_renewals table contains data."');
PREPARE stmt FROM @sql_renew; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop otp_codes table if safe
SET @sql_otp := IF(@can_rollback = 1,
    'DROP TABLE IF EXISTS otp_codes',
    'SELECT "Skipping otp_codes drop" AS notice');
PREPARE stmt FROM @sql_otp; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop foreign key and columns on allocations
SET @fk_exists := (SELECT COUNT(*) FROM information_schema.table_constraints 
                   WHERE table_schema = DATABASE() AND table_name = 'allocations' AND constraint_name = 'fk_alloc_successor');
SET @sql_fk := IF(@can_rollback = 1 AND @fk_exists > 0,
    'ALTER TABLE allocations DROP FOREIGN KEY fk_alloc_successor',
    'SELECT "No foreign key fk_alloc_successor to drop" AS notice');
PREPARE stmt FROM @sql_fk; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_alloc_cols := IF(@can_rollback = 1,
    'ALTER TABLE allocations DROP COLUMN reminder_status, DROP COLUMN reminder_sent_at, DROP COLUMN successor_allocation_id, DROP COLUMN expired_at',
    'SELECT "Skipping allocations column drop" AS notice');
PREPARE stmt FROM @sql_alloc_cols; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop borrow_transactions renewal columns
SET @sql_borrow_cols := IF(@can_rollback = 1,
    'ALTER TABLE borrow_transactions DROP COLUMN last_renewed_at, DROP COLUMN renewal_count',
    'SELECT "Skipping borrow_transactions column drop" AS notice');
PREPARE stmt FROM @sql_borrow_cols; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop users email_verified columns
SET @sql_user_cols := IF(@can_rollback = 1,
    'ALTER TABLE users DROP COLUMN email_verified_at, DROP COLUMN email_verified',
    'SELECT "Skipping users column drop" AS notice');
PREPARE stmt FROM @sql_user_cols; EXECUTE stmt; DEALLOCATE PREPARE stmt;

