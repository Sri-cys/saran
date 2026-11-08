-- =============================================================================
-- PSG iTech Library - Feature 10: Smart Book Discovery & Recommendations
-- Migration Script 10: Performance Indexes for Search, Popularity, and Recommendations
-- Database: psgitech_library / psgitech_library_test (MySQL 8.0+ / InnoDB)
-- Safe, additive, idempotent execution with INFORMATION_SCHEMA checks
-- =============================================================================

-- 1. Index on books(created_at) for recently added sorting and recency scoring
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'books' AND index_name = 'idx_books_created_at');
SET @sql := IF(@idx_exists = 0, 
    'ALTER TABLE books ADD INDEX idx_books_created_at (created_at)', 
    'SELECT "Index idx_books_created_at already exists on books" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Index on books(publisher) for catalog filtering
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'books' AND index_name = 'idx_books_publisher');
SET @sql := IF(@idx_exists = 0, 
    'ALTER TABLE books ADD INDEX idx_books_publisher (publisher)', 
    'SELECT "Index idx_books_publisher already exists on books" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Index on books(publication_year) for catalog filtering
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'books' AND index_name = 'idx_books_pub_year');
SET @sql := IF(@idx_exists = 0, 
    'ALTER TABLE books ADD INDEX idx_books_pub_year (publication_year)', 
    'SELECT "Index idx_books_pub_year already exists on books" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. Compound Index on borrow_transactions(book_id, issue_date) for Popularity and Trending scoring
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND index_name = 'idx_borrow_book_issued');
SET @sql := IF(@idx_exists = 0, 
    'ALTER TABLE borrow_transactions ADD INDEX idx_borrow_book_issued (book_id, issue_date)', 
    'SELECT "Index idx_borrow_book_issued already exists on borrow_transactions" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. Compound Index on borrow_transactions(student_id, issue_date) for Personalized Reading History aggregation
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND index_name = 'idx_borrow_student_issued');
SET @sql := IF(@idx_exists = 0, 
    'ALTER TABLE borrow_transactions ADD INDEX idx_borrow_student_issued (student_id, issue_date)', 
    'SELECT "Index idx_borrow_student_issued already exists on borrow_transactions" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

