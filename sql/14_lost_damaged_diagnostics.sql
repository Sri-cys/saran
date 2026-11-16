-- ==============================================================================
-- PSG iTech Library - Feature 14: Diagnostics Query Script
-- Strictly read-only (SELECT only). All queries must return 0 rows in a healthy database.
-- ==============================================================================

-- 1. Check for duplicate open cases per borrowing transaction
SELECT borrow_transaction_id, COUNT(*) AS open_case_count
FROM loss_damage_cases
WHERE status IN ('REPORTED','ASSESSED','PAYMENT_PENDING','REPLACEMENT_PENDING','REPLACEMENT_SUBMITTED')
GROUP BY borrow_transaction_id
HAVING COUNT(*) > 1;

-- 2. Check for confirmed LOST cases where loan is not LOST or copy is not ARCHIVED
SELECT c.case_id, c.borrow_transaction_id, bt.status AS loan_status, bc.status AS copy_status
FROM loss_damage_cases c
JOIN borrow_transactions bt ON c.borrow_transaction_id = bt.transaction_id
JOIN book_copies bc ON c.copy_id = bc.copy_id
WHERE c.case_type = 'LOST'
  AND c.status NOT IN ('REPORTED', 'CANCELLED')
  AND (bt.status != 'LOST' OR bc.status != 'ARCHIVED');

-- 3. Check for archived copies that are erroneously marked ALLOCATED or BORROWED
SELECT copy_id, copy_code, status
FROM book_copies
WHERE status = 'ARCHIVED'
  AND (
      copy_id IN (SELECT copy_id FROM allocations WHERE status = 'ALLOCATED')
      OR copy_id IN (SELECT copy_id FROM borrow_transactions WHERE status = 'ACTIVE')
  );

-- 4. Check for RESOLVED_PAID cases where fine still has outstanding amount
SELECT c.case_id, c.status, f.fine_id, f.outstanding_amount
FROM loss_damage_cases c
JOIN fines f ON c.fine_id = f.fine_id
WHERE c.status = 'RESOLVED_PAID'
  AND f.outstanding_amount > 0.00;

-- 5. Check for RESOLVED_REPLACED cases without a replacement copy of the same book
SELECT c.case_id, c.book_id, c.replacement_copy_id, bc.book_id AS copy_book_id
FROM loss_damage_cases c
LEFT JOIN book_copies bc ON c.replacement_copy_id = bc.copy_id
WHERE c.status = 'RESOLVED_REPLACED'
  AND (c.replacement_copy_id IS NULL OR bc.book_id IS NULL OR bc.book_id != c.book_id);

-- 6. Check for COMPENSATION fines without a corresponding loss/damage case
SELECT f.fine_id, f.borrow_transaction_id, f.original_amount
FROM fines f
LEFT JOIN loss_damage_cases c ON f.fine_id = c.fine_id
WHERE f.fine_type = 'COMPENSATION'
  AND c.case_id IS NULL;

-- 7. Check for case final_amount mismatch with compensation fine original_amount
SELECT c.case_id, c.final_amount, f.fine_id, f.original_amount
FROM loss_damage_cases c
JOIN fines f ON c.fine_id = f.fine_id
WHERE c.final_amount != f.original_amount;

-- 8. Check for fine amount mathematical balance violation (original != paid + waived + outstanding)
SELECT fine_id, original_amount, paid_amount, waived_amount, outstanding_amount
FROM fines
WHERE original_amount != (paid_amount + waived_amount + outstanding_amount);

-- 9. Check for replacement copies with duplicate copy codes
SELECT copy_code, COUNT(*) AS duplicate_count
FROM book_copies
GROUP BY copy_code
HAVING COUNT(*) > 1;

-- 10. Check for case status changes without an event record in loss_damage_case_events
SELECT c.case_id, c.status
FROM loss_damage_cases c
LEFT JOIN loss_damage_case_events e ON c.case_id = e.case_id
WHERE e.event_id IS NULL;
