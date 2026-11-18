-- ==============================================================================
-- PSG iTech Library Management System
-- Schema Fix: Allow Multiple Fines Per Loan (One Per Fine Type)
-- Ensures that a loan can simultaneously have both an OVERDUE fine and a
-- LOST_DAMAGED compensation fine without unique constraint violations.
-- ==============================================================================

-- 1. Drop old single-fine constraint on borrow_transaction_id if it exists
SET @drop_old_uk = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE fines DROP KEY uq_fine_borrow_tx',
        'SELECT "Old unique constraint uq_fine_borrow_tx does not exist"'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'fines'
      AND INDEX_NAME = 'uq_fine_borrow_tx'
);
PREPARE stmt_drop FROM @drop_old_uk;
EXECUTE stmt_drop;
DEALLOCATE PREPARE stmt_drop;

-- 2. Ensure composite unique constraint on (borrow_transaction_id, fine_type) exists
SET @add_composite_uk = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE fines ADD UNIQUE KEY uq_fine_borrow_type (borrow_transaction_id, fine_type)',
        'SELECT "Composite unique constraint uq_fine_borrow_type already present"'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'fines'
      AND INDEX_NAME = 'uq_fine_borrow_type'
);
PREPARE stmt_add FROM @add_composite_uk;
EXECUTE stmt_add;
DEALLOCATE PREPARE stmt_add;

-- Verification
SELECT 
    TABLE_NAME, 
    INDEX_NAME, 
    COLUMN_NAME, 
    NON_UNIQUE, 
    SEQ_IN_INDEX 
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'fines' 
  AND INDEX_NAME IN ('uq_fine_borrow_tx', 'uq_fine_borrow_type')
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
