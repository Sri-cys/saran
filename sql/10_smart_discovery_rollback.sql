-- =============================================================================
-- PSG iTech Library - Feature 10: Smart Book Discovery & Recommendations
-- Rollback Script 10: Drops only indexes created in migration 10
-- Database: psgitech_library / psgitech_library_test (MySQL 8.0+ / InnoDB)
-- Safe, idempotent execution with INFORMATION_SCHEMA checks
-- =============================================================================

-- 1. Drop idx_books_created_at
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'books' AND index_name = 'idx_books_created_at');
SET @sql := IF(@idx_exists > 0, 
    'ALTER TABLE books DROP INDEX idx_books_created_at', 
    'SELECT "Index idx_books_created_at does not exist on books" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Drop idx_books_publisher
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'books' AND index_name = 'idx_books_publisher');
SET @sql := IF(@idx_exists > 0, 
    'ALTER TABLE books DROP INDEX idx_books_publisher', 
    'SELECT "Index idx_books_publisher does not exist on books" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Drop idx_books_pub_year
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'books' AND index_name = 'idx_books_pub_year');
SET @sql := IF(@idx_exists > 0, 
    'ALTER TABLE books DROP INDEX idx_books_pub_year', 
    'SELECT "Index idx_books_pub_year does not exist on books" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. Drop idx_borrow_book_issued
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND index_name = 'idx_borrow_book_issued');
SET @sql := IF(@idx_exists > 0, 
    'ALTER TABLE borrow_transactions DROP INDEX idx_borrow_book_issued', 
    'SELECT "Index idx_borrow_book_issued does not exist on borrow_transactions" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. Drop idx_borrow_student_issued
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                    WHERE table_schema = DATABASE() AND table_name = 'borrow_transactions' AND index_name = 'idx_borrow_student_issued');
SET @sql := IF(@idx_exists > 0, 
    'ALTER TABLE borrow_transactions DROP INDEX idx_borrow_student_issued', 
    'SELECT "Index idx_borrow_student_issued does not exist on borrow_transactions" AS notice');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

