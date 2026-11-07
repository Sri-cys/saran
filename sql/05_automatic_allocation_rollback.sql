-- =============================================================================
-- PSG iTech Library - Feature 5: Automatic Book Allocation Rollback Script
-- Database: psgitech_library (MySQL 8.0+ / 26.10+ / InnoDB)
-- Caution: DEVELOPMENT ONLY. Run only to cleanly revert Feature 5 migration.
-- =============================================================================

USE psgitech_library;

-- Safety check: ensure no active allocations exist before dropping table
SET @active_allocations = (SELECT COUNT(*) FROM allocations WHERE status = 'ALLOCATED');
SET @allocated_copies = (SELECT COUNT(*) FROM book_copies WHERE status = 'ALLOCATED');

DELIMITER $$

CREATE PROCEDURE RollbackFeature5()
BEGIN
    DECLARE active_alloc INT DEFAULT 0;
    DECLARE active_copy INT DEFAULT 0;

    SELECT COUNT(*) INTO active_alloc FROM allocations WHERE status = 'ALLOCATED';
    SELECT COUNT(*) INTO active_copy FROM book_copies WHERE status = 'ALLOCATED';

    IF active_alloc > 0 OR active_copy > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Rollback aborted: Active ALLOCATED records or copies exist. Release all active allocations before rolling back.';
    ELSE
        DROP TABLE IF EXISTS allocations;
        SELECT 'Feature 5 allocations table rolled back successfully.' AS result;
    END IF;
END$$

DELIMITER ;

CALL RollbackFeature5();
DROP PROCEDURE IF EXISTS RollbackFeature5;

