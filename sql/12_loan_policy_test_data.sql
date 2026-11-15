-- ==============================================================================
-- PSG iTech Library - Feature 12: Exam-Season Dynamic Borrowing Policy
-- Test Data Fixture: 12_loan_policy_test_data.sql
-- Dedicated test books and reservations for policy verification
-- ==============================================================================

-- Create a dedicated test book with 5 copies for Feature 12 verification
INSERT INTO books (book_id, isbn, title, author, publisher, publication_year, category, department)
VALUES (7770, '978-0131103627', 'TEST-F12 Advanced Algorithms & Data Structures', 'Brian W. Kernighan', 'Prentice Hall', 2024, 'Computer Science', 'CSE')
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO book_copies (copy_id, book_id, copy_code, status)
VALUES 
    (77701, 7770, 'PSG-BK7770-C01', 'AVAILABLE'),
    (77702, 7770, 'PSG-BK7770-C02', 'AVAILABLE'),
    (77703, 7770, 'PSG-BK7770-C03', 'AVAILABLE'),
    (77704, 7770, 'PSG-BK7770-C04', 'AVAILABLE'),
    (77705, 7770, 'PSG-BK7770-C05', 'AVAILABLE')
ON DUPLICATE KEY UPDATE status = VALUES(status);
