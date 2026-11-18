-- ============================================================================
-- PSG iTech Library - Migration 17: Librarian Portal Extensions
-- Idempotent DDL for Shelf Locator, Acquisitions, Queries, Purchases, Activity Logs
-- ============================================================================

USE psgitech_library;

-- 1. Table: book_locations (Smart Shelf Locator)
CREATE TABLE IF NOT EXISTS book_locations (
    location_id INT AUTO_INCREMENT PRIMARY KEY,
    book_id INT NOT NULL,
    copy_id INT NULL,
    floor VARCHAR(20) NOT NULL,
    block VARCHAR(50) NOT NULL,
    section VARCHAR(50) NOT NULL,
    rack VARCHAR(50) NOT NULL,
    shelf VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_loc_book FOREIGN KEY (book_id) REFERENCES books(book_id) ON DELETE CASCADE,
    CONSTRAINT fk_loc_copy FOREIGN KEY (copy_id) REFERENCES book_copies(copy_id) ON DELETE SET NULL,
    INDEX idx_loc_book (book_id),
    INDEX idx_loc_copy (copy_id),
    INDEX idx_loc_shelf (floor, block, section, rack, shelf)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table: acquisition_requests (Book Requests from Students & Faculty)
CREATE TABLE IF NOT EXISTS acquisition_requests (
    request_id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    isbn VARCHAR(20) NULL,
    publisher VARCHAR(255) NULL,
    edition VARCHAR(50) NULL,
    department VARCHAR(50) NULL,
    requester_id INT NOT NULL,
    status ENUM('REQUESTED', 'APPROVED', 'ORDERED', 'RECEIVED', 'REJECTED') NOT NULL DEFAULT 'REQUESTED',
    demand_count INT NOT NULL DEFAULT 1,
    remarks TEXT NULL,
    estimated_price DECIMAL(10,2) NULL,
    approved_by INT NULL,
    approved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_acq_requester FOREIGN KEY (requester_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_acq_approver FOREIGN KEY (approved_by) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_acq_status (status),
    INDEX idx_acq_requester (requester_id),
    INDEX idx_acq_dept (department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Table: library_queries (Student Inquiries & Helpdesk)
CREATE TABLE IF NOT EXISTS library_queries (
    query_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    category VARCHAR(50) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    status ENUM('OPEN', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
    response_text TEXT NULL,
    resolved_by INT NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lq_student FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_lq_resolver FOREIGN KEY (resolved_by) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_lq_student (student_id),
    INDEX idx_lq_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Table: book_purchases (Acquisition Invoices & Vendor Purchases)
CREATE TABLE IF NOT EXISTS book_purchases (
    purchase_id INT AUTO_INCREMENT PRIMARY KEY,
    supplier_name VARCHAR(255) NOT NULL,
    invoice_no VARCHAR(100) NOT NULL,
    purchase_date DATE NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    request_id INT NULL,
    created_by INT NOT NULL,
    remarks TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_supplier_invoice UNIQUE (supplier_name, invoice_no),
    CONSTRAINT fk_bp_request FOREIGN KEY (request_id) REFERENCES acquisition_requests(request_id) ON DELETE SET NULL,
    CONSTRAINT fk_bp_creator FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE RESTRICT,
    INDEX idx_bp_supplier (supplier_name),
    INDEX idx_bp_date (purchase_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Table: library_activity_logs (Audit Trail for Circulation & Staff Actions)
CREATE TABLE IF NOT EXISTS library_activity_logs (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor_id INT NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    target_entity VARCHAR(50) NOT NULL,
    target_id VARCHAR(50) NOT NULL,
    action_details TEXT NULL,
    ip_address VARCHAR(45) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_actor (actor_id),
    INDEX idx_log_event (event_type),
    INDEX idx_log_target (target_entity, target_id),
    INDEX idx_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
