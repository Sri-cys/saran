-- ==============================================================================
-- PSG iTech Library - Feature 11: Large-Volume Notification Performance Seed
-- Inserts ~200,000 notifications across ~2,000 users for index and query stress tests
-- Deduplication keys are prefixed with 'PERF_TEST:' for clean teardown.
-- ==============================================================================

SET SESSION cte_max_recursion_depth = 250000;

-- Fast generation of numbers 1 to 200,000 using recursive CTE in batches
DROP PROCEDURE IF EXISTS seed_perf_notifications;
DELIMITER //
CREATE PROCEDURE seed_perf_notifications()
BEGIN
    DECLARE i INT DEFAULT 0;
    
    -- Ensure test students exist (1 to 2000 user ids)
    -- Insert 200,000 notifications across user_ids 1 to 2000
    WHILE i < 4 DO
        INSERT INTO notifications (recipient_user_id, notification_type, category, title, message, action_url, deduplication_key, is_read, created_at)
        WITH RECURSIVE seq AS (
            SELECT 1 AS n
            UNION ALL
            SELECT n + 1 FROM seq WHERE n < 50000
        )
        SELECT 
            ((n + (i * 50000)) % 2000) + 1 AS recipient_user_id,
            CASE (n % 5)
                WHEN 0 THEN 'RESERVATION_CREATED'
                WHEN 1 THEN 'ALLOCATION_READY'
                WHEN 2 THEN 'LOAN_ISSUED'
                WHEN 3 THEN 'FINE_CREATED'
                ELSE 'DUE_SOON'
            END AS notification_type,
            CASE (n % 5)
                WHEN 0 THEN 'RESERVATION'
                WHEN 1 THEN 'RESERVATION'
                WHEN 2 THEN 'BORROWING'
                WHEN 3 THEN 'FINES_PAYMENTS'
                ELSE 'DUE_REMINDERS'
            END AS category,
            CONCAT('Notification Title #', (n + (i * 50000))) AS title,
            CONCAT('This is an automated performance benchmark notification message for event item #', (n + (i * 50000))) AS message,
            'notifications.html' AS action_url,
            CONCAT('PERF_TEST:', (n + (i * 50000))) AS deduplication_key,
            IF((n % 3) = 0, 1, 0) AS is_read,
            DATE_SUB(NOW(6), INTERVAL (n % 1000) HOUR) AS created_at
        FROM seq
        ON DUPLICATE KEY UPDATE notification_id = notification_id;
        
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

-- CALL seed_perf_notifications(); -- invoked during performance benchmark testing
-- DROP PROCEDURE IF EXISTS seed_perf_notifications;
