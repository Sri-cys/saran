-- ============================================================================
-- PSG iTech Library - Feature 8: Student-to-Student Book Interchange
-- Test Data Cleanup Script: 08_book_interchange_test_cleanup.sql
-- Safely removes only TEST records created for Feature 8 verification.
-- ============================================================================

-- 1. Preview records targeted for cleanup
SELECT 'book_interchanges' AS target_table, COUNT(*) AS match_count 
FROM book_interchanges WHERE book_id = 94 OR from_student_id BETWEEN 281 AND 285 OR to_student_id BETWEEN 281 AND 285;

SELECT 'fines' AS target_table, COUNT(*) AS match_count 
FROM fines WHERE fine_id = 9401 OR student_id BETWEEN 281 AND 285;

SELECT 'borrow_transactions' AS target_table, COUNT(*) AS match_count 
FROM borrow_transactions WHERE book_id = 94 OR student_id BETWEEN 281 AND 285;

SELECT 'book_copies' AS target_table, COUNT(*) AS match_count 
FROM book_copies WHERE book_id = 94;

SELECT 'books' AS target_table, COUNT(*) AS match_count 
FROM books WHERE book_id = 94;

SELECT 'users' AS target_table, COUNT(*) AS match_count 
FROM users WHERE user_id BETWEEN 281 AND 285;

-- 2. Delete in foreign-key-safe dependency order
DELETE FROM book_interchanges WHERE book_id = 94 OR from_student_id BETWEEN 281 AND 285 OR to_student_id BETWEEN 281 AND 285;
DELETE FROM payment_audit_log WHERE fine_id = 9401;
DELETE FROM payments WHERE fine_id = 9401;
DELETE FROM fine_adjustments WHERE fine_id = 9401;
DELETE FROM fines WHERE fine_id = 9401 OR student_id BETWEEN 281 AND 285;
DELETE FROM allocations WHERE book_id = 94 OR student_id BETWEEN 281 AND 285;
DELETE FROM reservations WHERE book_id = 94 OR student_id BETWEEN 281 AND 285;
DELETE FROM borrow_transactions WHERE book_id = 94 OR student_id BETWEEN 281 AND 285;
DELETE FROM book_copies WHERE book_id = 94;
DELETE FROM books WHERE book_id = 94;
DELETE FROM users WHERE user_id BETWEEN 281 AND 285;

