-- The immutable 007 file is restored to its published SHA-256. Keep the
-- later security decision as an additive migration, not a rewritten baseline.
-- This does not enable AI or alter patient/clinical data.
ALTER TABLE rehab_ai_config ALTER COLUMN enable_plan_draft SET DEFAULT b'0';
UPDATE rehab_ai_config SET enable_plan_draft=b'0' WHERE enable_plan_draft<>b'0';
