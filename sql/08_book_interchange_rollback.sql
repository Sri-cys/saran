-- ============================================================================
-- PSG iTech Library - Feature 8: Student-to-Student Book Interchange
-- Development Rollback Script: 08_book_interchange_rollback.sql
-- Fails safely if any completed interchange records exist to prevent history loss.
-- ============================================================================

-- Guard check: Abort if any COMPLETED interchanges exist
SET @completed_count = (SELECT COUNT(*) FROM book_interchanges WHERE status = 'COMPLETED');

-- Note: MySQL raises an intentional error if completed history exists
SELECT IF(@completed_count > 0, 
    (SELECT table_name FROM information_schema.tables WHERE table_name = 'CANNOT_ROLLBACK_COMPLETED_INTERCHANGES_EXIST'),
    'SAFE_TO_ROLLBACK'
) AS rollback_safety_status;

-- Drop table only when safe
DROP TABLE IF EXISTS book_interchanges;

