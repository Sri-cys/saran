-- =============================================================================
-- PSG iTech Library - Enhancements of Features 1-8
-- Migration 09: Diagnostics Script (Read-Only Integrity Verification)
-- Database: psgitech_library / psgitech_library_test (MySQL 8.0+ / InnoDB)
-- All diagnostics must return 0 rows in steady state
-- =============================================================================

-- Diagnostic 1: Duplicate ACTIVE OTP codes per subject
SELECT 
    'DIAGNOSTIC_1_DUPLICATE_ACTIVE_OTP' AS diagnostic_name,
    purpose, subject_id, COUNT(*) AS active_otp_count
FROM otp_codes
WHERE status = 'ACTIVE'
GROUP BY purpose, subject_id
HAVING active_otp_count > 1;

-- Diagnostic 2: Active loans with renewal_count above maximum (1)
SELECT 
    'DIAGNOSTIC_2_EXCESSIVE_RENEWALS' AS diagnostic_name,
    transaction_id, student_id, book_id, renewal_count
FROM borrow_transactions
WHERE renewal_count > 1;

-- Diagnostic 3: Loan renewal audit rows not matching current due date
SELECT 
    'DIAGNOSTIC_3_RENEWAL_AUDIT_MISMATCH' AS diagnostic_name,
    lr.renewal_id, lr.transaction_id, lr.new_due_date, bt.due_date AS current_due_date
FROM loan_renewals lr
JOIN borrow_transactions bt ON lr.transaction_id = bt.transaction_id
WHERE bt.status = 'ACTIVE' AND lr.renewal_number = bt.renewal_count AND lr.new_due_date != bt.due_date;

-- Diagnostic 4: ALLOCATED allocations past deadline (older than 60s scan interval)
SELECT 
    'DIAGNOSTIC_4_OVERDUE_ALLOCATIONS_UNPROCESSED' AS diagnostic_name,
    allocation_id, reservation_id, student_id, copy_id, expires_at, NOW() AS db_now,
    TIMESTAMPDIFF(SECOND, expires_at, NOW()) AS overdue_seconds
FROM allocations
WHERE status = 'ALLOCATED' AND expires_at < DATE_SUB(NOW(), INTERVAL 60 SECOND);

-- Diagnostic 5: EXPIRED allocations whose associated reservation is not EXPIRED
SELECT 
    'DIAGNOSTIC_5_EXPIRED_ALLOC_WITHOUT_EXPIRED_RES' AS diagnostic_name,
    a.allocation_id, a.reservation_id, a.status AS alloc_status, r.status AS res_status
FROM allocations a
JOIN reservations r ON a.reservation_id = r.reservation_id
WHERE a.status = 'EXPIRED' AND r.status != 'EXPIRED';

-- Diagnostic 6: Receipts generated for non-SUCCESS payments
SELECT 
    'DIAGNOSTIC_6_RECEIPT_FOR_NON_SUCCESS_PAYMENT' AS diagnostic_name,
    payment_id, status, receipt_number
FROM payments
WHERE status != 'SUCCESS' AND receipt_number IS NOT NULL AND status != 'REFUNDED';

-- Diagnostic 7: Copy-code duplicates across physical inventory
SELECT 
    'DIAGNOSTIC_7_DUPLICATE_COPY_CODES' AS diagnostic_name,
    copy_code, COUNT(*) AS copy_count
FROM book_copies
GROUP BY copy_code
HAVING copy_count > 1;

-- Diagnostic 8: Unverified students with email_verified_at set or vice-versa
SELECT 
    'DIAGNOSTIC_8_EMAIL_VERIFICATION_INCONSISTENCY' AS diagnostic_name,
    user_id, email, email_verified, email_verified_at
FROM users
WHERE (email_verified = FALSE AND email_verified_at IS NOT NULL)
   OR (email_verified = TRUE AND email_verified_at IS NULL);

