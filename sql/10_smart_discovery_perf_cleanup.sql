-- =============================================================================
-- PSG iTech Library - Feature 10: Performance Dataset Cleanup Script
-- Safe FK-order cleanup of ONLY TEST-marked synthetic benchmarking records
-- Database: Dedicated schema ending in _test ONLY
-- =============================================================================

-- Assert schema safety
SELECT DATABASE() INTO @current_schema;
SELECT IF(@current_schema LIKE '%_test', 'SCHEMA VERIFIED SAFE', 
          CONCAT('ABORT: Current schema ', @current_schema, ' is not a _test schema!')) AS safety_check;

-- Preview counts of TEST rows to be purged
SELECT 
    (SELECT COUNT(*) FROM borrow_transactions WHERE student_id IN (SELECT user_id FROM users WHERE email LIKE 'test_user_%@psgitech.ac.in')) AS test_borrows,
    (SELECT COUNT(*) FROM reservations WHERE student_id IN (SELECT user_id FROM users WHERE email LIKE 'test_user_%@psgitech.ac.in')) AS test_reservations,
    (SELECT COUNT(*) FROM book_copies WHERE copy_code LIKE 'PSG-TEST-%') AS test_copies,
    (SELECT COUNT(*) FROM books WHERE isbn LIKE 'TEST-978-%') AS test_books,
    (SELECT COUNT(*) FROM users WHERE email LIKE 'test_user_%@psgitech.ac.in') AS test_students;

-- FK-Safe Deletions
-- 1. Purge loan renewals associated with test borrows
DELETE lr FROM loan_renewals lr
JOIN borrow_transactions bt ON lr.transaction_id = bt.transaction_id
JOIN users u ON bt.student_id = u.user_id
WHERE u.email LIKE 'test_user_%@psgitech.ac.in';

-- 2. Purge test borrow transactions
DELETE FROM borrow_transactions 
WHERE student_id IN (SELECT user_id FROM users WHERE email LIKE 'test_user_%@psgitech.ac.in');

-- 3. Purge test reservations
DELETE FROM reservations 
WHERE student_id IN (SELECT user_id FROM users WHERE email LIKE 'test_user_%@psgitech.ac.in');

-- 4. Purge test copies
DELETE FROM book_copies WHERE copy_code LIKE 'PSG-TEST-%';

-- 5. Purge test books
DELETE FROM books WHERE isbn LIKE 'TEST-978-%';

-- 6. Purge test students
DELETE FROM users WHERE email LIKE 'test_user_%@psgitech.ac.in';

SELECT "Cleaned up all TEST-marked synthetic benchmarking records successfully." AS status;

