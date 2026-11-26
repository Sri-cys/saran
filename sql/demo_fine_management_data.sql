-- ============================================================================
-- PSG iTech Library - Fine Management Jury Demo Data Script
-- Module: Feature 6 (Fine Calculation & UPI / Razorpay Payment Reconciliation)
-- Date: 2026-11-26
-- Description:
--   Prepares exactly 3 realistic, institutionally calculated outstanding fines
--   for both test student accounts:
--     1. Sivakathir S (User ID: 4, Reg No: 715522104045)
--     2. Saran Karthi (User ID: 7, Reg No: 715525104210)
--   Adheres strictly to the institutional overdue policy:
--     - ₹2.00 per overdue day
--     - 0-day grace period
--   Preserves existing historical financial records and maintains full foreign
--   key relational integrity with borrow_transactions, books, and book_copies.
-- ============================================================================

USE psgitech_library;

-- ----------------------------------------------------------------------------
-- 1. VERIFY STUDENT ACCOUNTS EXISTENCE
-- ----------------------------------------------------------------------------
SELECT user_id, name, email, register_number, role, active 
FROM users 
WHERE user_id IN (4, 7);

-- ----------------------------------------------------------------------------
-- 2. SEED / REFRESH BORROW TRANSACTIONS FOR DEMO FINES
-- ----------------------------------------------------------------------------
-- Ensure valid borrowing records exist with exact overdue duration matching the fines.

-- Sivakathir S (User ID: 4) Borrowing Transactions:
INSERT INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(1, 4, 1, 1, '2026-10-15', '2026-10-29', '2026-11-12', 'RETURNED', 2, 2)
ON DUPLICATE KEY UPDATE 
    student_id = VALUES(student_id), book_id = VALUES(book_id), copy_id = VALUES(copy_id),
    due_date = VALUES(due_date), return_date = VALUES(return_date), status = VALUES(status);

INSERT INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(3, 4, 3, 8, '2026-10-20', '2026-11-03', '2026-11-25', 'RETURNED', 2, 2)
ON DUPLICATE KEY UPDATE 
    student_id = VALUES(student_id), book_id = VALUES(book_id), copy_id = VALUES(copy_id),
    due_date = VALUES(due_date), return_date = VALUES(return_date), status = VALUES(status);

INSERT INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(5, 4, 4, 10, '2026-10-25', '2026-11-08', '2026-11-16', 'RETURNED', 2, 2)
ON DUPLICATE KEY UPDATE 
    student_id = VALUES(student_id), book_id = VALUES(book_id), copy_id = VALUES(copy_id),
    due_date = VALUES(due_date), return_date = VALUES(return_date), status = VALUES(status);

-- Saran Karthi (User ID: 7) Borrowing Transactions:
INSERT INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(6, 7, 1, 2, '2026-10-18', '2026-11-01', '2026-11-18', 'RETURNED', 2, 2)
ON DUPLICATE KEY UPDATE 
    student_id = VALUES(student_id), book_id = VALUES(book_id), copy_id = VALUES(copy_id),
    due_date = VALUES(due_date), return_date = VALUES(return_date), status = VALUES(status);

INSERT INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(7, 7, 5, 14, '2026-10-22', '2026-11-05', '2026-11-16', 'RETURNED', 2, 2)
ON DUPLICATE KEY UPDATE 
    student_id = VALUES(student_id), book_id = VALUES(book_id), copy_id = VALUES(copy_id),
    due_date = VALUES(due_date), return_date = VALUES(return_date), status = VALUES(status);

INSERT INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(8, 7, 2, 5, '2026-10-28', '2026-11-11', '2026-11-17', 'RETURNED', 2, 2)
ON DUPLICATE KEY UPDATE 
    student_id = VALUES(student_id), book_id = VALUES(book_id), copy_id = VALUES(copy_id),
    due_date = VALUES(due_date), return_date = VALUES(return_date), status = VALUES(status);

-- Preserved Historical Borrowings (Rahul Kumar #2, Saran #4):
INSERT INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(2, 6, 3, 8, '2026-09-10', '2026-09-24', '2026-10-19', 'RETURNED', 2, 2),
(4, 7, 2, 4, '2026-10-07', '2026-10-21', '2026-10-31', 'RETURNED', 2, 2)
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- ----------------------------------------------------------------------------
-- 3. SEED / REFRESH DEMO FINES (EXACTLY 3 OUTSTANDING FINES PER STUDENT)
-- ----------------------------------------------------------------------------

-- Fine #1: Sivakathir S - 14 overdue days @ ₹2.00/day = ₹28.00 (Introduction to Algorithms)
INSERT INTO fines 
(fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type, created_at)
VALUES 
(1, 1, 4, 1, 1, 14, 14, 2.00, 28.00, 0.00, 0.00, 28.00, 'UNPAID', 'OVERDUE', '2026-11-12 10:00:00')
ON DUPLICATE KEY UPDATE 
    paid_amount = 0.00, waived_amount = 0.00, outstanding_amount = 28.00, status = 'UNPAID', overdue_days = 14;

-- Fine #5: Sivakathir S - 8 overdue days @ ₹2.00/day = ₹16.00 (Computer Networks)
INSERT INTO fines 
(fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type, created_at)
VALUES 
(5, 5, 4, 4, 10, 8, 8, 2.00, 16.00, 0.00, 0.00, 16.00, 'UNPAID', 'OVERDUE', '2026-11-16 11:30:00')
ON DUPLICATE KEY UPDATE 
    paid_amount = 0.00, waived_amount = 0.00, outstanding_amount = 16.00, status = 'UNPAID', overdue_days = 8;

-- Fine #6: Sivakathir S - 22 overdue days @ ₹2.00/day = ₹44.00 (Artificial Intelligence)
INSERT INTO fines 
(fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type, created_at)
VALUES 
(6, 3, 4, 3, 8, 22, 22, 2.00, 44.00, 0.00, 0.00, 44.00, 'UNPAID', 'OVERDUE', '2026-11-25 14:00:00')
ON DUPLICATE KEY UPDATE 
    paid_amount = 0.00, waived_amount = 0.00, outstanding_amount = 44.00, status = 'UNPAID', overdue_days = 22;

-- Fine #3: Saran Karthi - 17 overdue days @ ₹2.00/day = ₹34.00 (Introduction to Algorithms)
INSERT INTO fines 
(fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type, created_at)
VALUES 
(3, 6, 7, 1, 2, 17, 17, 2.00, 34.00, 0.00, 0.00, 34.00, 'UNPAID', 'OVERDUE', '2026-11-18 10:00:00')
ON DUPLICATE KEY UPDATE 
    paid_amount = 0.00, waived_amount = 0.00, outstanding_amount = 34.00, status = 'UNPAID', overdue_days = 17;

-- Fine #7: Saran Karthi - 11 overdue days @ ₹2.00/day = ₹22.00 (Operating System Concepts)
INSERT INTO fines 
(fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type, created_at)
VALUES 
(7, 7, 7, 5, 14, 11, 11, 2.00, 22.00, 0.00, 0.00, 22.00, 'UNPAID', 'OVERDUE', '2026-11-16 16:20:00')
ON DUPLICATE KEY UPDATE 
    paid_amount = 0.00, waived_amount = 0.00, outstanding_amount = 22.00, status = 'UNPAID', overdue_days = 11;

-- Fine #8: Saran Karthi - 6 overdue days @ ₹2.00/day = ₹12.00 (Clean Code)
INSERT INTO fines 
(fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type, created_at)
VALUES 
(8, 8, 7, 2, 5, 6, 6, 2.00, 12.00, 0.00, 0.00, 12.00, 'UNPAID', 'OVERDUE', '2026-11-17 09:45:00')
ON DUPLICATE KEY UPDATE 
    paid_amount = 0.00, waived_amount = 0.00, outstanding_amount = 12.00, status = 'UNPAID', overdue_days = 6;

-- Preserved Historical Fines:
-- Fine #2: Rahul Kumar (User ID: 6) - ₹50.00 PAID
INSERT INTO fines 
(fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type, created_at)
VALUES 
(2, 2, 6, 3, 8, 25, 25, 2.00, 50.00, 50.00, 0.00, 0.00, 'PAID', 'OVERDUE', '2026-10-19 15:30:00')
ON DUPLICATE KEY UPDATE status = 'PAID';

-- Fine #4: Saran Karthi (User ID: 7) - ₹20.00 PAID
INSERT INTO fines 
(fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status, fine_type, created_at)
VALUES 
(4, 4, 7, 2, 4, 10, 10, 2.00, 20.00, 20.00, 0.00, 0.00, 'PAID', 'OVERDUE', '2026-10-31 10:00:00')
ON DUPLICATE KEY UPDATE status = 'PAID';

-- ----------------------------------------------------------------------------
-- 4. VERIFICATION QUERIES
-- ----------------------------------------------------------------------------
-- Verify Sivakathir S has exactly 3 UNPAID fines totaling ₹88.00:
SELECT fine_id, borrow_transaction_id, student_id, overdue_days, original_amount, outstanding_amount, status 
FROM fines 
WHERE student_id = 4 AND status = 'UNPAID';

-- Verify Saran Karthi has exactly 3 UNPAID fines totaling ₹68.00:
SELECT fine_id, borrow_transaction_id, student_id, overdue_days, original_amount, outstanding_amount, status 
FROM fines 
WHERE student_id = 7 AND status = 'UNPAID';

-- ----------------------------------------------------------------------------
-- 5. REVERSIBLE ROLLBACK (Optional: Run to revert to initial clean state)
-- ----------------------------------------------------------------------------
-- DELETE FROM fines WHERE fine_id IN (5, 6, 7, 8);
-- DELETE FROM borrow_transactions WHERE transaction_id IN (5, 6, 7, 8);
