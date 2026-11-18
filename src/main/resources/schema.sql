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
    email_verified BOOLEAN DEFAULT TRUE,
    email_verified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
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
    replacement_price DECIMAL(10,2) NULL,
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
    renewal_count INT DEFAULT 0,
    last_renewed_at TIMESTAMP NULL,
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
    expired_at TIMESTAMP NULL DEFAULT NULL,
    successor_allocation_id INT NULL DEFAULT NULL,
    reminder_sent_at TIMESTAMP NULL DEFAULT NULL,
    reminder_status VARCHAR(30) NULL DEFAULT NULL,
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
    fine_type VARCHAR(20) NOT NULL DEFAULT 'OVERDUE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (borrow_transaction_id, fine_type),
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

CREATE TABLE IF NOT EXISTS payment_fines (
    payment_id INT NOT NULL,
    fine_id INT NOT NULL,
    amount_applied DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id, fine_id),
    FOREIGN KEY (payment_id) REFERENCES payments (payment_id) ON DELETE CASCADE,
    FOREIGN KEY (fine_id) REFERENCES fines (fine_id) ON DELETE RESTRICT
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

CREATE TABLE IF NOT EXISTS loan_renewals (
    renewal_id INT AUTO_INCREMENT PRIMARY KEY,
    transaction_id INT NOT NULL,
    student_id INT NOT NULL,
    previous_due_date DATE NOT NULL,
    new_due_date DATE NOT NULL,
    renewal_number INT NOT NULL,
    renewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    renewed_by INT NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (renewed_by) REFERENCES users (user_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS otp_codes (
    otp_id INT AUTO_INCREMENT PRIMARY KEY,
    purpose VARCHAR(30) NOT NULL,
    subject_id INT NOT NULL,
    destination_email VARCHAR(100) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL DEFAULT NULL,
    active_subject_lock VARCHAR(64) AS (CASE WHEN status = 'ACTIVE' THEN CONCAT(purpose, '-', CAST(subject_id AS VARCHAR)) ELSE NULL END),
    UNIQUE (active_subject_lock)
);

-- ==============================================================================
-- Feature 11: Notification and Alert Management System
-- ==============================================================================

CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id INT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    category VARCHAR(30) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    entity_type VARCHAR(30) NULL,
    entity_id INT NULL,
    action_url VARCHAR(200) NULL,
    deduplication_key VARCHAR(120) NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at TIMESTAMP NULL,
    UNIQUE KEY uq_notification_dedupe (recipient_user_id, deduplication_key),
    KEY idx_notif_user_id (recipient_user_id, notification_id),
    KEY idx_notif_user_read (recipient_user_id, is_read, notification_id),
    KEY idx_notif_user_cat (recipient_user_id, category, notification_id)
);

CREATE TABLE IF NOT EXISTS notification_preferences (
    preference_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    category VARCHAR(30) NOT NULL,
    in_app_enabled TINYINT(1) NOT NULL DEFAULT 1,
    email_enabled TINYINT(1) NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_category (user_id, category),
    KEY idx_pref_user (user_id)
);

CREATE TABLE IF NOT EXISTS notification_outbox (
    outbox_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL UNIQUE,
    recipient_user_id INT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    delivery_status ENUM('PENDING','PROCESSING','SENT','FAILED','SKIPPED') NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    claimed_at TIMESTAMP NULL,
    claim_token VARCHAR(40) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    last_error_code VARCHAR(40) NULL,
    CONSTRAINT fk_outbox_notification FOREIGN KEY (notification_id) 
        REFERENCES notifications(notification_id) ON DELETE CASCADE,
    KEY idx_outbox_poll (delivery_status, next_attempt_at, outbox_id),
    KEY idx_outbox_recipient (recipient_user_id)
);

CREATE TABLE IF NOT EXISTS notification_interests (
    interest_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    interest_type VARCHAR(30) NOT NULL,
    value VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_interest (user_id, interest_type, value),
    KEY idx_interest_type_val (interest_type, value)
);

-- ==============================================================================
-- Feature 12: Exam-Season Dynamic Borrowing Policy
-- ==============================================================================
CREATE TABLE IF NOT EXISTS loan_policy_settings (
    id INT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    enter_threshold INT NOT NULL DEFAULT 4,
    exit_threshold INT NOT NULL DEFAULT 1,
    high_demand_days INT NOT NULL DEFAULT 5,
    version INT NOT NULL DEFAULT 1,
    updated_by INT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_enter_threshold CHECK (enter_threshold >= 2),
    CONSTRAINT chk_exit_threshold CHECK (exit_threshold >= 0),
    CONSTRAINT chk_hysteresis CHECK (exit_threshold < enter_threshold),
    CONSTRAINT chk_high_demand_days CHECK (high_demand_days >= 2 AND high_demand_days < 14)
);

CREATE TABLE IF NOT EXISTS loan_policy_changes (
    change_id INT AUTO_INCREMENT PRIMARY KEY,
    changed_by INT NULL,
    changed_by_name VARCHAR(100) NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_enabled BOOLEAN NOT NULL,
    new_enabled BOOLEAN NOT NULL,
    old_enter_threshold INT NOT NULL,
    new_enter_threshold INT NOT NULL,
    old_exit_threshold INT NOT NULL,
    new_exit_threshold INT NOT NULL,
    old_high_demand_days INT NOT NULL,
    new_high_demand_days INT NOT NULL,
    version INT NOT NULL,
    INDEX idx_policy_changes_time (changed_at DESC)
);

CREATE TABLE IF NOT EXISTS book_loan_policy_state (
    book_id INT PRIMARY KEY,
    policy_code VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    entered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_demand INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_policy_state_book FOREIGN KEY (book_id) 
        REFERENCES books (book_id) ON DELETE CASCADE,
    INDEX idx_policy_state_code (policy_code, last_evaluated_at)
);

CREATE TABLE IF NOT EXISTS loan_policy_windows (
    window_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    enter_threshold_override INT NOT NULL DEFAULT 2,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_window_dates CHECK (start_date <= end_date),
    CONSTRAINT chk_window_override CHECK (enter_threshold_override >= 2),
    INDEX idx_policy_windows_dates (active, start_date, end_date)
);

CREATE TABLE IF NOT EXISTS loss_damage_cases (
    case_id INT AUTO_INCREMENT PRIMARY KEY,
    case_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REPORTED',
    borrow_transaction_id INT NOT NULL,
    student_id INT NOT NULL,
    book_id INT NOT NULL,
    copy_id INT NOT NULL,
    reported_by INT NOT NULL,
    reported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    report_note VARCHAR(300) NULL,
    severity VARCHAR(20) NULL,
    copy_disposition VARCHAR(30) NULL,
    assessed_by INT NULL,
    assessed_at TIMESTAMP NULL,
    price_used DECIMAL(10,2) NULL,
    age_days INT NULL,
    age_flag BOOLEAN NOT NULL DEFAULT FALSE,
    depreciation_applied DECIMAL(6,4) NULL,
    processing_fee DECIMAL(10,2) NULL,
    severity_fraction DECIMAL(4,2) NULL,
    computed_amount DECIMAL(10,2) NULL,
    final_amount DECIMAL(10,2) NULL,
    override_by INT NULL,
    override_reason VARCHAR(200) NULL,
    resolution_choice VARCHAR(20) NULL,
    fine_id INT NULL,
    replacement_deadline DATE NULL,
    replacement_copy_id INT NULL,
    rejection_reason VARCHAR(200) NULL,
    resolved_at TIMESTAMP NULL,
    resolved_by INT NULL,
    active_loan_key INT AS (CASE WHEN status IN ('REPORTED','ASSESSED','PAYMENT_PENDING','REPLACEMENT_PENDING','REPLACEMENT_SUBMITTED') THEN borrow_transaction_id ELSE NULL END),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (active_loan_key),
    FOREIGN KEY (borrow_transaction_id) REFERENCES borrow_transactions (transaction_id) ON DELETE RESTRICT,
    FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (book_id) REFERENCES books (book_id) ON DELETE RESTRICT,
    FOREIGN KEY (copy_id) REFERENCES book_copies (copy_id) ON DELETE RESTRICT,
    FOREIGN KEY (reported_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (assessed_by) REFERENCES users (user_id) ON DELETE RESTRICT,
    FOREIGN KEY (fine_id) REFERENCES fines (fine_id) ON DELETE RESTRICT,
    FOREIGN KEY (replacement_copy_id) REFERENCES book_copies (copy_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS loss_damage_case_events (
    event_id INT AUTO_INCREMENT PRIMARY KEY,
    case_id INT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    old_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    actor_user_id INT NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    details VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (case_id) REFERENCES loss_damage_cases (case_id) ON DELETE RESTRICT,
    FOREIGN KEY (actor_user_id) REFERENCES users (user_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS students (
    student_id INT AUTO_INCREMENT PRIMARY KEY,
    permanent_roll_number VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    account_status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    registration_status ENUM('NOT_REGISTERED', 'REGISTERED') NOT NULL DEFAULT 'NOT_REGISTERED',
    user_id INT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
);







