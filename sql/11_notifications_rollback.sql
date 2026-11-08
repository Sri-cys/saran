-- ==============================================================================
-- PSG iTech Library - Feature 11: Notification and Alert Management System
-- Rollback Script: 11_notifications_rollback.sql
-- Fails safely if user data exists (must be emptied first).
-- ==============================================================================

SET @notif_count = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'notifications');
SET @outbox_count = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'notification_outbox');

-- Refuse to drop if production/user records exist
DROP PROCEDURE IF EXISTS check_empty_and_drop;
DELIMITER //
CREATE PROCEDURE check_empty_and_drop()
BEGIN
    DECLARE n_rows INT DEFAULT 0;
    DECLARE o_rows INT DEFAULT 0;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'notifications') THEN
        SELECT COUNT(*) INTO n_rows FROM notifications;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'notification_outbox') THEN
        SELECT COUNT(*) INTO o_rows FROM notification_outbox;
    END IF;

    IF (n_rows > 0 OR o_rows > 0) THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Rollback aborted: notification tables contain records. Please back up or truncate before running rollback.';
    ELSE
        DROP TABLE IF EXISTS notification_interests;
        DROP TABLE IF EXISTS notification_outbox;
        DROP TABLE IF EXISTS notification_preferences;
        DROP TABLE IF EXISTS notifications;
        SELECT 'Feature 11 notification tables successfully dropped.' AS status;
    END IF;
END //
DELIMITER ;

CALL check_empty_and_drop();
DROP PROCEDURE IF EXISTS check_empty_and_drop;
