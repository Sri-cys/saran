-- =============================================================================
-- PSG iTech Library - Feature 4: Test Data Cleanup (MANUAL DEVELOPER ONLY)
-- Strict Safety Rules:
-- 1. Affects ONLY rows containing explicit "TEST-" identifiers.
-- 2. FK-safe order: reservations -> borrow_transactions -> book_copies -> books -> users.
-- 3. Pre-execution SELECT queries allow developers to verify records before deletion.
-- =============================================================================

USE psgitech_library;

-- -----------------------------------------------------------------------------
-- Step 0: PREVIEW RECORDS TO BE DELETED (Verification Check)
-- -----------------------------------------------------------------------------
SELECT '--- TEST RESERVATIONS TO BE CLEANED ---' AS preview_header;
SELECT reservation_id, student_id, book_id, status FROM reservations
WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE 'TEST-%')
   OR student_id IN (SELECT user_id FROM users WHERE email LIKE 'test.student.%@psgitech.test');

SELECT '--- TEST BORROW TRANSACTIONS TO BE CLEANED ---' AS preview_header;
SELECT transaction_id, student_id, book_id, copy_id FROM borrow_transactions
WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE 'TEST-%')
   OR student_id IN (SELECT user_id FROM users WHERE email LIKE 'test.student.%@psgitech.test');

SELECT '--- TEST COPIES TO BE CLEANED ---' AS preview_header;
SELECT copy_id, copy_code, book_id FROM book_copies WHERE copy_code LIKE 'TEST-CPY-%';

SELECT '--- TEST BOOKS TO BE CLEANED ---' AS preview_header;
SELECT book_id, isbn, title FROM books WHERE isbn LIKE 'TEST-%';

SELECT '--- TEST USERS TO BE CLEANED ---' AS preview_header;
SELECT user_id, email, register_number FROM users WHERE email LIKE 'test.student.%@psgitech.test';

-- -----------------------------------------------------------------------------
-- Step 1: Delete Test Reservations
-- -----------------------------------------------------------------------------
DELETE FROM reservations
WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE 'TEST-%')
   OR student_id IN (SELECT user_id FROM users WHERE email LIKE 'test.student.%@psgitech.test');

-- -----------------------------------------------------------------------------
-- Step 2: Delete Test Borrow Transactions (if created during tests)
-- -----------------------------------------------------------------------------
DELETE FROM borrow_transactions
WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE 'TEST-%')
   OR student_id IN (SELECT user_id FROM users WHERE email LIKE 'test.student.%@psgitech.test');

-- -----------------------------------------------------------------------------
-- Step 3: Delete Test Copies
-- -----------------------------------------------------------------------------
DELETE FROM book_copies WHERE copy_code LIKE 'TEST-CPY-%';

-- -----------------------------------------------------------------------------
-- Step 4: Delete Test Books
-- -----------------------------------------------------------------------------
DELETE FROM books WHERE isbn LIKE 'TEST-%';

-- -----------------------------------------------------------------------------
-- Step 5: Delete Test Users
-- -----------------------------------------------------------------------------
DELETE FROM users WHERE email LIKE 'test.student.%@psgitech.test';

