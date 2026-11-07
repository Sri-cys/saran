-- =============================================================================
-- PSG iTech Library - Feature 4: Reservation Queue Schema Rollback
-- DEVELOPMENT ONLY: Schema Rollback Helper
-- Warning: Drops the reservations table. Does not perform application deletions.
-- =============================================================================

USE psgitech_library;

DROP TABLE IF EXISTS reservations;

