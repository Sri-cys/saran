-- ==============================================================================
-- PSG iTech Library - Feature 11: Notification Test Cleanup
-- Removes only TEST-marked notifications and associated outbox/preference rows
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Preview TEST rows to be removed
SELECT 'Notifications to delete' AS label, COUNT(*) AS count
FROM notifications
WHERE deduplication_key LIKE 'TEST:%';

-- 2. Delete outbox rows linked to TEST notifications
DELETE FROM notification_outbox 
WHERE notification_id IN (SELECT notification_id FROM notifications WHERE deduplication_key LIKE 'TEST:%');

-- 3. Delete TEST notifications
DELETE FROM notifications 
WHERE deduplication_key LIKE 'TEST:%';

-- 4. Delete preferences for test categories or dummy test users if any
DELETE FROM notification_preferences
WHERE category LIKE 'TEST_%';

SET FOREIGN_KEY_CHECKS = 1;
