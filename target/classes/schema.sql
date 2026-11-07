CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL,
    register_number VARCHAR(50) UNIQUE,
    employee_id VARCHAR(50) UNIQUE,
    department VARCHAR(50),
    `year` INT,
    active BOOLEAN DEFAULT TRUE,
    must_change_password BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS remember_tokens (
    token_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS books (
    book_id INT AUTO_INCREMENT PRIMARY KEY,
    isbn VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    publisher VARCHAR(255) NOT NULL,
    edition VARCHAR(50),
    publication_year INT NOT NULL,
    category VARCHAR(100) NOT NULL,
    department VARCHAR(50) NOT NULL,
    description TEXT,
    cover_image_url VARCHAR(500),
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS book_copies (
    copy_id INT AUTO_INCREMENT PRIMARY KEY,
    copy_code VARCHAR(50) NOT NULL UNIQUE,
    book_id INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (book_id) REFERENCES books (book_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS borrow_transactions (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    book_id INT NOT NULL,
    copy_id INT NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    issued_by INT NOT NULL,
    returned_by INT NULL,
    active_copy_lock INT AS (CASE WHEN status = 'ACTIVE' THEN copy_id ELSE NULL END),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (book_id) REFERENCES books (book_id) ON DELETE RESTRICT,
    FOREIGN KEY (copy_id) REFERENCES book_copies (copy_id) ON DELETE RESTRICT,
    FOREIGN KEY (issued_by) REFERENCES users (user_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS reservations (
    reservation_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    book_id INT NOT NULL,
    reservation_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    cancelled_at TIMESTAMP NULL DEFAULT NULL,
    cancelled_by INT NULL,
    active_reservation_lock VARCHAR(64) AS (CASE WHEN status IN ('WAITING','ALLOCATED') THEN CONCAT(CAST(student_id AS VARCHAR), '-', CAST(book_id AS VARCHAR)) ELSE NULL END),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (book_id) REFERENCES books (book_id) ON DELETE RESTRICT,
    FOREIGN KEY (cancelled_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    UNIQUE (active_reservation_lock)
);

CREATE TABLE IF NOT EXISTS allocations (
    allocation_id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL,
    student_id INT NOT NULL,
    book_id INT NOT NULL,
    copy_id INT NOT NULL,
    return_transaction_id INT NULL,
    borrow_transaction_id INT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ALLOCATED',
    allocated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    collected_at TIMESTAMP NULL DEFAULT NULL,
    cancelled_at TIMESTAMP NULL DEFAULT NULL,
    cancelled_by INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active_copy_id INT AS (CASE WHEN status = 'ALLOCATED' THEN copy_id ELSE NULL END),
    active_reservation_id INT AS (CASE WHEN status = 'ALLOCATED' THEN reservation_id ELSE NULL END),
    FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id) ON DELETE RESTRICT,
    FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (book_id) REFERENCES books (book_id) ON DELETE RESTRICT,
    FOREIGN KEY (copy_id) REFERENCES book_copies (copy_id) ON DELETE RESTRICT,
    FOREIGN KEY (cancelled_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    UNIQUE (active_copy_id),
    UNIQUE (active_reservation_id)
);

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
    status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (borrow_transaction_id),
    FOREIGN KEY (borrow_transaction_id) REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (book_id) REFERENCES books (book_id) ON DELETE RESTRICT,
    FOREIGN KEY (copy_id) REFERENCES book_copies (copy_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    fine_id INT NOT NULL,
    student_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    method VARCHAR(30) NOT NULL DEFAULT 'UPI_MANUAL',
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    payment_code VARCHAR(64) NOT NULL,
    reference_id VARCHAR(64) NULL,
    idempotency_key VARCHAR(128) NULL,
    receipt_number VARCHAR(64) NULL,
    active_fine_id INT AS (CASE WHEN status IN ('INITIATED', 'PENDING_VERIFICATION') THEN fine_id ELSE NULL END),
    submitted_at TIMESTAMP NULL DEFAULT NULL,
    verified_by INT NULL,
    verified_at TIMESTAMP NULL DEFAULT NULL,
    rejection_reason TEXT NULL,
    student_note TEXT NULL,
    disputed BOOLEAN NOT NULL DEFAULT FALSE,
    refunded_by INT NULL,
    refunded_at TIMESTAMP NULL DEFAULT NULL,
    refund_reason TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (payment_code),
    UNIQUE (active_fine_id),
    UNIQUE (method, reference_id),
    UNIQUE (student_id, idempotency_key),
    UNIQUE (receipt_number),
    FOREIGN KEY (fine_id) REFERENCES fines (fine_id) ON DELETE RESTRICT,
    FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (verified_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (refunded_by) REFERENCES users (user_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS fine_adjustments (
    adjustment_id INT AUTO_INCREMENT PRIMARY KEY,
    fine_id INT NOT NULL,
    type VARCHAR(30) NOT NULL DEFAULT 'WAIVER',
    amount DECIMAL(10,2) NOT NULL,
    reason TEXT NOT NULL,
    approved_by INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (fine_id) REFERENCES fines (fine_id) ON DELETE RESTRICT,
    FOREIGN KEY (approved_by) REFERENCES users (user_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS payment_audit_log (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    fine_id INT NOT NULL,
    payment_id INT NULL,
    event_type VARCHAR(64) NOT NULL,
    old_status VARCHAR(32) NULL,
    new_status VARCHAR(32) NOT NULL,
    amount DECIMAL(10,2) NULL,
    actor_user_id INT NULL,
    actor_role VARCHAR(32) NOT NULL,
    details VARCHAR(500) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (fine_id) REFERENCES fines (fine_id) ON DELETE RESTRICT,
    FOREIGN KEY (payment_id) REFERENCES payments (payment_id) ON DELETE RESTRICT,
    FOREIGN KEY (actor_user_id) REFERENCES users (user_id) ON DELETE RESTRICT
);

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
    active_transaction_id INT AS (CASE WHEN status = 'REQUESTED' THEN transaction_id ELSE NULL END),
    UNIQUE (active_transaction_id),
    CONSTRAINT chk_interchange_different_students CHECK (from_student_id <> to_student_id),
    FOREIGN KEY (transaction_id) REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    FOREIGN KEY (book_id) REFERENCES books (book_id) ON DELETE RESTRICT,
    FOREIGN KEY (copy_id) REFERENCES book_copies (copy_id) ON DELETE RESTRICT,
    FOREIGN KEY (from_student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (to_student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (initiated_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (accepted_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (rejected_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (cancelled_by) REFERENCES users (user_id) ON DELETE RESTRICT
);



