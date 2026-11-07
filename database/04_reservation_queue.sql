-- =============================================================================
-- PSG iTech Library - Feature 4: Reservation Queue Schema Migration
-- Database: psgitech_library (MySQL 8.0+ / InnoDB)
-- =============================================================================

USE psgitech_library;

CREATE TABLE IF NOT EXISTS reservations (
    reservation_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    book_id INT NOT NULL,
    reservation_date DATE NOT NULL,
    status ENUM('WAITING','ALLOCATED','FULFILLED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'WAITING',
    cancelled_at TIMESTAMP NULL DEFAULT NULL,
    cancelled_by INT NULL,
    active_reservation_lock VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status IN ('WAITING','ALLOCATED') 
             THEN CONCAT(student_id, '-', book_id) 
             ELSE NULL 
        END
    ) STORED,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_active_reservation_lock (active_reservation_lock),
    INDEX idx_res_book_status_fifo (book_id, status, created_at, reservation_id),
    INDEX idx_res_student_status (student_id, status),
    INDEX idx_res_status (status),
    CONSTRAINT fk_res_student FOREIGN KEY (student_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_res_book FOREIGN KEY (book_id)
        REFERENCES books (book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_res_cancelled_by FOREIGN KEY (cancelled_by)
        REFERENCES users (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

