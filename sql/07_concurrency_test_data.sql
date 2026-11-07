-- =============================================================================
-- PSG iTech Library - Feature 7: Concurrency Test Data Fixtures
-- Target Schema: psgitech_library_test ONLY
-- =============================================================================

USE psgitech_library_test;

-- 1. Insert Isolated Test Students for Concurrency Testing
INSERT IGNORE INTO users (user_id, name, email, password_hash, role, register_number, department, year, active)
VALUES 
(201, 'Concurrent Student Alpha', 'conc.alpha@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104201', 'CSE', 3, TRUE),
(202, 'Concurrent Student Beta', 'conc.beta@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104202', 'CSE', 3, TRUE),
(203, 'Concurrent Student Gamma', 'conc.gamma@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104203', 'ECE', 3, TRUE),
(204, 'Concurrent Student Delta', 'conc.delta@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104204', 'MECH', 2, TRUE),
(205, 'Concurrent Student Epsilon', 'conc.epsilon@psgitech.ac.in', '$2a$10$wT8B1Z7p4C6.qX4Y6H8HzehC5k0hK5e9WqD6P5M8n9p5m2Q5o7K7W', 'STUDENT', '715521104205', 'EEE', 2, TRUE);

-- 2. Insert Test Book Title
INSERT IGNORE INTO books (book_id, title, author, isbn, publisher, publication_year, category, department, is_archived)
VALUES (99, 'High-Performance Concurrency in Java', 'Brian Goetz', '9780321349606', 'Addison-Wesley', 2006, 'Computer Science', 'CSE', FALSE);

-- 3. Insert Physical Copies for Test Book
INSERT IGNORE INTO book_copies (copy_id, book_id, copy_code, status)
VALUES 
(901, 99, 'PSG-BK99-C01', 'AVAILABLE'),
(902, 99, 'PSG-BK99-C02', 'AVAILABLE'),
(903, 99, 'PSG-BK99-C03', 'AVAILABLE'),
(904, 99, 'PSG-BK99-C04', 'AVAILABLE'),
(905, 99, 'PSG-BK99-C05', 'AVAILABLE');
