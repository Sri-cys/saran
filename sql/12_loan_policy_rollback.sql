-- ==============================================================================
-- PSG iTech Library - Feature 12: Exam-Season Dynamic Borrowing Policy
-- Rollback Script: 12_loan_policy_rollback.sql
-- Safe rollback: refuses to drop columns if any loan records have policy_code set.
-- ==============================================================================

SET @loans_with_policy := (SELECT COUNT(*) FROM borrow_transactions WHERE policy_code IS NOT NULL);

DELIMITER $$
BEGIN NOT ATOMIC
    IF @loans_with_policy > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Rollback aborted: borrow_transactions contains rows with dynamic policy_code set.';
    END IF;
END $$
DELIMITER ;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS loan_policy_windows;
DROP TABLE IF EXISTS book_loan_policy_state;
DROP TABLE IF EXISTS loan_policy_changes;
DROP TABLE IF EXISTS loan_policy_settings;

-- Remove added columns if safe
SET @col_policy_code := (SELECT COUNT(*) FROM information_schema.columns 
                         WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND column_name = 'policy_code');
SET @sql_drop_policy := IF(@col_policy_code > 0, 
    'ALTER TABLE borrow_transactions DROP COLUMN policy_code', 
    'SELECT "Column policy_code does not exist" AS notice');
PREPARE stmt1 FROM @sql_drop_policy; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

SET @col_loan_days := (SELECT COUNT(*) FROM information_schema.columns 
                       WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND column_name = 'loan_days');
SET @sql_drop_days := IF(@col_loan_days > 0, 
    'ALTER TABLE borrow_transactions DROP COLUMN loan_days', 
    'SELECT "Column loan_days does not exist" AS notice');
PREPARE stmt2 FROM @sql_drop_days; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

SET FOREIGN_KEY_CHECKS = 1;
