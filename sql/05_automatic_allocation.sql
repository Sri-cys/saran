-- =============================================================================
-- PSG iTech Library - Feature 5: Automatic Book Allocation Schema Migration
-- Database: psgitech_library (MySQL 8.0+ / 26.10+ / InnoDB)
-- =============================================================================

USE psgitech_library;

-- -----------------------------------------------------------------------------
-- 1. Verify / Note on book_copies.status
-- In psgitech_library (database/02_book_management.sql), book_copies.status was
-- originally created with:
-- ENUM('AVAILABLE','BORROWED','ALLOCATED','TRANSFER_PENDING','ARCHIVED')
-- Status 'ALLOCATED' is already present in MySQL and does not require ALTER TABLE.
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- 2. Create allocations Table
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS allocations (
    allocation_id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL,
    student_id INT NOT NULL,
    book_id INT NOT NULL,
    copy_id INT NOT NULL,
    return_transaction_id INT NULL,
    borrow_transaction_id INT NULL,
    status ENUM('ALLOCATED','COLLECTED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ALLOCATED',
    allocated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP NOT NULL,
    collected_at TIMESTAMP NULL DEFAULT NULL,
    cancelled_at TIMESTAMP NULL DEFAULT NULL,
    cancelled_by INT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Stored generated columns guaranteeing at most one active ALLOCATED record per physical copy and per reservation
    active_copy_id INT GENERATED ALWAYS AS (IF(status = 'ALLOCATED', copy_id, NULL)) STORED,
    active_reservation_id INT GENERATED ALWAYS AS (IF(status = 'ALLOCATED', reservation_id, NULL)) STORED,

    UNIQUE KEY uq_active_copy_allocation (active_copy_id),
    UNIQUE KEY uq_active_reservation_allocation (active_reservation_id),

    INDEX idx_alloc_status_expires (status, expires_at),
    INDEX idx_alloc_student_status (student_id, status),
    INDEX idx_alloc_book_status (book_id, status),
    INDEX idx_alloc_copy (copy_id),

    CONSTRAINT fk_alloc_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservations (reservation_id) ON DELETE RESTRICT,
    CONSTRAINT fk_alloc_student FOREIGN KEY (student_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_alloc_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_alloc_copy FOREIGN KEY (copy_id)
        REFERENCES book_copies (copy_id) ON DELETE RESTRICT,
    CONSTRAINT fk_alloc_return_tx FOREIGN KEY (return_transaction_id)
        REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    CONSTRAINT fk_alloc_borrow_tx FOREIGN KEY (borrow_transaction_id)
        REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    CONSTRAINT fk_alloc_cancelled_by FOREIGN KEY (cancelled_by)
        REFERENCES users (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

