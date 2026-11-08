-- ==============================================================================
-- PSG iTech Library - Feature 11: Notification System Diagnostics
-- SELECT-only diagnostic queries for data integrity, leak audit, and worker status
-- ==============================================================================

-- 1. Check outbox records without a parent notification (orphan check)
SELECT o.outbox_id, o.notification_id, o.recipient_user_id, o.delivery_status
FROM notification_outbox o
LEFT JOIN notifications n ON o.notification_id = n.notification_id
WHERE n.notification_id IS NULL;

-- 2. Check SENT outbox rows missing sent_at timestamp
SELECT outbox_id, recipient_user_id, delivery_status, created_at, sent_at
FROM notification_outbox
WHERE delivery_status = 'SENT' AND sent_at IS NULL;

-- 3. Check PROCESSING rows stuck longer than lease (lease = 300 seconds)
SELECT outbox_id, recipient_user_id, claim_token, claimed_at, attempt_count
FROM notification_outbox
WHERE delivery_status = 'PROCESSING' 
  AND claimed_at < DATE_SUB(NOW(), INTERVAL 300 SECOND);

-- 4. Check PENDING rows older than 1 hour (unprocessed backlog)
SELECT outbox_id, recipient_user_id, notification_type, next_attempt_at, attempt_count
FROM notification_outbox
WHERE delivery_status = 'PENDING' 
  AND next_attempt_at < DATE_SUB(NOW(), INTERVAL 1 HOUR);

-- 5. Audit: Scan for OTP/code leakages in notifications (6-digit patterns near 'code' or 'OTP')
SELECT notification_id, recipient_user_id, notification_type, message
FROM notifications
WHERE message REGEXP '([Oo][Tt][Pp]|[Cc][Oo][Dd][Ee])[^0-9]{0,10}[0-9]{6}'
   OR message REGEXP '[0-9]{6}[^0-9]{0,10}([Oo][Tt][Pp]|[Cc][Oo][Dd][Ee])';

-- 6. Audit: Scan for UTR-like banking reference patterns in notifications
SELECT notification_id, recipient_user_id, notification_type, message
FROM notifications
WHERE message REGEXP '([Uu][Tt][Rr]|[Rr][Ee][Ff])[^0-9]{0,10}[0-9]{12}'
   OR message REGEXP '[0-9]{12}';

-- 7. Check preferences for unknown/invalid categories
SELECT preference_id, user_id, category
FROM notification_preferences
WHERE category NOT IN ('RESERVATION', 'ALLOCATION_REMINDERS', 'BORROWING', 'DUE_REMINDERS', 'FINES_PAYMENTS', 'INTERCHANGE', 'SYSTEM', 'DISCOVERY');

-- 8. High-level delivery summary
SELECT delivery_status, COUNT(*) AS count, MIN(created_at) AS oldest, MAX(created_at) AS newest
FROM notification_outbox
GROUP BY delivery_status;
