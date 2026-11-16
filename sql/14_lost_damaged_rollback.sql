-- ==============================================================================
-- PSG iTech Library - Feature 14: Rollback Script
-- Refuses to drop tables/columns if any real compensation fine or case exists.
-- ==============================================================================

SET @cases_count := (SELECT COUNT(*) FROM information_schema.TABLES 
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'loss_damage_cases');
SET @existing_cases := IF(@cases_count > 0, (SELECT COUNT(*) FROM loss_damage_cases), 0);

SET @fines_col := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fines' AND COLUMN_NAME = 'fine_type');
SET @compensation_fines := IF(@fines_col > 0, (SELECT COUNT(*) FROM fines WHERE fine_type = 'COMPENSATION'), 0);

-- Guard: Fail rollback if data exists
SET @can_rollback := IF(@existing_cases = 0 AND @compensation_fines = 0, 1, 0);

-- 1. Drop case events table
SET @sqlstmt := IF(@can_rollback = 1, 
    'DROP TABLE IF EXISTS loss_damage_case_events', 
    'SIGNAL SQLSTATE "45000" SET MESSAGE_TEXT = "Cannot rollback: loss_damage_cases or compensation fines exist."');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Drop loss_damage_cases table
SET @sqlstmt := IF(@can_rollback = 1, 
    'DROP TABLE IF EXISTS loss_damage_cases', 
    'SELECT "Skipping drop loss_damage_cases"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Restore fines unique index and remove fine_type
SET @exist_new := (SELECT COUNT(*) FROM information_schema.STATISTICS 
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fines' AND INDEX_NAME = 'uq_fine_borrow_type');
SET @sqlstmt := IF(@can_rollback = 1 AND @exist_new > 0, 
    'ALTER TABLE fines ADD UNIQUE KEY uq_fine_borrow_tx (borrow_transaction_id), DROP KEY uq_fine_borrow_type', 
    'SELECT "Skipping fines index rollback"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sqlstmt := IF(@can_rollback = 1 AND @fines_col > 0, 
    'ALTER TABLE fines DROP COLUMN fine_type', 
    'SELECT "Skipping fines column drop"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. Revert borrow_transactions status enum
SET @sqlstmt := IF(@can_rollback = 1, 
    'ALTER TABLE borrow_transactions MODIFY COLUMN status ENUM("ACTIVE","RETURNED") NOT NULL DEFAULT "ACTIVE"', 
    'SELECT "Skipping borrow_transactions status revert"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Drop replacement_price from books
SET @book_col := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'books' AND COLUMN_NAME = 'replacement_price');
SET @sqlstmt := IF(@can_rollback = 1 AND @book_col > 0, 
    'ALTER TABLE books DROP COLUMN replacement_price', 
    'SELECT "Skipping replacement_price drop"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
