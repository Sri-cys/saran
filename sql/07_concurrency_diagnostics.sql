-- =============================================================================
-- PSG iTech Library - Feature 7: Concurrency & Lock Diagnostics
-- Database: psgitech_library / psgitech_library_test (MySQL 8.0+ / InnoDB)
-- =============================================================================

-- 1. Server Connection & Capacity Diagnostics
SELECT 
    @@max_connections AS server_max_connections,
    @@innodb_lock_wait_timeout AS innodb_lock_wait_timeout_seconds,
    @@transaction_isolation AS default_transaction_isolation;

SHOW STATUS LIKE 'Threads_connected';
SHOW STATUS LIKE 'Threads_running';
SHOW STATUS LIKE 'Max_used_connections';
SHOW STATUS LIKE 'Innodb_row_lock_waits';
SHOW STATUS LIKE 'Innodb_row_lock_time_avg';
SHOW STATUS LIKE 'Innodb_deadlocks';

-- 2. Inspect Active InnoDB Transactions
SELECT 
    trx_id,
    trx_state,
    trx_started,
    trx_wait_started,
    trx_mysql_thread_id,
    trx_query
FROM information_schema.innodb_trx;

-- 3. Data Integrity: Check for any orphaned WAITING reservations with AVAILABLE copies
SELECT 
    r.reservation_id,
    r.book_id,
    r.student_id,
    r.status AS reservation_status,
    bc.copy_id AS available_copy_id,
    bc.copy_code,
    bc.status AS copy_status
FROM reservations r
JOIN book_copies bc ON r.book_id = bc.book_id
WHERE r.status = 'WAITING' 
  AND bc.status = 'AVAILABLE';

-- 4. Check for double active allocations on physical copies
SELECT 
    copy_id,
    COUNT(*) AS active_allocations
FROM allocations
WHERE status = 'ALLOCATED'
GROUP BY copy_id
HAVING COUNT(*) > 1;

-- 5. Check for double active loans on physical copies
SELECT 
    copy_id,
    COUNT(*) AS active_loans
FROM borrow_transactions
WHERE status = 'ACTIVE'
GROUP BY copy_id
HAVING COUNT(*) > 1;

-- 6. Check for duplicate active reservations for the same student on the same book
SELECT 
    student_id,
    book_id,
    COUNT(*) AS active_reservations
FROM reservations
WHERE status IN ('WAITING', 'ALLOCATED')
GROUP BY student_id, book_id
HAVING COUNT(*) > 1;
