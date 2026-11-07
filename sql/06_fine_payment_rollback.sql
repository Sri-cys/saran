-- =============================================================================
-- PSG iTech Library - Feature 6: Fine & Payment Schema Rollback Procedure
-- Database: psgitech_library (MySQL 8.0+ / 26.10+)
-- CAUTION: DEVELOPMENT USE ONLY. Drops financial history tables.
-- Safety: Refuses to proceed if any live fines or payments exist.
-- =============================================================================

USE psgitech_library;

DELIMITER $$

CREATE PROCEDURE sp_rollback_feature6_schema()
BEGIN
    DECLARE v_fine_count INT DEFAULT 0;
    DECLARE v_payment_count INT DEFAULT 0;

    -- 1. Check for live records
    SELECT COUNT(*) INTO v_fine_count FROM fines;
    SELECT COUNT(*) INTO v_payment_count FROM payments;

    IF v_fine_count > 0 OR v_payment_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ABORTED: Cannot rollback Feature 6 while rows exist in fines or payments. Please archive or inspect data first.';
    ELSE
        -- 2. Safe drop in reverse dependency order
        DROP TABLE IF EXISTS payment_audit_log;
        DROP TABLE IF EXISTS fine_adjustments;
        DROP TABLE IF EXISTS payments;
        DROP TABLE IF EXISTS fines;
        SELECT 'Feature 6 schema dropped successfully. Financial tables removed.' AS status;
    END IF;
END$$

DELIMITER ;

CALL sp_rollback_feature6_schema();
DROP PROCEDURE IF EXISTS sp_rollback_feature6_schema;

