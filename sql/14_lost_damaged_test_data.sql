-- ==============================================================================
-- PSG iTech Library - Feature 14: Test Fixtures
-- TEST-marked, INSERT-only, no hardcoded numeric keys.
-- ==============================================================================

-- 1. Create TEST Users
INSERT INTO users (username, password_hash, email, role, register_number, name, department, year, phone_number, active)
VALUES 
('test_student_f14_a', '$2a$10$e8w.xeqzE6fWzrq8J/5YqOSGjP/pA8u8QZcQdG0Ccm8t41X5u8gCe', 'f14_student_a@psgitech.ac.in', 'STUDENT', '715523F1401', 'F14 Student Alpha', 'CSE', 3, '9876543210', 1),
('test_student_f14_b', '$2a$10$e8w.xeqzE6fWzrq8J/5YqOSGjP/pA8u8QZcQdG0Ccm8t41X5u8gCe', 'f14_student_b@psgitech.ac.in', 'STUDENT', '715523F1402', 'F14 Student Beta', 'ECE', 3, '9876543211', 1),
('test_student_f14_c', '$2a$10$e8w.xeqzE6fWzrq8J/5YqOSGjP/pA8u8QZcQdG0Ccm8t41X5u8gCe', 'f14_student_c@psgitech.ac.in', 'STUDENT', '715523F1403', 'F14 Student Gamma', 'MECH', 2, '9876543212', 1),
('test_student_f14_inact', '$2a$10$e8w.xeqzE6fWzrq8J/5YqOSGjP/pA8u8QZcQdG0Ccm8t41X5u8gCe', 'f14_student_inact@psgitech.ac.in', 'STUDENT', '715523F1404', 'F14 Student Inactive', 'EEE', 4, '9876543213', 0);

-- 2. Create TEST Books (with price & ISBN, without price, and without ISBN)
INSERT INTO books (isbn, title, author, publisher, publication_year, category, department, replacement_price)
VALUES 
('978-0-1414-0001-1', 'TEST Book F14 Priced with ISBN', 'Prof. F14 Author', 'Tech Press', 2022, 'Computer Science', 'CSE', 750.00),
('978-0-1414-0002-8', 'TEST Book F14 Unpriced with ISBN', 'Dr. F14 Scholar', 'Academic Pub', 2021, 'Electronics', 'ECE', NULL),
('NOISBN-F14-0003', 'TEST Book F14 Without Standard ISBN', 'Er. F14 Engineer', 'Local Pub', 2023, 'Mechanical', 'MECH', 400.00);

-- 3. Create TEST Book Copies of different ages
INSERT INTO book_copies (book_id, copy_code, status, created_at)
VALUES 
((SELECT book_id FROM books WHERE isbn = '978-0-1414-0001-1'), 'PSG-F14-BK1-C01', 'AVAILABLE', DATE_SUB(NOW(), INTERVAL 365 DAY)),
((SELECT book_id FROM books WHERE isbn = '978-0-1414-0001-1'), 'PSG-F14-BK1-C02', 'AVAILABLE', DATE_SUB(NOW(), INTERVAL 730 DAY)),
((SELECT book_id FROM books WHERE isbn = '978-0-1414-0002-8'), 'PSG-F14-BK2-C01', 'AVAILABLE', NOW()),
((SELECT book_id FROM books WHERE isbn = 'NOISBN-F14-0003'), 'PSG-F14-BK3-C01', 'AVAILABLE', NULL);
