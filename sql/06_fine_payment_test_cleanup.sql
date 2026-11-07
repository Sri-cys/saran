-- =============================================================================
-- PSG iTech Library - Feature 6: Test Data Cleanup Script
-- Database: psgitech_library (MySQL 8.0+ / 26.10+)
-- Purpose: Safely delete ONLY records created by 06_fine_payment_test_data.sql.
-- CAUTION: Development manual use only. Never called from application code.
-- =============================================================================

USE psgitech_library;

-- 1. Dry Run Inspection: View records targeted for deletion
SELECT 'AUDIT LOGS TO REMOVE' AS category, audit_id, fine_id, payment_id, event_type, created_at
FROM payment_audit_log
WHERE fine_id IN (
    SELECT fine_id FROM fines WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%')
);

SELECT 'ADJUSTMENTS TO REMOVE' AS category, adjustment_id, fine_id, amount, reason
FROM fine_adjustments
WHERE fine_id IN (
    SELECT fine_id FROM fines WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%')
);

SELECT 'PAYMENTS TO REMOVE' AS category, payment_id, fine_id, payment_code, amount, status
FROM payments
WHERE fine_id IN (
    SELECT fine_id FROM fines WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%')
);

SELECT 'FINES TO REMOVE' AS category, fine_id, borrow_transaction_id, student_id, original_amount, status
FROM fines
WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%');

SELECT 'BORROW TRANSACTIONS TO REMOVE' AS category, transaction_id, student_id, copy_id, status
FROM borrow_transactions
WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%');

SELECT 'BOOK COPIES TO REMOVE' AS category, copy_id, copy_code, status
FROM book_copies
WHERE copy_code LIKE 'PSG-TEST-FN%';

SELECT 'BOOKS TO REMOVE' AS category, book_id, isbn, title
FROM books
WHERE isbn LIKE '978-0-TEST-FINE%';

SELECT 'STUDENTS TO REMOVE' AS category, user_id, email, register_number
FROM users
WHERE register_number IN ('TEST-21L801', 'TEST-21L802', 'TEST-21L803', 'TEST-21L804', 'TEST-21L805');

-- 2. Clean Removal in Strict Foreign-Key Dependency Order
DELETE FROM payment_audit_log
WHERE fine_id IN (
    SELECT fine_id FROM fines WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%')
);

DELETE FROM fine_adjustments
WHERE fine_id IN (
    SELECT fine_id FROM fines WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%')
);

DELETE FROM payments
WHERE fine_id IN (
    SELECT fine_id FROM fines WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%')
);

DELETE FROM fines
WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%');

DELETE FROM borrow_transactions
WHERE book_id IN (SELECT book_id FROM books WHERE isbn LIKE '978-0-TEST-FINE%');

DELETE FROM book_copies
WHERE copy_code LIKE 'PSG-TEST-FN%';

DELETE FROM books
WHERE isbn LIKE '978-0-TEST-FINE%';

DELETE FROM users
WHERE register_number IN ('TEST-21L801', 'TEST-21L802', 'TEST-21L803', 'TEST-21L804', 'TEST-21L805');

SELECT 'Feature 6 test data cleanup completed successfully.' AS status;

