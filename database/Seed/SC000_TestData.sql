-- =====================================================================
-- SEED: fake sample data for testing only.
-- Run AFTER all migrations. Never put real student data here.
-- Changed this file? Rebuild cleanly with:  bash database/setup.sh --reset
-- (INSERT IGNORE skips rows that already exist, so edits to old rows are not applied.)
-- =====================================================================

USE adaptive_reading;

INSERT IGNORE INTO accounts (id, username, display_name) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'demo_student', 'Demo Student');

INSERT IGNORE INTO students (id, display_name) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', 'Demo Student');

-- No credential is seeded. Save slots are created by the game or by the Java test files.

-- ---------- PROGRESS ----------
-- Nothing seeded: save slots are created by the game (or by the Java test files).