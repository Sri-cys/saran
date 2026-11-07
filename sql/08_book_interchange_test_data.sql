-- ============================================================================
-- PSG iTech Library - Feature 8: Student-to-Student Book Interchange
-- Test Data Seed Script: 08_book_interchange_test_data.sql
-- ============================================================================

-- Dedicated TEST Students (IDs 281-285)
INSERT INTO users (user_id, name, email, password_hash, phone, role, register_number, department, `year`, active) VALUES
(281, 'TEST Student Alpha', 'test.alpha281@psgitech.ac.in', '$2a$12$mohGNJyzR0i.BrCBGcaOauBw5uGH6otEWzwYKOIw9ethox8UmLOsO', '9876500281', 'STUDENT', 'TESTREG281', 'CSE', 3, TRUE),
(282, 'TEST Student Beta', 'test.beta282@psgitech.ac.in', '$2a$12$mohGNJyzR0i.BrCBGcaOauBw5uGH6otEWzwYKOIw9ethox8UmLOsO', '9876500282', 'STUDENT', 'TESTREG282', 'CSE', 3, TRUE),
(283, 'TEST Student Gamma', 'test.gamma283@psgitech.ac.in', '$2a$12$mohGNJyzR0i.BrCBGcaOauBw5uGH6otEWzwYKOIw9ethox8UmLOsO', '9876500283', 'STUDENT', 'TESTREG283', 'ECE', 2, TRUE),
(284, 'TEST Student Inactive', 'test.inactive284@psgitech.ac.in', '$2a$12$mohGNJyzR0i.BrCBGcaOauBw5uGH6otEWzwYKOIw9ethox8UmLOsO', '9876500284', 'STUDENT', 'TESTREG284', 'MECH', 4, FALSE),
(285, 'TEST Student Fined', 'test.fined285@psgitech.ac.in', '$2a$12$mohGNJyzR0i.BrCBGcaOauBw5uGH6otEWzwYKOIw9ethox8UmLOsO', '9876500285', 'STUDENT', 'TESTREG285', 'IT', 3, TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name), active = VALUES(active);

-- Dedicated TEST Book (ID 94)
INSERT INTO books (book_id, isbn, title, author, publisher, publication_year, category, department, is_archived) VALUES
(94, '978-0000000094', 'TEST Software Engineering Interchange', 'TEST Author', 'TEST Press', 2026, 'Engineering', 'CSE', FALSE)
ON DUPLICATE KEY UPDATE title = VALUES(title);

-- Dedicated TEST Copies (IDs 941-943)
INSERT INTO book_copies (copy_id, copy_code, book_id, status) VALUES
(941, 'TEST-BK94-C01', 94, 'BORROWED'),
(942, 'TEST-BK94-C02', 94, 'AVAILABLE'),
(943, 'TEST-BK94-C03', 94, 'AVAILABLE')
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- Dedicated Active Borrowing for Student Alpha (ID 9401)
INSERT INTO borrow_transactions (transaction_id, student_id, book_id, copy_id, issue_date, due_date, status, issued_by) VALUES
(9401, 281, 94, 941, CURRENT_DATE(), DATE_ADD(CURRENT_DATE(), INTERVAL 14 DAY), 'ACTIVE', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status), student_id = VALUES(student_id);

-- Outstanding Fine for Student Fined (ID 285)
INSERT INTO borrow_transactions (transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by) VALUES
(9402, 285, 94, 942, DATE_SUB(CURRENT_DATE(), INTERVAL 20 DAY), DATE_SUB(CURRENT_DATE(), INTERVAL 6 DAY), CURRENT_DATE(), 'RETURNED', 1, 1)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO fines (fine_id, borrow_transaction_id, student_id, book_id, copy_id, overdue_days, chargeable_days, fine_per_day, original_amount, paid_amount, waived_amount, outstanding_amount, status) VALUES
(9401, 9402, 285, 94, 942, 6, 6, 2.00, 12.00, 0.00, 0.00, 12.00, 'UNPAID')
ON DUPLICATE KEY UPDATE outstanding_amount = VALUES(outstanding_amount), status = VALUES(status);

