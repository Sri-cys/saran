-- =============================================================================
-- PSG iTech Library - Feature 3: Book Borrowing Schema Migration & Seed Data
-- Database: psgitech_library (MySQL 8.0+ / InnoDB)
-- =============================================================================

USE psgitech_library;

CREATE TABLE IF NOT EXISTS borrow_transactions (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    book_id INT NOT NULL,
    copy_id INT NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE NULL,
    status ENUM('ACTIVE','RETURNED') NOT NULL DEFAULT 'ACTIVE',
    issued_by INT NOT NULL,
    returned_by INT NULL,
    active_copy_lock INT GENERATED ALWAYS AS (IF(status = 'ACTIVE', copy_id, NULL)) STORED,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_active_copy_lock (active_copy_lock),
    INDEX idx_borrow_student (student_id),
    INDEX idx_borrow_book (book_id),
    INDEX idx_borrow_copy (copy_id),
    INDEX idx_borrow_status (status),
    INDEX idx_borrow_due_date (due_date),
    INDEX idx_borrow_student_status (student_id, status),
    INDEX idx_borrow_student_book_status (student_id, book_id, status),
    CONSTRAINT fk_borrow_student FOREIGN KEY (student_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_borrow_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_borrow_copy FOREIGN KEY (copy_id)
        REFERENCES book_copies (copy_id) ON DELETE RESTRICT,
    CONSTRAINT fk_borrow_issued_by FOREIGN KEY (issued_by)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_borrow_returned_by FOREIGN KEY (returned_by)
        REFERENCES users (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(1, 4, 1, 1, '2026-10-25', '2026-11-08', NULL, 'ACTIVE', 2, NULL);

UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1;

INSERT IGNORE INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(2, 5, 2, 5, '2026-10-01', '2026-10-15', '2026-10-14', 'RETURNED', 2, 2);

