-- =============================================================================
-- PSG iTech Library - Feature 5: Automatic Book Allocation Test Cleanup
-- Purpose: Safely delete ONLY records created by 05_automatic_allocation_test_data.sql
-- Caution: DEVELOPMENT MANUAL USE ONLY. Never called from application code.
-- =============================================================================

USE psgitech_library;

-- 1. Dry run inspection: Print the rows that will be removed
SELECT 'ALLOCATIONS TO REMOVE' AS category, allocation_id, reservation_id, student_id, copy_id, status 
FROM allocations 
WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-TEST-ALLOC1');

SELECT 'RESERVATIONS TO REMOVE' AS category, reservation_id, student_id, book_id, status 
FROM reservations 
WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-TEST-ALLOC1');

SELECT 'BORROW TRANSACTIONS TO REMOVE' AS category, transaction_id, student_id, copy_id, status 
FROM borrow_transactions 
WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-TEST-ALLOC1');

SELECT 'BOOK COPIES TO REMOVE' AS category, copy_id, copy_code, status 
FROM book_copies 
WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-TEST-ALLOC1');

SELECT 'BOOKS TO REMOVE' AS category, book_id, isbn, title 
FROM books 
WHERE isbn = '978-0-TEST-ALLOC1';

SELECT 'STUDENTS TO REMOVE' AS category, user_id, email, register_number 
FROM users 
WHERE register_number IN ('TEST-21L901', 'TEST-21L902', 'TEST-21L903', 'TEST-21L904', 'TEST-21L905');

-- 2. Clean removal in strict foreign-key safe order
DELETE FROM allocations 
WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-TEST-ALLOC1');

DELETE FROM reservations 
WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-TEST-ALLOC1');

DELETE FROM borrow_transactions 
WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-TEST-ALLOC1');

DELETE FROM book_copies 
WHERE book_id IN (SELECT book_id FROM books WHERE isbn = '978-0-TEST-ALLOC1');

DELETE FROM books 
WHERE isbn = '978-0-TEST-ALLOC1';

DELETE FROM users 
WHERE register_number IN ('TEST-21L901', 'TEST-21L902', 'TEST-21L903', 'TEST-21L904', 'TEST-21L905');

SELECT 'Cleanup completed successfully.' AS status;

