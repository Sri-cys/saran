-- =============================================================================
-- PSG iTech Library - Feature 6: Fine & Payment Diagnostics (Read-Only)
-- Database: psgitech_library (MySQL 8.0+ / 26.10+)
-- Purpose: Inspect financial invariants, detect orphaned states and retroactive cases.
-- =============================================================================

USE psgitech_library;

-- -----------------------------------------------------------------------------
-- Diagnostic A: RETURNED borrow transactions that were overdue but have no fine
-- Detects pre-Feature 6 historical returns (D11 non-retroactive policy monitoring).
-- -----------------------------------------------------------------------------
SELECT 
    'A: OVERDUE RETURNS WITHOUT FINE (D11 HISTORICAL)' AS diagnostic_category,
    bt.transaction_id,
    bt.student_id,
    u.name AS student_name,
    u.register_number,
    b.title AS book_title,
    bt.due_date,
    bt.return_date,
    DATEDIFF(bt.return_date, bt.due_date) AS overdue_days
FROM borrow_transactions bt
JOIN users u ON bt.student_id = u.user_id
JOIN books b ON bt.book_id = b.book_id
LEFT JOIN fines f ON bt.transaction_id = f.borrow_transaction_id
WHERE bt.status = 'RETURNED'
  AND bt.return_date > bt.due_date
  AND f.fine_id IS NULL;

-- -----------------------------------------------------------------------------
-- Diagnostic B: Fines whose original amount != paid + waived + outstanding
-- Detects mathematical invariant violations (O = P + W + R).
-- -----------------------------------------------------------------------------
SELECT 
    'B: FINE INVARIANT VIOLATIONS (O != P + W + R)' AS diagnostic_category,
    f.fine_id,
    f.borrow_transaction_id,
    f.student_id,
    f.original_amount,
    f.paid_amount,
    f.waived_amount,
    f.outstanding_amount,
    (f.paid_amount + f.waived_amount + f.outstanding_amount) AS calculated_sum,
    f.status
FROM fines f
WHERE f.original_amount != (f.paid_amount + f.waived_amount + f.outstanding_amount)
   OR f.paid_amount < 0.00
   OR f.waived_amount < 0.00
   OR f.outstanding_amount < 0.00;

-- -----------------------------------------------------------------------------
-- Diagnostic C: SUCCESS payments whose total != fines.paid_amount
-- Detects ledger desynchronization between verified payments and fine records.
-- -----------------------------------------------------------------------------
SELECT 
    'C: PAYMENT SUM MISMATCH WITH FINE PAID AMOUNT' AS diagnostic_category,
    f.fine_id,
    f.paid_amount AS fine_recorded_paid,
    COALESCE(SUM(p.amount), 0.00) AS total_verified_payments,
    f.status AS fine_status
FROM fines f
LEFT JOIN payments p ON f.fine_id = p.fine_id AND p.status = 'SUCCESS'
GROUP BY f.fine_id, f.paid_amount, f.status
HAVING f.paid_amount != COALESCE(SUM(p.amount), 0.00);

-- -----------------------------------------------------------------------------
-- Diagnostic D: Fines with two or more in-flight payments (INITIATED or PENDING_VERIFICATION)
-- -----------------------------------------------------------------------------
SELECT 
    'D: MULTIPLE IN-FLIGHT PAYMENTS DETECTED' AS diagnostic_category,
    p.fine_id,
    COUNT(p.payment_id) AS in_flight_count,
    GROUP_CONCAT(p.payment_code) AS payment_codes
FROM payments p
WHERE p.status IN ('INITIATED', 'PENDING_VERIFICATION')
GROUP BY p.fine_id
HAVING COUNT(p.payment_id) > 1;

