-- ============================================================
-- Sample Seed Data
-- ============================================================
-- These accounts are created for testing and demonstration.
-- The ON CONFLICT clause ensures re-running this script is safe.
-- ============================================================

INSERT INTO accounts (id, account_number, holder_name, balance, currency, status, version, created_at, updated_at)
VALUES
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'ACC-100001', 'Alice Johnson', 5000.0000, 'USD', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b2c3d4e5-f6a7-8901-bcde-f12345678901', 'ACC-100002', 'Bob Smith',     3000.0000, 'USD', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c3d4e5f6-a7b8-9012-cdef-123456789012', 'ACC-100003', 'Charlie Brown', 1500.0000, 'USD', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (account_number) DO NOTHING;
