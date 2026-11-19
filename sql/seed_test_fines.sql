-- ============================================================================
-- PSG iTech Library - Seed Test Fines for Razorpay Payment Testing
-- Script: sql/seed_test_fines.sql
-- Inserts 2 realistic UNPAID fines (₹45.00 and ₹80.00) for active student account
-- (Sri Saran / srisaran2111@gmail.com / 715525104210) if currently 0 unpaid fines.
-- ============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS seed_student_test_fines$$

CREATE PROCEDURE seed_student_test_fines()
BEGIN
    DECLARE v_student_id INT;
    DECLARE v_unpaid_count INT DEFAULT 0;
    DECLARE v_tx1_id INT;
    DECLARE v_tx2_id INT;
    DECLARE v_book1_id INT;
    DECLARE v_copy1_id INT;
    DECLARE v_book2_id INT;
    DECLARE v_copy2_id INT;

    -- 1. Identify active student user ID for Sri Saran
    SELECT user_id INTO v_student_id
    FROM users 
    WHERE (email = 'srisaran2111@gmail.com' OR register_number = '715525104210' OR name LIKE '%Sri Saran%')
      AND role = 'STUDENT'
    ORDER BY user_id DESC
    LIMIT 1;

    -- If student not found, pick first available registered student
    IF v_student_id IS NULL THEN
        SELECT user_id INTO v_student_id FROM users WHERE role = 'STUDENT' ORDER BY user_id ASC LIMIT 1;
    END IF;

    IF v_student_id IS NOT NULL THEN
        -- Check existing unpaid fines count for this student
        SELECT COUNT(*) INTO v_unpaid_count
        FROM fines
        WHERE student_id = v_student_id AND status = 'UNPAID';

        IF v_unpaid_count = 0 THEN
            -- Pick two active books and copies
            SELECT b.book_id, bc.copy_id INTO v_book1_id, v_copy1_id
            FROM books b 
            JOIN book_copies bc ON b.book_id = bc.book_id
            WHERE b.book_id = 2
            LIMIT 1;

            SELECT b.book_id, bc.copy_id INTO v_book2_id, v_copy2_id
            FROM books b 
            JOIN book_copies bc ON b.book_id = bc.book_id
            WHERE b.book_id = 4
            LIMIT 1;

            IF v_book1_id IS NULL THEN
                SELECT b.book_id, bc.copy_id INTO v_book1_id, v_copy1_id FROM books b JOIN book_copies bc ON b.book_id = bc.book_id LIMIT 1;
            END IF;
            IF v_book2_id IS NULL THEN
                SELECT b.book_id, bc.copy_id INTO v_book2_id, v_copy2_id FROM books b JOIN book_copies bc ON b.book_id = bc.book_id WHERE b.book_id <> v_book1_id LIMIT 1;
            END IF;

            -- Insert Returned Borrowing Transaction 1 (Late by 9 chargeable days)
            INSERT INTO borrow_transactions (
                student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by
            ) VALUES (
                v_student_id, v_book1_id, v_copy1_id, 
                DATE_SUB(CURDATE(), INTERVAL 23 DAY), 
                DATE_SUB(CURDATE(), INTERVAL 9 DAY), 
                CURDATE(), 
                'RETURNED',
                2,
                2
            );
            SET v_tx1_id = LAST_INSERT_ID();

            -- Insert Fine 1: ₹45.00 (9 days * ₹5.00/day)
            INSERT INTO fines (
                borrow_transaction_id, student_id, book_id, copy_id,
                overdue_days, chargeable_days, fine_per_day, grace_period_days, max_fine_applied,
                original_amount, paid_amount, waived_amount, outstanding_amount,
                status, fine_type
            ) VALUES (
                v_tx1_id, v_student_id, v_book1_id, v_copy1_id,
                9, 9, 5.00, 0, 500.00,
                45.00, 0.00, 0.00, 45.00,
                'UNPAID', 'OVERDUE'
            );

            -- Insert Returned Borrowing Transaction 2 (Late by 16 chargeable days)
            INSERT INTO borrow_transactions (
                student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by
            ) VALUES (
                v_student_id, v_book2_id, v_copy2_id, 
                DATE_SUB(CURDATE(), INTERVAL 30 DAY), 
                DATE_SUB(CURDATE(), INTERVAL 16 DAY), 
                CURDATE(), 
                'RETURNED',
                2,
                2
            );
            SET v_tx2_id = LAST_INSERT_ID();

            -- Insert Fine 2: ₹80.00 (16 days * ₹5.00/day)
            INSERT INTO fines (
                borrow_transaction_id, student_id, book_id, copy_id,
                overdue_days, chargeable_days, fine_per_day, grace_period_days, max_fine_applied,
                original_amount, paid_amount, waived_amount, outstanding_amount,
                status, fine_type
            ) VALUES (
                v_tx2_id, v_student_id, v_book2_id, v_copy2_id,
                16, 16, 5.00, 0, 500.00,
                80.00, 0.00, 0.00, 80.00,
                'UNPAID', 'OVERDUE'
            );

            SELECT CONCAT('Successfully seeded 2 UNPAID fines (₹45.00 and ₹80.00) for student user_id: ', v_student_id) AS result;
        ELSE
            SELECT CONCAT('Student user_id ', v_student_id, ' already has ', v_unpaid_count, ' unpaid fine(s). Skipping seed.') AS result;
        END IF;
    ELSE
        SELECT 'No student user found in users table.' AS result;
    END IF;
END$$

DELIMITER ;

CALL seed_student_test_fines();
DROP PROCEDURE IF EXISTS seed_student_test_fines;
