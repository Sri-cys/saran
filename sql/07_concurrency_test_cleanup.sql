-- =============================================================================
-- PSG iTech Library - Feature 7: Concurrency Test Cleanup
-- Target Schema: psgitech_library_test ONLY
-- =============================================================================

USE psgitech_library_test;

-- Clean test payments and fines
DELETE FROM payment_audit_log WHERE fine_id IN (SELECT fine_id FROM fines WHERE book_id = 99);
DELETE FROM payments WHERE fine_id IN (SELECT fine_id FROM fines WHERE book_id = 99);
DELETE FROM fine_adjustments WHERE fine_id IN (SELECT fine_id FROM fines WHERE book_id = 99);
DELETE FROM fines WHERE book_id = 99;

-- Clean test allocations, reservations, and borrow transactions
DELETE FROM allocations WHERE book_id = 99;
DELETE FROM reservations WHERE book_id = 99;
DELETE FROM borrow_transactions WHERE book_id = 99;

-- Clean test copies and book
DELETE FROM book_copies WHERE book_id = 99;
DELETE FROM books WHERE book_id = 99;

-- Clean test users
DELETE FROM users WHERE user_id IN (201, 202, 203, 204, 205);

