-- ==============================================================================
-- PSG iTech Library - Feature 14: Test Fixture Cleanup Script
-- Safely cleans up only rows matching the 'f14_' / 'TEST%F14' patterns.
-- ==============================================================================

-- Preview count of records to be removed
SELECT 'loss_damage_case_events' AS tbl, COUNT(*) AS preview_count 
FROM loss_damage_case_events 
WHERE case_id IN (SELECT case_id FROM loss_damage_cases WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%'))
UNION ALL
SELECT 'loss_damage_cases', COUNT(*) 
FROM loss_damage_cases 
WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%')
UNION ALL
SELECT 'payments', COUNT(*) 
FROM payments 
WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%')
UNION ALL
SELECT 'fines', COUNT(*) 
FROM fines 
WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%')
UNION ALL
SELECT 'borrow_transactions', COUNT(*) 
FROM borrow_transactions 
WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%')
UNION ALL
SELECT 'book_copies', COUNT(*) 
FROM book_copies 
WHERE copy_code LIKE 'PSG-F14-%'
UNION ALL
SELECT 'books', COUNT(*) 
FROM books 
WHERE isbn LIKE '978-0-1414-%' OR isbn = 'NOISBN-F14-0003'
UNION ALL
SELECT 'users', COUNT(*) 
FROM users 
WHERE username LIKE 'test_student_f14%';

-- 1. Remove events for F14 test cases
DELETE FROM loss_damage_case_events 
WHERE case_id IN (
    SELECT case_id FROM loss_damage_cases 
    WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%')
);

-- 2. Remove F14 test cases
DELETE FROM loss_damage_cases 
WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%');

-- 3. Remove F14 payments
DELETE FROM payments 
WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%');

-- 4. Remove F14 fines
DELETE FROM fines 
WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%');

-- 5. Remove F14 borrowings
DELETE FROM borrow_transactions 
WHERE student_id IN (SELECT user_id FROM users WHERE username LIKE 'test_student_f14%');

-- 6. Remove F14 book copies
DELETE FROM book_copies 
WHERE copy_code LIKE 'PSG-F14-%';

-- 7. Remove F14 books
DELETE FROM books 
WHERE isbn LIKE '978-0-1414-%' OR isbn = 'NOISBN-F14-0003';

-- 8. Remove F14 users
DELETE FROM users 
WHERE username LIKE 'test_student_f14%';
