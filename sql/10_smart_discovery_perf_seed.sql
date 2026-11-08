-- =============================================================================
-- PSG iTech Library - Feature 10: Performance Benchmark Dataset Generator
-- Generates synthetic TEST-marked records for D12 benchmarking:
-- ~2,000 students, ~20,000 books, ~60,000 copies, ~200,000 borrows, ~50,000 reservations
-- Safe: Runs ONLY on schemas ending with _test
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_seed_perf_dataset;

DELIMITER $$
CREATE PROCEDURE sp_seed_perf_dataset()
BEGIN
    DECLARE current_db VARCHAR(100);
    DECLARE student_count INT DEFAULT 2000;
    DECLARE book_count INT DEFAULT 20000;
    DECLARE chunk_start INT;
    DECLARE chunk_end INT;
    DECLARE chunk_size INT DEFAULT 2000;

    SELECT DATABASE() INTO current_db;
    IF current_db NOT LIKE '%_test' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Refusing to run seed script on non-test schema!';
    END IF;

    SET SESSION cte_max_recursion_depth = 100000;

    -- 1. Insert 2,000 Test Students
    SET chunk_start = 1;
    WHILE chunk_start <= student_count DO
        SET chunk_end = LEAST(chunk_start + chunk_size - 1, student_count);
        INSERT INTO users (name, email, password_hash, phone, role, register_number, department, year, active, email_verified)
        WITH RECURSIVE seq AS (
            SELECT chunk_start AS n
            UNION ALL
            SELECT n + 1 FROM seq WHERE n < chunk_end
        )
        SELECT 
            CONCAT('TEST_USER_', n),
            CONCAT('test_user_', n, '@psgitech.ac.in'),
            '$2a$12$e8ZbzKk84uYf9f.E1i8Vq.Vn0hA57qf9C8Jb7hG2K6n3d1F5b9vCe',
            CONCAT('98765', LPAD(n, 5, '0')),
            'STUDENT',
            CONCAT('TEST_REG_', LPAD(n, 5, '0')),
            ELT(1 + (n MOD 6), 'CSE', 'IT', 'AI&DS', 'ECE', 'MECH', 'CIVIL'),
            1 + (n MOD 4),
            1,
            1
        FROM seq;
        SET chunk_start = chunk_end + 1;
    END WHILE;

    -- 2. Insert 20,000 Test Books
    SET chunk_start = 1;
    WHILE chunk_start <= book_count DO
        SET chunk_end = LEAST(chunk_start + chunk_size - 1, book_count);
        INSERT INTO books (isbn, title, author, publisher, edition, publication_year, category, department, description, is_archived, created_at)
        WITH RECURSIVE seq AS (
            SELECT chunk_start AS n
            UNION ALL
            SELECT n + 1 FROM seq WHERE n < chunk_end
        )
        SELECT 
            CONCAT('TEST-978-', LPAD(n, 8, '0')),
            CONCAT('TEST Book ', ELT(1 + (n MOD 8), 'Data Structures', 'Operating Systems', 'Machine Learning', 'Computer Networks', 'Cloud Computing', 'Database Systems', 'Algorithms', 'Software Engineering'), ' Edition ', n),
            CONCAT('Author ', ELT(1 + (n MOD 10), 'Silberschatz', 'Tanenbaum', 'Cormen', 'Russell', 'Knuth', 'Bloch', 'Martin', 'Lamport', 'Gosling', 'Stroustrup')),
            ELT(1 + (n MOD 5), 'Pearson Education', 'McGraw Hill', 'MIT Press', 'OReilly Media', 'Cambridge University Press'),
            CONCAT(1 + (n MOD 5), 'th Edition'),
            2000 + (n MOD 25),
            ELT(1 + (n MOD 7), 'Computer Science', 'Information Technology', 'Artificial Intelligence', 'Electronics', 'Mechanical', 'Civil', 'Mathematics'),
            ELT(1 + (n MOD 6), 'CSE', 'IT', 'AI&DS', 'ECE', 'MECH', 'CIVIL'),
            CONCAT('Comprehensive study textbook for engineering course module ', n),
            0,
            DATE_SUB(NOW(), INTERVAL (n MOD 365) DAY)
        FROM seq;
        SET chunk_start = chunk_end + 1;
    END WHILE;

    -- 3. Insert 60,000 Test Book Copies (3 copies per book using real book_ids)
    INSERT INTO book_copies (copy_code, book_id, status)
    SELECT CONCAT('PSG-TEST-', b.book_id, '-C01'), b.book_id, 'AVAILABLE'
    FROM books b WHERE b.isbn LIKE 'TEST-978-%'
    UNION ALL
    SELECT CONCAT('PSG-TEST-', b.book_id, '-C02'), b.book_id, 'AVAILABLE'
    FROM books b WHERE b.isbn LIKE 'TEST-978-%'
    UNION ALL
    SELECT CONCAT('PSG-TEST-', b.book_id, '-C03'), b.book_id, 'BORROWED'
    FROM books b WHERE b.isbn LIKE 'TEST-978-%';

    -- 4. Temporary staging for student IDs and copy IDs
    DROP TEMPORARY TABLE IF EXISTS tmp_students;
    CREATE TEMPORARY TABLE tmp_students (
        idx INT AUTO_INCREMENT PRIMARY KEY,
        user_id INT
    ) ENGINE=MEMORY;
    INSERT INTO tmp_students (user_id)
    SELECT user_id FROM users WHERE email LIKE 'test_user_%@psgitech.ac.in' ORDER BY user_id;

    DROP TEMPORARY TABLE IF EXISTS tmp_copies;
    CREATE TEMPORARY TABLE tmp_copies (
        idx INT AUTO_INCREMENT PRIMARY KEY,
        copy_id INT,
        book_id INT
    ) ENGINE=MEMORY;
    INSERT INTO tmp_copies (copy_id, book_id)
    SELECT copy_id, book_id FROM book_copies WHERE copy_code LIKE 'PSG-TEST-%' ORDER BY copy_id;

    -- 5. Insert 200,000 Test Borrow Transactions in 10 chunks of 20,000
    SET chunk_start = 1;
    WHILE chunk_start <= 200000 DO
        SET chunk_end = chunk_start + 20000 - 1;
        INSERT INTO borrow_transactions (student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by)
        WITH RECURSIVE seq AS (
            SELECT chunk_start AS n
            UNION ALL
            SELECT n + 1 FROM seq WHERE n < chunk_end
        )
        SELECT 
            s.user_id,
            c.book_id,
            c.copy_id,
            DATE_SUB(CURRENT_DATE, INTERVAL (1 + (n MOD 120)) DAY),
            DATE_ADD(DATE_SUB(CURRENT_DATE, INTERVAL (1 + (n MOD 120)) DAY), INTERVAL 14 DAY),
            DATE_ADD(DATE_SUB(CURRENT_DATE, INTERVAL (1 + (n MOD 120)) DAY), INTERVAL 10 DAY),
            'RETURNED',
            1
        FROM seq
        JOIN tmp_students s ON s.idx = (1 + (seq.n MOD 2000))
        JOIN tmp_copies c ON c.idx = (1 + (seq.n MOD 60000));
        SET chunk_start = chunk_end + 1;
    END WHILE;

    -- 6. Insert 50,000 Test Reservations in 5 chunks of 10,000
    SET chunk_start = 1;
    WHILE chunk_start <= 50000 DO
        SET chunk_end = chunk_start + 10000 - 1;
        INSERT INTO reservations (student_id, book_id, reservation_date, status, created_at)
        WITH RECURSIVE seq AS (
            SELECT chunk_start AS n
            UNION ALL
            SELECT n + 1 FROM seq WHERE n < chunk_end
        )
        SELECT 
            s.user_id,
            c.book_id,
            DATE_SUB(CURRENT_DATE, INTERVAL (1 + (n MOD 60)) DAY),
            'FULFILLED',
            DATE_SUB(NOW(), INTERVAL (1 + (n MOD 60)) DAY)
        FROM seq
        JOIN tmp_students s ON s.idx = (1 + ((seq.n * 3) MOD 2000))
        JOIN tmp_copies c ON c.idx = (1 + ((seq.n * 7) MOD 60000));
        SET chunk_start = chunk_end + 1;
    END WHILE;

    DROP TEMPORARY TABLE IF EXISTS tmp_students;
    DROP TEMPORARY TABLE IF EXISTS tmp_copies;

    SELECT 'Performance dataset generated successfully.' AS result;
END$$
DELIMITER ;
