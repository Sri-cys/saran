-- ==============================================================================
-- PSG iTech Library - Feature 11: Notification Test Seed Data
-- Uses TEST-marked deduplication keys and titles for deterministic test setup
-- ==============================================================================

-- Assume user_id = 5 (Student Sivakathir) exists from seed_data.sql
SET @test_user = (SELECT user_id FROM users WHERE email = 'sivakathir.22cs@psgitech.ac.in' LIMIT 1);
SET @test_user = IFNULL(@test_user, 1);

-- Insert sample notifications across categories
INSERT INTO notifications (recipient_user_id, notification_type, category, title, message, entity_type, entity_id, action_url, deduplication_key, is_read, created_at)
VALUES 
(@test_user, 'RESERVATION_CREATED', 'RESERVATION', 'Reservation Confirmed', 'You have been added to the reservation queue for Introduction to Algorithms.', 'RESERVATION', 1, 'my-reservations.html', 'TEST:RESERVATION_CREATED:1', 0, NOW()),
(@test_user, 'ALLOCATION_READY', 'RESERVATION', 'Your Book Is Ready!', 'A copy of Introduction to Algorithms is on hold for you. Please collect by tomorrow.', 'ALLOCATION', 1, 'my-reservations.html', 'TEST:ALLOCATION_READY:1', 0, NOW()),
(@test_user, 'LOAN_ISSUED', 'BORROWING', 'Book Issued', 'Introduction to Algorithms copy PSG-BK1-C01 has been issued to you. Due date: 2026-11-22.', 'BORROW_TRANSACTION', 1, 'my-borrowings.html', 'TEST:LOAN_ISSUED:1', 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(@test_user, 'FINE_CREATED', 'FINES_PAYMENTS', 'Overdue Fine Assessed', 'An overdue fine has been assessed. View your fine ledger for details.', 'FINE', 1, 'my-fines.html', 'TEST:FINE_CREATED:1', 0, NOW())
ON DUPLICATE KEY UPDATE notification_id = notification_id;

-- Insert corresponding outbox rows for email delivery testing
INSERT INTO notification_outbox (notification_id, recipient_user_id, notification_type, delivery_status, attempt_count, next_attempt_at)
SELECT n.notification_id, n.recipient_user_id, n.notification_type, 'PENDING', 0, NOW()
FROM notifications n
LEFT JOIN notification_outbox o ON n.notification_id = o.notification_id
WHERE n.deduplication_key LIKE 'TEST:%' AND n.notification_type IN ('ALLOCATION_READY', 'FINE_CREATED') AND o.outbox_id IS NULL;
