INSERT INTO users (name, email, password_hash, phone, role, register_number, employee_id, department, `year`, active, must_change_password)
VALUES 
('Admin Office', 'admin@psgitech.ac.in', '$2a$12$mohGNJyzR0i.BrCBGcaOauBw5uGH6otEWzwYKOIw9ethox8UmLOsO', '9876543210', 'ADMIN', NULL, NULL, NULL, NULL, TRUE, FALSE),
('Dr. K. Ramanathan', 'librarian@psgitech.ac.in', '$2a$12$q9pWdhQeyqI1vvJz5fJubeJLLnizFXPoYX5k5zJtBTyhIGiMhlxku', '9876543211', 'LIBRARIAN', NULL, 'LIB101', NULL, NULL, TRUE, FALSE),
('Ms. S. Meena', 'meena.lib@psgitech.ac.in', '$2a$12$ljfDketAW/uNDlHvhXZkce5OQRn4qTRz1OwRWCyglBnjyzMn2bVNu', '9876543212', 'LIBRARIAN', NULL, 'LIB102', NULL, NULL, TRUE, TRUE),
('Sivakathir S', 'sivakathir.22cs@psgitech.ac.in', '$2a$12$Jd.ZAaa7IFxQnxnXv.Fb6.0bG6pDBHZ913zg4pHnhRMH2OK.G2.qG', '9876543213', 'STUDENT', '715522104045', NULL, 'CSE', 3, TRUE, FALSE),
('Ananya Ramesh', 'ananya.23ai@psgitech.ac.in', '$2a$12$Jd.ZAaa7IFxQnxnXv.Fb6.0bG6pDBHZ913zg4pHnhRMH2OK.G2.qG', '9876543214', 'STUDENT', '715523105012', NULL, 'AI&DS', 2, TRUE, FALSE),
('Rahul Kumar', 'rahul.21me@psgitech.ac.in', '$2a$12$Jd.ZAaa7IFxQnxnXv.Fb6.0bG6pDBHZ913zg4pHnhRMH2OK.G2.qG', '9876543215', 'STUDENT', '715521102030', NULL, 'MECH', 4, FALSE, FALSE);

INSERT INTO books (book_id, isbn, title, author, publisher, edition, publication_year, category, department, description, cover_image_url, is_archived) VALUES
(1, '978-0262033848', 'Introduction to Algorithms', 'Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, Clifford Stein', 'MIT Press', '4th Edition', 2022, 'Data Structures', 'CSE', 'The definitive modern algorithms textbook covering asymptotic analysis, sorting, dynamic programming, greedy algorithms, and graph theory.', 'https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg', FALSE),
(2, '978-0132350884', 'Clean Code: A Handbook of Agile Software Craftsmanship', 'Robert C. Martin', 'Prentice Hall', '1st Edition', 2008, 'Programming', 'CSE', 'Best practices and clean architecture patterns for writing robust, maintainable, and readable software systems.', 'https://covers.openlibrary.org/b/isbn/9780132350884-L.jpg', FALSE),
(3, '978-0136042594', 'Artificial Intelligence: A Modern Approach', 'Stuart Russell, Peter Norvig', 'Pearson', '4th Edition', 2020, 'Artificial Intelligence', 'AI&DS', 'Comprehensive university reference on search algorithms, probabilistic reasoning, machine learning, and natural language processing.', 'https://covers.openlibrary.org/b/isbn/9780136042594-L.jpg', FALSE),
(4, '978-0132126953', 'Computer Networks', 'Andrew S. Tanenbaum, David J. Wetherall', 'Pearson', '5th Edition', 2011, 'Computer Science', 'CSE', 'Covers protocol architecture, physical layers, data links, wireless transmission, routing algorithms, and internet security.', 'https://covers.openlibrary.org/b/isbn/9780132126953-L.jpg', FALSE),
(5, '978-1119456339', 'Operating System Concepts', 'Abraham Silberschatz, Peter B. Galvin, Greg Gagne', 'Wiley', '10th Edition', 2018, 'Computer Science', 'CSE', 'Core foundational textbook covering process scheduling, multi-threading, concurrency, memory management, and file systems.', 'https://covers.openlibrary.org/b/isbn/9781119456339-L.jpg', FALSE),
(6, '978-0078022159', 'Database System Concepts', 'Abraham Silberschatz, Henry F. Korth, S. Sudarshan', 'McGraw-Hill Education', '7th Edition', 2019, 'Computer Science', 'IT', 'Comprehensive guide to relational data models, SQL queries, transaction processing, indexing, and distributed databases.', 'https://covers.openlibrary.org/b/isbn/9780078022159-L.jpg', FALSE),
(7, '978-0199464689', 'Linear Algebra and Its Applications', 'Gilbert Strang', 'Cengage Learning', '4th Edition', 2016, 'Mathematics', 'GENERAL', 'Essential linear algebra concepts for computing, including vector spaces, matrix factorization, eigenvalues, and SVD.', 'https://covers.openlibrary.org/b/isbn/9780199464689-L.jpg', FALSE),
(8, '978-0199339136', 'Microelectronic Circuits', 'Adel S. Sedra, Kenneth C. Smith', 'Oxford University Press', '7th Edition', 2014, 'Electronics', 'ECE', 'Foundational curriculum textbook analyzing operational amplifiers, diode circuits, MOSFETs, and analog signal processing.', 'https://covers.openlibrary.org/b/isbn/9780199339136-L.jpg', FALSE),
(9, '978-1260440232', 'Java: The Complete Reference', 'Herbert Schildt', 'McGraw-Hill Education', '12th Edition', 2021, 'Programming', 'IT', 'Authoritative guide to Java programming syntax, OOP principles, collections framework, concurrency utilities, and I/O streams.', 'https://covers.openlibrary.org/b/isbn/9781260440232-L.jpg', FALSE),
(10, '978-0134685991', 'Effective Java', 'Joshua Bloch', 'Addison-Wesley Professional', '3rd Edition', 2018, 'Programming', 'CSE', 'Industry standard manual of best programming practices in the Java programming language.', 'https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg', FALSE);

INSERT INTO book_copies (copy_code, book_id, status) VALUES
('PSG-BK1-C01', 1, 'AVAILABLE'),
('PSG-BK1-C02', 1, 'AVAILABLE'),
('PSG-BK1-C03', 1, 'AVAILABLE'),
('PSG-BK1-C04', 1, 'AVAILABLE'),

('PSG-BK2-C01', 2, 'AVAILABLE'),
('PSG-BK2-C02', 2, 'AVAILABLE'),
('PSG-BK2-C03', 2, 'AVAILABLE'),

('PSG-BK3-C01', 3, 'AVAILABLE'),
('PSG-BK3-C02', 3, 'AVAILABLE'),
('PSG-BK3-C03', 3, 'AVAILABLE'),

('PSG-BK4-C01', 4, 'AVAILABLE'),
('PSG-BK4-C02', 4, 'AVAILABLE'),
('PSG-BK4-C03', 4, 'AVAILABLE'),

('PSG-BK5-C01', 5, 'AVAILABLE'),
('PSG-BK5-C02', 5, 'AVAILABLE'),
('PSG-BK5-C03', 5, 'AVAILABLE'),

('PSG-BK6-C01', 6, 'AVAILABLE'),
('PSG-BK6-C02', 6, 'AVAILABLE'),
('PSG-BK6-C03', 6, 'AVAILABLE'),

('PSG-BK7-C01', 7, 'AVAILABLE'),
('PSG-BK7-C02', 7, 'AVAILABLE'),

('PSG-BK8-C01', 8, 'AVAILABLE'),
('PSG-BK8-C02', 8, 'AVAILABLE'),

('PSG-BK9-C01', 9, 'AVAILABLE'),
('PSG-BK9-C02', 9, 'AVAILABLE'),
('PSG-BK9-C03', 9, 'AVAILABLE'),

('PSG-BK10-C01', 10, 'AVAILABLE'),
('PSG-BK10-C02', 10, 'AVAILABLE');

INSERT INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(1, 4, 1, 1, '2026-10-25', '2026-11-08', NULL, 'ACTIVE', 2, NULL);

UPDATE book_copies SET status = 'BORROWED' WHERE copy_id = 1;

INSERT INTO borrow_transactions 
(transaction_id, student_id, book_id, copy_id, issue_date, due_date, return_date, status, issued_by, returned_by)
VALUES 
(2, 5, 2, 5, '2026-10-01', '2026-10-15', '2026-10-14', 'RETURNED', 2, 2);

INSERT INTO students (permanent_roll_number, full_name, account_status, registration_status) VALUES
('715525104189', 'SANJUNIVASINI S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104190', 'SANTHOSH V', 'ACTIVE', 'NOT_REGISTERED'),
('715525104191', 'SARVESH P', 'ACTIVE', 'NOT_REGISTERED'),
('715525104192', 'SARVIN S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104193', 'SATHVIKA D V', 'ACTIVE', 'NOT_REGISTERED'),
('715525104194', 'SATHYAPRIYA S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104195', 'SELVAKUMAR D', 'ACTIVE', 'NOT_REGISTERED'),
('715525104196', 'SHARVESH MAHI A', 'ACTIVE', 'NOT_REGISTERED'),
('715525104197', 'SHREE LAKSHAN S B', 'ACTIVE', 'NOT_REGISTERED'),
('715525104198', 'SHREESHANTH S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104199', 'SIVAKATHIR S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104200', 'SIVAPRAKASH M', 'ACTIVE', 'NOT_REGISTERED'),
('715525104201', 'SONA V', 'ACTIVE', 'NOT_REGISTERED'),
('715525104202', 'SOWMIYA S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104203', 'SREESAMPREETA A B', 'ACTIVE', 'NOT_REGISTERED'),
('715525104204', 'SRIMANIKANDAN S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104205', 'SRINIDHI A', 'ACTIVE', 'NOT_REGISTERED'),
('715525104206', 'SRINIDHI A S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104207', 'SRINIVAS RAMACHANDRAN', 'ACTIVE', 'NOT_REGISTERED'),
('715525104208', 'SRI PRASATH K M', 'ACTIVE', 'NOT_REGISTERED'),
('715525104209', 'SRIRAM R', 'ACTIVE', 'NOT_REGISTERED'),
('715525104210', 'SRI SARAN KARTHI M S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104211', 'SRI SARANYA T', 'ACTIVE', 'NOT_REGISTERED'),
('715525104212', 'SRI SUBBU LAKSHMI S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104213', 'SRRI VARSHAN M R', 'ACTIVE', 'NOT_REGISTERED'),
('715525104214', 'SRUTHI S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104215', 'SUBANANDHINI V', 'ACTIVE', 'NOT_REGISTERED'),
('715525104216', 'SUBRAMANIAN B', 'ACTIVE', 'NOT_REGISTERED'),
('715525104217', 'SUDHARSAN G', 'ACTIVE', 'NOT_REGISTERED'),
('715525104218', 'SUJITH A K', 'ACTIVE', 'NOT_REGISTERED'),
('715525104219', 'SURENDHAR P', 'ACTIVE', 'NOT_REGISTERED'),
('715525104220', 'SWETHA SIVABALAKRISHNAN', 'ACTIVE', 'NOT_REGISTERED'),
('715525104221', 'TANISHKA S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104222', 'THANGADHURAI T', 'ACTIVE', 'NOT_REGISTERED'),
('715525104223', 'THARIKA T', 'ACTIVE', 'NOT_REGISTERED'),
('715525104224', 'THAYAANITHI M', 'ACTIVE', 'NOT_REGISTERED'),
('715525104225', 'THIRUSELVAN R K', 'ACTIVE', 'NOT_REGISTERED'),
('715525104226', 'TIRATH SHAILENDRA S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104227', 'TSUNDUPALLI ARUN KUMAR', 'ACTIVE', 'NOT_REGISTERED'),
('715525104228', 'UGANDHAR S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104229', 'VAISHIKAA B A', 'ACTIVE', 'NOT_REGISTERED'),
('715525104230', 'VARSHA A R', 'ACTIVE', 'NOT_REGISTERED'),
('715525104231', 'VARSHINI B', 'ACTIVE', 'NOT_REGISTERED'),
('715525104232', 'VARSHINI M', 'ACTIVE', 'NOT_REGISTERED'),
('715525104233', 'VARSINI V', 'ACTIVE', 'NOT_REGISTERED'),
('715525104234', 'VEERAVVEL SUDHAN V', 'ACTIVE', 'NOT_REGISTERED'),
('715525104235', 'VIBEESH S S', 'ACTIVE', 'NOT_REGISTERED'),
('715525104236', 'VIJAY ADITHIYA E', 'ACTIVE', 'NOT_REGISTERED'),
('715525104237', 'VISHALINI A R', 'ACTIVE', 'NOT_REGISTERED'),
('715525104238', 'VISHAL M', 'ACTIVE', 'NOT_REGISTERED'),
('715525104239', 'VISHAL M', 'ACTIVE', 'NOT_REGISTERED'),
('715525104240', 'VISHAL M', 'ACTIVE', 'NOT_REGISTERED'),
('715525104241', 'VISHAL PRAKASH', 'ACTIVE', 'NOT_REGISTERED'),
('715525104242', 'VISHMITHA V', 'ACTIVE', 'NOT_REGISTERED'),
('715525104243', 'VISHWAM R', 'ACTIVE', 'NOT_REGISTERED'),
('715525104244', 'YADHUNANDHAN R L', 'ACTIVE', 'NOT_REGISTERED'),
('715525104245', 'YAMUNASRI A', 'ACTIVE', 'NOT_REGISTERED'),
('715525104246', 'YATISH T', 'ACTIVE', 'NOT_REGISTERED'),
('715525104247', 'YAZHINI M P', 'ACTIVE', 'NOT_REGISTERED'),
('715525104248', 'YUGAVARTHAN A', 'ACTIVE', 'NOT_REGISTERED'),
('715525104249', 'YUVANKARTHICK B', 'ACTIVE', 'NOT_REGISTERED'),
('715525104250', 'YUVANSHANKAR M', 'ACTIVE', 'NOT_REGISTERED'),
('715525104251', 'YUVASRI R', 'ACTIVE', 'NOT_REGISTERED');


