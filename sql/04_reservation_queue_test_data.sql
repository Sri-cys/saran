-- =============================================================================
-- PSG iTech Library - Feature 4: Test Data for Verification (TEST ONLY)
-- Strict Safety Rules:
-- 1. All records carry explicit "TEST - " identifiers.
-- 2. Inserts ONLY; no modifications to existing real library records.
-- 3. Dynamic subqueries / variable assignment avoids hardcoded PK collisions.
-- =============================================================================

USE psgitech_library;

-- 1. Insert Test Students
INSERT IGNORE INTO users (name, email, password_hash, phone, role, register_number, department, `year`, active)
VALUES 
('TEST - Student Alpha', 'test.student.alpha@psgitech.test', '$2a$12$e/z/Pj5XnKkZ6i6v9x8tDeJzIeG4mB6u4zHqU7i8J8b8yB.test.1', '9876500001', 'STUDENT', 'TEST-715522104001', 'CSE', 3, TRUE),
('TEST - Student Beta', 'test.student.beta@psgitech.test', '$2a$12$e/z/Pj5XnKkZ6i6v9x8tDeJzIeG4mB6u4zHqU7i8J8b8yB.test.2', '9876500002', 'STUDENT', 'TEST-715522104002', 'CSE', 3, TRUE),
('TEST - Student Gamma', 'test.student.gamma@psgitech.test', '$2a$12$e/z/Pj5XnKkZ6i6v9x8tDeJzIeG4mB6u4zHqU7i8J8b8yB.test.3', '9876500003', 'STUDENT', 'TEST-715522104003', 'IT', 2, TRUE),
('TEST - Student Delta', 'test.student.delta@psgitech.test', '$2a$12$e/z/Pj5XnKkZ6i6v9x8tDeJzIeG4mB6u4zHqU7i8J8b8yB.test.4', '9876500004', 'STUDENT', 'TEST-715522104004', 'ECE', 4, TRUE);

-- 2. Insert Test Books
-- Book 1: Fully Checked Out Book (all copies BORROWED - Reservable)
INSERT IGNORE INTO books (isbn, title, author, publisher, publication_year, category, department, description, is_archived)
VALUES 
('TEST-978-000000001', 'TEST - Book All Borrowed', 'Author Test One', 'Test Press', 2024, 'Computer Science', 'CSE', 'A test book where all physical copies are actively borrowed.', FALSE),
('TEST-978-000000002', 'TEST - Book With Available Copy', 'Author Test Two', 'Test Press', 2024, 'Data Science', 'AI&DS', 'A test book that currently has available copies on the shelf.', FALSE);

-- 3. Insert Test Physical Copies
-- For Book 1: 2 copies, both BORROWED
INSERT IGNORE INTO book_copies (copy_code, book_id, status)
SELECT 'TEST-CPY-001-A', book_id, 'BORROWED' FROM books WHERE isbn = 'TEST-978-000000001';

INSERT IGNORE INTO book_copies (copy_code, book_id, status)
SELECT 'TEST-CPY-001-B', book_id, 'BORROWED' FROM books WHERE isbn = 'TEST-978-000000001';

-- For Book 2: 1 copy AVAILABLE
INSERT IGNORE INTO book_copies (copy_code, book_id, status)
SELECT 'TEST-CPY-002-A', book_id, 'AVAILABLE' FROM books WHERE isbn = 'TEST-978-000000002';

