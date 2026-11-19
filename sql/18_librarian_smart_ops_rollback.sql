-- ============================================================================
-- PSG iTech Library - Rollback 18: Librarian Smart Desk Operations
-- ============================================================================

DROP TABLE IF EXISTS goodwill_extensions;
DROP TABLE IF EXISTS library_layout;
DELETE FROM config_settings WHERE setting_key IN (
    'high_demand_queue_threshold',
    'copy_shortage_ratio',
    'goodwill_extension_days',
    'trust_exemplary_min_returns',
    'trust_max_overdue_days_for_exemplary'
);
