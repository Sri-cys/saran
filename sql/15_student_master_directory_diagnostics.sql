-- Diagnostics for Feature 15: Student Master Directory
SELECT COUNT(*) AS total_students FROM students;
SELECT registration_status, COUNT(*) AS count FROM students GROUP BY registration_status;
SELECT account_status, COUNT(*) AS count FROM students GROUP BY account_status;
SELECT s.permanent_roll_number, s.full_name, s.registration_status, s.user_id, u.email
FROM students s
LEFT JOIN users u ON s.user_id = u.user_id
WHERE s.registration_status = 'REGISTERED' OR s.user_id IS NOT NULL;
