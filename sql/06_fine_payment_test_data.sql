-- =============================================================================
-- PSG iTech Library - Feature 6: Test Data Seed Script
-- Database: psgitech_library (MySQL 8.0+ / 26.10+)
-- Purpose: Seed dedicated test records for Part 14 verification of fine calculation,
--          manual UPI payments, waivers, and eligibility blocking.
-- =============================================================================

USE psgitech_library;

-- 1. Insert TEST Students
INSERT INTO users (name, email, password_hash, phone, role, register_number, department, year, active)
VALUES 
('TEST Student DueToday', 'test.fine.today@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543221', 'STUDENT', 'TEST-21L801', 'CSE', 3, TRUE),
('TEST Student OneDayLate', 'test.fine.1day@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543222', 'STUDENT', 'TEST-21L802', 'CSE', 3, TRUE),
('TEST Student FiveDaysLate', 'test.fine.5days@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543223', 'STUDENT', 'TEST-21L803', 'CSE', 3, TRUE),
('TEST Student ThirtyDaysLate', 'test.fine.30days@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543224', 'STUDENT', 'TEST-21L804', 'CSE', 3, TRUE),
('TEST Student MultiFines', 'test.fine.multi@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543225', 'STUDENT', 'TEST-21L805', 'CSE', 3, TRUE);

-- 2. Insert TEST Textbooks
INSERT INTO books (isbn, title, author, publisher, edition, publication_year, category, department, description, is_archived)
VALUES 
('978-0-TEST-FINE1', 'TEST - Principles of Financial Ledger Engineering', 'Alice Financial, Bob Ledger', 'Academic Press', '1st Edition', 2026, 'Computer Science', 'CSE', 'Test book for overdue fine generation and payment testing.', FALSE),
('978-0-TEST-FINE2', 'TEST - Advanced Payment Verification Systems', 'Charlie UPI, David Audit', 'Academic Press', '2nd Edition', 2026, 'Computer Science', 'CSE', 'Test book for multi-fine settlement and waiver testing.', FALSE);

-- 3. Insert Physical Copies
SET @book1_id = (SELECT book_id FROM books WHERE isbn = '978-0-TEST-FINE1');
SET @book2_id = (SELECT book_id FROM books WHERE isbn = '978-0-TEST-FINE2');

INSERT INTO book_copies (copy_code, book_id, status)
VALUES 
('PSG-TEST-FN1-C01', @book1_id, 'BORROWED'),
('PSG-TEST-FN1-C02', @book1_id, 'BORROWED'),
('PSG-TEST-FN1-C03', @book1_id, 'BORROWED'),
('PSG-TEST-FN1-C04', @book1_id, 'BORROWED'),
('PSG-TEST-FN2-C01', @book2_id, 'BORROWED'),
('PSG-TEST-FN2-C02', @book2_id, 'BORROWED');

-- 4. Insert ACTIVE Borrow Transactions with predetermined due dates
SET @staff_id = (SELECT user_id FROM users WHERE role IN ('LIBRARIAN', 'ADMIN') LIMIT 1);
SET @student_today_id = (SELECT user_id FROM users WHERE register_number = 'TEST-21L801');
SET @student_1day_id  = (SELECT user_id FROM users WHERE register_number = 'TEST-21L802');
SET @student_5days_id = (SELECT user_id FROM users WHERE register_number = 'TEST-21L803');
SET @student_30days_id= (SELECT user_id FROM users WHERE register_number = 'TEST-21L804');
SET @student_multi_id = (SELECT user_id FROM users WHERE register_number = 'TEST-21L805');

SET @copy1_id = (SELECT copy_id FROM book_copies WHERE copy_code = 'PSG-TEST-FN1-C01');
SET @copy2_id = (SELECT copy_id FROM book_copies WHERE copy_code = 'PSG-TEST-FN1-C02');
SET @copy3_id = (SELECT copy_id FROM book_copies WHERE copy_code = 'PSG-TEST-FN1-C03');
SET @copy4_id = (SELECT copy_id FROM book_copies WHERE copy_code = 'PSG-TEST-FN1-C04');
SET @copy5_id = (SELECT copy_id FROM book_copies WHERE copy_code = 'PSG-TEST-FN2-C01');
SET @copy6_id = (SELECT copy_id FROM book_copies WHERE copy_code = 'PSG-TEST-FN2-C02');

-- Tx 1: Due Today (0 days late)
INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by)
VALUES (@student_today_id, @book1_id, @copy1_id, DATE_SUB(CURDATE(), INTERVAL 14 DAY), CURDATE(), 'ACTIVE', @staff_id);

-- Tx 2: 1 Day Late (Due yesterday)
INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by)
VALUES (@student_1day_id, @book1_id, @copy2_id, DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'ACTIVE', @staff_id);

-- Tx 3: 5 Days Late
INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by)
VALUES (@student_5days_id, @book1_id, @copy3_id, DATE_SUB(CURDATE(), INTERVAL 19 DAY), DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'ACTIVE', @staff_id);

-- Tx 4: 30 Days Late
INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by)
VALUES (@student_30days_id, @book1_id, @copy4_id, DATE_SUB(CURDATE(), INTERVAL 44 DAY), DATE_SUB(CURDATE(), INTERVAL 30 DAY), 'ACTIVE', @staff_id);

-- Tx 5 & 6: Student with Multiple Overdue Books (3 days late and 10 days late)
INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by)
VALUES 
(@student_multi_id, @book2_id, @copy5_id, DATE_SUB(CURDATE(), INTERVAL 17 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'ACTIVE', @staff_id),
(@student_multi_id, @book2_id, @copy6_id, DATE_SUB(CURDATE(), INTERVAL 24 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY), 'ACTIVE', @staff_id);

