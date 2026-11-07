-- =============================================================================
-- PSG iTech Library - Enhancements of Features 1-8
-- Migration 09: Test Data Cleanup Script (Development & Test Only)
-- Database: psgitech_library_test (MySQL 8.0+ / InnoDB)
-- Safe, FK-ordered deletion restricted strictly to entities matching 'TEST-%' or '%@example.test'
-- =============================================================================

-- Safety check: only run on test databases
SET @curr_db := DATABASE();
SELECT IF(@curr_db LIKE '%_test', 'PROCEED', 
    'ABORT: Cleanup can only be executed on databases ending in _test') AS safety_status;

-- Step 1: Preview rows to be cleaned
SELECT 'loan_renewals' AS entity, COUNT(*) AS count_to_delete
FROM loan_renewals lr
JOIN borrow_transactions bt ON lr.transaction_id = bt.transaction_id
JOIN books b ON bt.book_id = b.book_id
WHERE b.isbn LIKE '978-0-TEST-ENH-%';

SELECT 'otp_codes' AS entity, COUNT(*) AS count_to_delete
FROM otp_codes
WHERE destination_email LIKE '%@example.test';

-- Step 2: Delete in FK-safe dependency order
-- Clear loan_renewals
DELETE lr FROM loan_renewals lr
JOIN borrow_transactions bt ON lr.transaction_id = bt.transaction_id
JOIN books b ON bt.book_id = b.book_id
WHERE b.isbn LIKE '978-0-TEST-ENH-%';

-- Clear otp_codes
DELETE FROM otp_codes WHERE destination_email LIKE '%@example.test';

-- Clear successor_allocation_id links on allocations
UPDATE allocations a
JOIN books b ON a.book_id = b.book_id
SET a.successor_allocation_id = NULL
WHERE b.isbn LIKE '978-0-TEST-ENH-%';

-- Clear allocations
DELETE a FROM allocations a
JOIN books b ON a.book_id = b.book_id
WHERE b.isbn LIKE '978-0-TEST-ENH-%';

-- Clear reservations
DELETE r FROM reservations r
JOIN books b ON r.book_id = b.book_id
WHERE b.isbn LIKE '978-0-TEST-ENH-%';

-- Clear borrow transactions
DELETE bt FROM borrow_transactions bt
JOIN books b ON bt.book_id = b.book_id
WHERE b.isbn LIKE '978-0-TEST-ENH-%';

-- Clear book copies
DELETE bc FROM book_copies bc
JOIN books b ON bc.book_id = b.book_id
WHERE b.isbn LIKE '978-0-TEST-ENH-%';

-- Clear test books
DELETE FROM books WHERE isbn LIKE '978-0-TEST-ENH-%';

-- Clear test users
DELETE FROM users WHERE email LIKE '%@example.test' OR register_number LIKE 'TEST-REG-ENH%';
