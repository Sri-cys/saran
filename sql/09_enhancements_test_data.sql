-- =============================================================================
-- PSG iTech Library - Enhancements of Features 1-8
-- Migration 09: Test Data Seed Script
-- Database: psgitech_library_test (MySQL 8.0+ / InnoDB)
-- All entities clearly prefixed with 'TEST-ENH' or '@example.test'
-- =============================================================================

-- 1. Insert Test Users for Feature Enhancements
INSERT INTO users (name, email, password_hash, phone, role, register_number, department, `year`, active, email_verified, email_verified_at)
VALUES 
('TEST Student Verified', 'test.enh.verified@example.test', '$2a$10$wE1Vp2bH5R9U.9v3i7uB4e1qO2s9X9h3j4k5l6m7n8o9p0q1r2s3t', '9876543201', 'STUDENT', 'TEST-REG-ENH01', 'CSE', 3, TRUE, TRUE, CURRENT_TIMESTAMP),
('TEST Student Unverified', 'test.enh.unverified@example.test', '$2a$10$wE1Vp2bH5R9U.9v3i7uB4e1qO2s9X9h3j4k5l6m7n8o9p0q1r2s3t', '9876543202', 'STUDENT', 'TEST-REG-ENH02', 'CSE', 3, TRUE, FALSE, NULL),
('TEST Student Renewal 1', 'test.enh.renewal@example.test', '$2a$10$wE1Vp2bH5R9U.9v3i7uB4e1qO2s9X9h3j4k5l6m7n8o9p0q1r2s3t', '9876543203', 'STUDENT', 'TEST-REG-ENH03', 'CSE', 3, TRUE, TRUE, CURRENT_TIMESTAMP),
('TEST Student Handover A', 'test.enh.handover.a@example.test', '$2a$10$wE1Vp2bH5R9U.9v3i7uB4e1qO2s9X9h3j4k5l6m7n8o9p0q1r2s3t', '9876543204', 'STUDENT', 'TEST-REG-ENH04', 'CSE', 3, TRUE, TRUE, CURRENT_TIMESTAMP),
('TEST Student Handover B', 'test.enh.handover.b@example.test', '$2a$10$wE1Vp2bH5R9U.9v3i7uB4e1qO2s9X9h3j4k5l6m7n8o9p0q1r2s3t', '9876543205', 'STUDENT', 'TEST-REG-ENH05', 'CSE', 3, TRUE, TRUE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2. Insert Test Books for Feature Enhancements
INSERT INTO books (isbn, title, author, publisher, publication_year, category, department, is_archived)
VALUES 
('978-0-TEST-ENH-01', 'TEST Book for Renewals', 'TEST Author Renewal', 'TEST Press', 2026, 'Engineering', 'CSE', FALSE),
('978-0-TEST-ENH-02', 'TEST Book for Barcodes', 'TEST Author Barcode', 'TEST Press', 2026, 'Engineering', 'CSE', FALSE),
('978-0-TEST-ENH-03', 'TEST Book for Handover OTP', 'TEST Author Handover', 'TEST Press', 2026, 'Engineering', 'CSE', FALSE)
ON DUPLICATE KEY UPDATE title = VALUES(title);
