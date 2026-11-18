-- ==============================================================================
-- PSG iTech Library Management System
-- Diagnostic Utility: Physical Copy Inventory & Availability Drift Checker
-- Purpose: Compares physical copy shelf status against active borrow transactions
--          to detect and alert on copy counter drift caused by retired, lost,
--          damaged, or desynchronized physical book copies.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- QUERY 1: System-Wide Inventory & Availability Reconciliation Report
-- ------------------------------------------------------------------------------
SELECT 
    b.book_id,
    b.isbn,
    b.title,
    b.department,
    COUNT(c.copy_id) AS total_physical_copies,
    COUNT(CASE WHEN c.status != 'ARCHIVED' THEN 1 END) AS active_inventory_copies,
    COUNT(CASE WHEN c.status = 'ARCHIVED' THEN 1 END) AS retired_archived_copies,
    COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) AS shelf_available_copies,
    COUNT(CASE WHEN c.status = 'BORROWED' THEN 1 END) AS copies_marked_borrowed,
    COUNT(CASE WHEN c.status = 'ALLOCATED' THEN 1 END) AS copies_marked_allocated,
    COALESCE(active_loans.active_loan_count, 0) AS active_loans_in_transactions,
    -- Expected available copies = Active (unarchived) physical copies minus active checkout transactions
    (COUNT(CASE WHEN c.status != 'ARCHIVED' THEN 1 END) - COALESCE(active_loans.active_loan_count, 0)) AS computed_expected_available,
    -- Availability Drift = Actual physical AVAILABLE copies minus Computed Expected Available copies
    -- A non-zero value indicates copy counter drift or desynchronized circulation states.
    (COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) - 
     (COUNT(CASE WHEN c.status != 'ARCHIVED' THEN 1 END) - COALESCE(active_loans.active_loan_count, 0))) AS availability_drift
FROM books b
LEFT JOIN book_copies c ON b.book_id = c.book_id
LEFT JOIN (
    SELECT book_id, COUNT(*) AS active_loan_count
    FROM borrow_transactions
    WHERE status = 'ACTIVE'
    GROUP BY book_id
) active_loans ON b.book_id = active_loans.book_id
GROUP BY b.book_id, b.isbn, b.title, b.department, active_loans.active_loan_count
ORDER BY availability_drift DESC, b.book_id ASC;


-- ------------------------------------------------------------------------------
-- QUERY 2: Anomaly Alert Query (Filters strictly for titles experiencing drift)
-- Run this query in automated health checks or monitoring scripts.
-- ------------------------------------------------------------------------------
SELECT 
    b.book_id,
    b.isbn,
    b.title,
    COUNT(CASE WHEN c.status != 'ARCHIVED' THEN 1 END) AS active_inventory,
    COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) AS shelf_available,
    COALESCE(active_loans.active_loan_count, 0) AS active_loans,
    (COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) - 
     (COUNT(CASE WHEN c.status != 'ARCHIVED' THEN 1 END) - COALESCE(active_loans.active_loan_count, 0))) AS availability_drift,
    CASE 
        WHEN (COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) - 
              (COUNT(CASE WHEN c.status != 'ARCHIVED' THEN 1 END) - COALESCE(active_loans.active_loan_count, 0))) > 0 
            THEN 'PHANTOM_AVAILABILITY_OVERCOUNT'
        WHEN (COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) - 
              (COUNT(CASE WHEN c.status != 'ARCHIVED' THEN 1 END) - COALESCE(active_loans.active_loan_count, 0))) < 0 
            THEN 'UNDERREPORTED_AVAILABILITY_DEFICIT'
        ELSE 'SYNCHRONIZED'
    END AS anomaly_classification
FROM books b
LEFT JOIN book_copies c ON b.book_id = c.book_id
LEFT JOIN (
    SELECT book_id, COUNT(*) AS active_loan_count
    FROM borrow_transactions
    WHERE status = 'ACTIVE'
    GROUP BY book_id
) active_loans ON b.book_id = active_loans.book_id
GROUP BY b.book_id, b.isbn, b.title, active_loans.active_loan_count
HAVING availability_drift != 0;


-- ------------------------------------------------------------------------------
-- QUERY 3: Summary Integrity KPI Metrics
-- ------------------------------------------------------------------------------
SELECT 
    (SELECT COUNT(*) FROM books) AS total_catalog_titles,
    (SELECT COUNT(*) FROM book_copies) AS total_physical_copies,
    (SELECT COUNT(*) FROM book_copies WHERE status = 'ARCHIVED') AS retired_copies,
    (SELECT COUNT(*) FROM borrow_transactions WHERE status = 'ACTIVE') AS total_active_loans,
    (SELECT COUNT(*) FROM (
        SELECT b.book_id
        FROM books b
        LEFT JOIN book_copies c ON b.book_id = c.book_id
        LEFT JOIN (
            SELECT book_id, COUNT(*) AS active_loan_count
            FROM borrow_transactions WHERE status = 'ACTIVE' GROUP BY book_id
        ) al ON b.book_id = al.book_id
        GROUP BY b.book_id, al.active_loan_count
        HAVING (COUNT(CASE WHEN c.status = 'AVAILABLE' THEN 1 END) - 
               (COUNT(CASE WHEN c.status != 'ARCHIVED' THEN 1 END) - COALESCE(al.active_loan_count, 0))) != 0
    ) drift_titles) AS titles_with_drift;
