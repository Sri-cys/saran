-- ==============================================================================
-- PSG iTech Library - Feature 12: Exam-Season Dynamic Borrowing Policy
-- Test Data Cleanup: 12_loan_policy_test_cleanup.sql
-- Safely cleans up test fixtures
-- ==============================================================================

DELETE FROM book_loan_policy_state WHERE book_id = 7770;
DELETE FROM borrow_transactions WHERE book_id = 7770;
DELETE FROM reservations WHERE book_id = 7770;
DELETE FROM book_copies WHERE book_id = 7770;
DELETE FROM books WHERE book_id = 7770;
