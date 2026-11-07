-- =============================================================================
-- PSG iTech Library - Feature 5: Allocation Diagnostic Queries (Read-Only)
-- Database: psgitech_library
-- Purpose: Detect data anomalies, orphaned states, and queue-jumping edge cases.
-- =============================================================================

USE psgitech_library;

-- -----------------------------------------------------------------------------
-- Diagnostic 1: Books with WAITING reservations AND AVAILABLE copies
-- Indicates a potential queue-jumping anomaly (e.g. newly added copies left as AVAILABLE)
-- -----------------------------------------------------------------------------
SELECT 
    b.book_id,
    b.title,
    b.isbn,
    COUNT(DISTINCT r.reservation_id) AS waiting_reservations_count,
    COUNT(DISTINCT c.copy_id) AS available_copies_count
FROM books b
JOIN reservations r ON b.book_id = r.book_id AND r.status = 'WAITING'
JOIN book_copies c ON b.book_id = c.book_id AND c.status = 'AVAILABLE'
GROUP BY b.book_id, b.title, b.isbn;

-- -----------------------------------------------------------------------------
-- Diagnostic 2: Physical copies with status 'ALLOCATED' but NO active allocation record
-- Indicates an orphaned ALLOCATED copy
-- -----------------------------------------------------------------------------
SELECT 
    c.copy_id,
    c.copy_code,
    c.book_id,
    c.status AS copy_status
FROM book_copies c
LEFT JOIN allocations a ON c.copy_id = a.copy_id AND a.status = 'ALLOCATED'
WHERE c.status = 'ALLOCATED' AND a.allocation_id IS NULL;

-- -----------------------------------------------------------------------------
-- Diagnostic 3: Active allocation records whose physical copy is NOT in status 'ALLOCATED'
-- Indicates an allocation record pointing to an unallocated or mismatched copy
-- -----------------------------------------------------------------------------
SELECT 
    a.allocation_id,
    a.reservation_id,
    a.student_id,
    a.copy_id,
    a.status AS allocation_status,
    c.copy_code,
    c.status AS copy_status
FROM allocations a
JOIN book_copies c ON a.copy_id = c.copy_id
WHERE a.status = 'ALLOCATED' AND c.status != 'ALLOCATED';

-- -----------------------------------------------------------------------------
-- Diagnostic 4: Active allocation records whose reservation is NOT in status 'ALLOCATED'
-- Indicates an allocation whose associated reservation is not marked ALLOCATED
-- -----------------------------------------------------------------------------
SELECT 
    a.allocation_id,
    a.reservation_id,
    a.student_id,
    a.status AS allocation_status,
    r.status AS reservation_status
FROM allocations a
JOIN reservations r ON a.reservation_id = r.reservation_id
WHERE a.status = 'ALLOCATED' AND r.status != 'ALLOCATED';

-- -----------------------------------------------------------------------------
-- Diagnostic 5: Expired active allocations (Feature 9 preparation)
-- -----------------------------------------------------------------------------
SELECT 
    a.allocation_id,
    a.reservation_id,
    a.student_id,
    a.copy_id,
    a.expires_at,
    TIMESTAMPDIFF(HOUR, a.expires_at, NOW()) AS hours_overdue
FROM allocations a
WHERE a.status = 'ALLOCATED' AND a.expires_at < NOW();

