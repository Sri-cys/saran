-- ============================================================================
-- PSG iTech Library - Feature 8: Student-to-Student Book Interchange
-- Migration Script: 08_book_interchange.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS book_interchanges (
    interchange_id INT AUTO_INCREMENT PRIMARY KEY,
    transaction_id INT NOT NULL,
    book_id INT NOT NULL,
    copy_id INT NOT NULL,
    from_student_id INT NOT NULL,
    to_student_id INT NOT NULL,
    initiated_by INT NOT NULL,
    accepted_by INT NULL,
    rejected_by INT NULL,
    cancelled_by INT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    original_issue_date DATE NOT NULL,
    original_due_date DATE NOT NULL,
    request_note VARCHAR(255) NULL,
    rejection_reason VARCHAR(255) NULL,
    cancellation_reason VARCHAR(255) NULL,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP NULL DEFAULT NULL,
    completed_at TIMESTAMP NULL DEFAULT NULL,
    cancelled_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Explicit STORED generated column to enforce at most one active REQUESTED interchange per transaction
    active_transaction_id INT GENERATED ALWAYS AS (CASE WHEN status = 'REQUESTED' THEN transaction_id ELSE NULL END) STORED,

    -- Database constraints
    CONSTRAINT uk_active_transaction_interchange UNIQUE (active_transaction_id),
    CONSTRAINT chk_interchange_different_students CHECK (from_student_id <> to_student_id),

    -- Foreign Keys with RESTRICT
    CONSTRAINT fk_interchange_tx FOREIGN KEY (transaction_id) REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    CONSTRAINT fk_interchange_book FOREIGN KEY (book_id) REFERENCES books (book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_interchange_copy FOREIGN KEY (copy_id) REFERENCES book_copies (copy_id) ON DELETE RESTRICT,
    CONSTRAINT fk_interchange_from FOREIGN KEY (from_student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_interchange_to FOREIGN KEY (to_student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_interchange_init FOREIGN KEY (initiated_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_interchange_acc FOREIGN KEY (accepted_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_interchange_rej FOREIGN KEY (rejected_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_interchange_canc FOREIGN KEY (cancelled_by) REFERENCES users (user_id) ON DELETE RESTRICT,

    -- Optimized query indexes
    INDEX idx_interchange_from_status (from_student_id, status),
    INDEX idx_interchange_to_status (to_student_id, status),
    INDEX idx_interchange_book_status (book_id, status),
    INDEX idx_interchange_tx (transaction_id),
    INDEX idx_interchange_status_req (status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

