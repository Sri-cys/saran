-- ============================================================================
-- PSG iTech Library - Migration 18: Librarian Smart Desk Operations
-- Idempotent DDL for 2D Floor Layout, Goodwill Extensions, Config Settings
-- ============================================================================

-- 1. Table: library_layout (2D Floor Grid & Shelf Heatmap)
CREATE TABLE IF NOT EXISTS library_layout (
    id INT AUTO_INCREMENT PRIMARY KEY,
    layout_id INT NULL,
    floor VARCHAR(20) NOT NULL,
    block VARCHAR(50) NOT NULL,
    section VARCHAR(50) NOT NULL,
    rack VARCHAR(50) NOT NULL,
    grid_row INT NOT NULL,
    grid_col INT NOT NULL,
    row_span INT NOT NULL DEFAULT 1,
    col_span INT NOT NULL DEFAULT 1,
    label VARCHAR(100) NULL,
    category_hint VARCHAR(100) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_floor_grid (floor, grid_row, grid_col),
    INDEX idx_layout_rack (floor, block, section, rack)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Table: goodwill_extensions (One-Time-Per-Student Goodwill Loan Due Date Extension)
CREATE TABLE IF NOT EXISTS goodwill_extensions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    extension_id INT NULL,
    student_id INT NOT NULL,
    transaction_id INT NOT NULL,
    loan_id INT NULL,
    extra_days INT NOT NULL DEFAULT 2,
    reason TEXT NOT NULL,
    previous_due_date DATE NULL,
    new_due_date DATE NULL,
    granted_by INT NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_goodwill_student (student_id),
    INDEX idx_ge_student (student_id),
    INDEX idx_ge_transaction (transaction_id),
    CONSTRAINT fk_ge_student FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_ge_staff FOREIGN KEY (granted_by) REFERENCES users(user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ensure config_settings table exists
CREATE TABLE IF NOT EXISTS config_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed / ensure required config settings
INSERT INTO config_settings (setting_key, setting_value, description)
VALUES 
    ('high_demand_queue_threshold', '4', 'Threshold of waiting reservations to mark book as High Demand'),
    ('copy_shortage_ratio', '2.0', 'Ratio of waiting requests to active copies indicating shortage'),
    ('goodwill_extension_days', '2', 'Default goodwill extension days granted by librarian (+2 days)'),
    ('trust_exemplary_min_returns', '5', 'Minimum on-time returns required for exemplary trust standing'),
    ('trust_max_overdue_days_for_exemplary', '0', 'Maximum allowable lifetime overdue days for exemplary trust standing')
ON DUPLICATE KEY UPDATE 
    setting_value = VALUES(setting_value),
    description = VALUES(description);
