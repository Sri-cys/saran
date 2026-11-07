-- =============================================================================
-- PSG iTech Library - Feature 6: Fine Calculation and UPI Payment Schema
-- Database: psgitech_library (MySQL 8.0+ / 26.10+)
-- Purpose: Persistent fine records, UPI payments, waivers, and audit log.
-- =============================================================================

USE psgitech_library;

-- -----------------------------------------------------------------------------
-- Table 1: fines
-- Stores finalised overdue fines created atomically upon book return.
-- Invariant: original_amount = paid_amount + waived_amount + outstanding_amount
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fines (
    fine_id INT AUTO_INCREMENT PRIMARY KEY,
    borrow_transaction_id INT NOT NULL,
    student_id INT NOT NULL,
    book_id INT NOT NULL,
    copy_id INT NOT NULL,
    overdue_days INT NOT NULL,
    chargeable_days INT NOT NULL,
    fine_per_day DECIMAL(10,2) NOT NULL,
    grace_period_days INT NOT NULL DEFAULT 0,
    max_fine_applied DECIMAL(10,2) NULL,
    original_amount DECIMAL(10,2) NOT NULL,
    paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    waived_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    outstanding_amount DECIMAL(10,2) NOT NULL,
    status ENUM('UNPAID', 'PARTIALLY_PAID', 'PAID', 'WAIVED') NOT NULL DEFAULT 'UNPAID',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uq_fine_borrow_tx (borrow_transaction_id),
    INDEX idx_fines_student_status (student_id, status),
    INDEX idx_fines_status (status),
    INDEX idx_fines_book (book_id),
    INDEX idx_fines_created_at (created_at),

    CONSTRAINT chk_fine_amounts CHECK (
        original_amount = paid_amount + waived_amount + outstanding_amount
        AND paid_amount >= 0.00
        AND waived_amount >= 0.00
        AND outstanding_amount >= 0.00
    ),
    CONSTRAINT fk_fines_borrow_tx FOREIGN KEY (borrow_transaction_id)
        REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    CONSTRAINT fk_fines_student FOREIGN KEY (student_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_fines_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_fines_copy FOREIGN KEY (copy_id)
        REFERENCES book_copies (copy_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Table 2: payments
-- Stores payment attempts, manual UPI transaction references, and verification status.
-- Generated column active_fine_id ensures at most one in-flight payment attempt per fine.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    fine_id INT NOT NULL,
    student_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    method ENUM('UPI_MANUAL') NOT NULL DEFAULT 'UPI_MANUAL',
    status ENUM('INITIATED', 'PENDING_VERIFICATION', 'SUCCESS', 'REJECTED', 'CANCELLED', 'REFUNDED') NOT NULL DEFAULT 'INITIATED',
    payment_code VARCHAR(64) NOT NULL,
    reference_id VARCHAR(64) NULL,
    idempotency_key VARCHAR(128) NULL,
    receipt_number VARCHAR(64) NULL,
    active_fine_id INT GENERATED ALWAYS AS (
        CASE WHEN status IN ('INITIATED', 'PENDING_VERIFICATION') THEN fine_id ELSE NULL END
    ) STORED,
    submitted_at TIMESTAMP NULL DEFAULT NULL,
    verified_by INT NULL,
    verified_at TIMESTAMP NULL DEFAULT NULL,
    rejection_reason TEXT NULL,
    student_note TEXT NULL,
    disputed TINYINT(1) NOT NULL DEFAULT 0,
    refunded_by INT NULL,
    refunded_at TIMESTAMP NULL DEFAULT NULL,
    refund_reason TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uq_payment_code (payment_code),
    UNIQUE KEY uq_active_fine_payment (active_fine_id),
    UNIQUE KEY uq_payment_reference (method, reference_id),
    UNIQUE KEY uq_student_idempotency (student_id, idempotency_key),
    UNIQUE KEY uq_receipt_number (receipt_number),
    INDEX idx_payments_fine (fine_id),
    INDEX idx_payments_student_status (student_id, status),
    INDEX idx_payments_status_created (status, created_at),

    CONSTRAINT chk_payment_amount CHECK (amount > 0.00),
    CONSTRAINT fk_payments_fine FOREIGN KEY (fine_id)
        REFERENCES fines (fine_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_student FOREIGN KEY (student_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_verified_by FOREIGN KEY (verified_by)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_refunded_by FOREIGN KEY (refunded_by)
        REFERENCES users (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Table 3: fine_adjustments
-- Stores administrative waivers approved by Admins with mandatory reasons.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fine_adjustments (
    adjustment_id INT AUTO_INCREMENT PRIMARY KEY,
    fine_id INT NOT NULL,
    type ENUM('WAIVER') NOT NULL DEFAULT 'WAIVER',
    amount DECIMAL(10,2) NOT NULL,
    reason TEXT NOT NULL,
    approved_by INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_adjustments_fine (fine_id),
    INDEX idx_adjustments_approved_by (approved_by),

    CONSTRAINT chk_adjustment_amount CHECK (amount > 0.00),
    CONSTRAINT fk_adjustments_fine FOREIGN KEY (fine_id)
        REFERENCES fines (fine_id) ON DELETE RESTRICT,
    CONSTRAINT fk_adjustments_approved_by FOREIGN KEY (approved_by)
        REFERENCES users (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Table 4: payment_audit_log
-- Immutable append-only audit trail for all monetary state changes.
-- Application code executes INSERT only; never UPDATE or DELETE.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment_audit_log (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    fine_id INT NOT NULL,
    payment_id INT NULL,
    event_type ENUM(
        'FINE_CREATED',
        'PAYMENT_INITIATED',
        'REFERENCE_SUBMITTED',
        'PAYMENT_VERIFIED',
        'PAYMENT_REJECTED',
        'PAYMENT_CANCELLED',
        'WAIVER_APPLIED',
        'REFUND_RECORDED',
        'DISPUTE_RAISED',
        'DISPUTE_RESOLVED'
    ) NOT NULL,
    old_status VARCHAR(32) NULL,
    new_status VARCHAR(32) NOT NULL,
    amount DECIMAL(10,2) NULL,
    actor_user_id INT NULL,
    actor_role VARCHAR(32) NOT NULL,
    details VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_audit_fine (fine_id),
    INDEX idx_audit_payment (payment_id),
    INDEX idx_audit_event (event_type),
    INDEX idx_audit_created_at (created_at),

    CONSTRAINT fk_audit_fine FOREIGN KEY (fine_id)
        REFERENCES fines (fine_id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_payment FOREIGN KEY (payment_id)
        REFERENCES payments (payment_id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

