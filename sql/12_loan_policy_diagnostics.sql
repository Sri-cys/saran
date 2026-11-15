-- ==============================================================================
-- PSG iTech Library - Feature 12: Exam-Season Dynamic Borrowing Policy
-- Diagnostics Script: 12_loan_policy_diagnostics.sql
-- SELECT-only assertions. Every query should return 0 rows for a healthy system.
-- ==============================================================================

-- 1. High-demand loans whose loan_days is not strictly shorter than standard (14 days)
SELECT transaction_id, book_id, policy_code, loan_days, issue_date, due_date
FROM borrow_transactions
WHERE policy_code = 'HIGH_DEMAND' AND (loan_days >= 14 OR loan_days < 2);

-- 2. Loans never renewed whose due_date differs from issue_date + loan_days
SELECT transaction_id, student_id, book_id, issue_date, due_date, loan_days,
       DATE_ADD(issue_date, INTERVAL loan_days DAY) AS expected_due_date
FROM borrow_transactions
WHERE policy_code IS NOT NULL 
  AND loan_days IS NOT NULL 
  AND renewal_count = 0
  AND due_date != DATE_ADD(issue_date, INTERVAL loan_days DAY);

-- 3. Policy state rows for non-existent books
SELECT s.book_id, s.policy_code, s.last_demand
FROM book_loan_policy_state s
LEFT JOIN books b ON s.book_id = b.book_id
WHERE b.book_id IS NULL;

-- 4. State rows with policy_code outside allowed set ('STANDARD', 'HIGH_DEMAND')
SELECT book_id, policy_code, last_demand
FROM book_loan_policy_state
WHERE policy_code NOT IN ('STANDARD', 'HIGH_DEMAND');

-- 5. Settings table invariant violations (must be exactly 1 row, exit < enter, sane bounds)
SELECT id, enabled, enter_threshold, exit_threshold, high_demand_days, version
FROM loan_policy_settings
WHERE id != 1 
   OR exit_threshold >= enter_threshold 
   OR high_demand_days < 2 
   OR high_demand_days >= 14 
   OR enter_threshold < 2 
   OR exit_threshold < 0;

-- 6. History versions not strictly increasing or gaps
SELECT c1.change_id, c1.version AS current_version, c2.version AS prev_version
FROM loan_policy_changes c1
JOIN loan_policy_changes c2 ON c1.change_id = c2.change_id + 1
WHERE c1.version <= c2.version;

-- 7. More than one settings row
SELECT COUNT(*) AS settings_row_count
FROM loan_policy_settings
HAVING COUNT(*) != 1;
