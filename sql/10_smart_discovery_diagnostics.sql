-- =============================================================================
-- PSG iTech Library - Feature 10: Smart Book Discovery & Recommendations
-- Diagnostics Script 10: Validates presence of performance indexes
-- Database: psgitech_library / psgitech_library_test (MySQL 8.0+ / InnoDB)
-- =============================================================================

SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX, NON_UNIQUE
FROM information_schema.statistics
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN (
    'idx_books_created_at',
    'idx_books_publisher',
    'idx_books_pub_year',
    'idx_borrow_book_issued',
    'idx_borrow_student_issued'
  )
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

