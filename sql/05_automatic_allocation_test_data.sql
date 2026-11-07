-- =============================================================================
-- PSG iTech Library - Feature 5: Automatic Book Allocation Test Data
-- Purpose: Reproduce FIFO allocation, candidate skip (inactive / limit),
--          collection, and release scenarios in PART 12.
-- Target Database: psgitech_library (MySQL 8.0+ / 26.10+)
-- =============================================================================

USE psgitech_library;

-- 1. Insert Test Students
INSERT INTO users (name, email, password_hash, phone, role, register_number, department, year, active)
VALUES 
('TEST Student Alpha', 'test.alloc.alpha@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543210', 'STUDENT', 'TEST-21L901', 'CSE', 3, TRUE),
('TEST Student Beta', 'test.alloc.beta@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543211', 'STUDENT', 'TEST-21L902', 'CSE', 3, TRUE),
('TEST Student Gamma', 'test.alloc.gamma@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543212', 'STUDENT', 'TEST-21L903', 'CSE', 3, TRUE),
('TEST Student Inactive', 'test.alloc.inactive@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543213', 'STUDENT', 'TEST-21L904', 'CSE', 3, FALSE),
('TEST Student AtLimit', 'test.alloc.limit@psgitech.ac.in', '$2a$10$e846ZJpZ5wXhX8C6EwKjE.iR62yUfZs9fO2O1GZ8m2hC7p4K9E0q2', '9876543214', 'STUDENT', 'TEST-21L905', 'CSE', 3, TRUE);

-- 2. Insert Test Book
INSERT INTO books (isbn, title, author, publisher, edition, publication_year, category, department, description, is_archived)
VALUES 
('978-0-TEST-ALLOC1', 'TEST - Distributed Systems for Automatic Allocation', 'Leslie Lamport, Martin Kleppmann', 'Tech Press', '1st Edition', 2026, 'Computer Science', 'CSE', 'Test textbook for validating Feature 5 automatic allocation queue processing.', FALSE);

-- 3. Insert 3 Physical Copies (all initialized as BORROWED)
SET @test_book_id = (SELECT book_id FROM books WHERE isbn = '978-0-TEST-ALLOC1');

INSERT INTO book_copies (copy_code, book_id, status)
VALUES 
('PSG-TEST-AL1-C01', @test_book_id, 'BORROWED'),
('PSG-TEST-AL1-C02', @test_book_id, 'BORROWED'),
('PSG-TEST-AL1-C03', @test_book_id, 'BORROWED');

-- 4. Insert Active Borrow Transactions (Borrowing Limit = 3, so student AtLimit borrows all 3 copies)
SET @staff_id = (SELECT user_id FROM users WHERE role IN ('LIBRARIAN', 'ADMIN') LIMIT 1);
SET @student_limit_id = (SELECT user_id FROM users WHERE register_number = 'TEST-21L905');
SET @copy1_id = (SELECT copy_id FROM book_copies WHERE copy_code = 'PSG-TEST-AL1-C01');
SET @copy2_id = (SELECT copy_id FROM book_copies WHERE copy_code = 'PSG-TEST-AL1-C02');
SET @copy3_id = (SELECT copy_id FROM book_copies WHERE copy_code = 'PSG-TEST-AL1-C03');

INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, status, issued_by)
VALUES 
(@student_limit_id, @test_book_id, @copy1_id, '2026-10-20', '2026-11-03', 'ACTIVE', @staff_id),
(@student_limit_id, @test_book_id, @copy2_id, '2026-10-20', '2026-11-03', 'ACTIVE', @staff_id),
(@student_limit_id, @test_book_id, @copy3_id, '2026-10-20', '2026-11-03', 'ACTIVE', @staff_id);

-- 5. Insert Waiting Reservations in Specific FIFO Sequence
-- #1 Inactive student (must be skipped)
SET @student_inactive_id = (SELECT user_id FROM users WHERE register_number = 'TEST-21L904');
SET @student_alpha_id = (SELECT user_id FROM users WHERE register_number = 'TEST-21L901');
SET @student_beta_id = (SELECT user_id FROM users WHERE register_number = 'TEST-21L902');
SET @student_gamma_id = (SELECT user_id FROM users WHERE register_number = 'TEST-21L903');

INSERT INTO reservations (student_id, book_id, reservation_date, status, created_at)
VALUES 
(@student_inactive_id, @test_book_id, '2026-10-25', 'WAITING', '2026-10-25 09:00:00.000000'),
(@student_alpha_id, @test_book_id, '2026-10-25', 'WAITING', '2026-10-25 10:00:00.000000'),
(@student_beta_id, @test_book_id, '2026-10-25', 'WAITING', '2026-10-25 11:00:00.000000'),
(@student_gamma_id, @test_book_id, '2026-10-25', 'WAITING', '2026-10-25 12:00:00.000000');

