-- ============================================================
-- Banking Transaction System — Database Schema
-- ============================================================
-- This schema is designed for PostgreSQL.
-- It includes proper constraints, indexes, and version columns
-- for optimistic locking.
-- ============================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─────────────────────────────────────────────────────────────
-- ACCOUNTS TABLE
-- ─────────────────────────────────────────────────────────────
-- Stores bank account information.
-- The `version` column is used by Hibernate for optimistic locking.
-- The CHECK constraint on balance prevents negative balances at the DB level.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS accounts (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_number  VARCHAR(20)     NOT NULL,
    holder_name     VARCHAR(100)    NOT NULL,
    balance         DECIMAL(19, 4)  NOT NULL DEFAULT 0.0000,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status          VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

-- ─────────────────────────────────────────────────────────────
-- TRANSACTIONS TABLE
-- ─────────────────────────────────────────────────────────────
-- Records every financial operation.
-- source_account_id is NULL for deposits (money comes from outside).
-- target_account_id is NULL for withdrawals (money goes outside).
-- idempotency_key ensures the same operation is never processed twice.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transactions (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    type                VARCHAR(20)     NOT NULL,
    amount              DECIMAL(19, 4)  NOT NULL,
    source_account_id   UUID,
    target_account_id   UUID,
    status              VARCHAR(10)     NOT NULL DEFAULT 'SUCCESS',
    idempotency_key     VARCHAR(100),
    description         VARCHAR(500),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('SUCCESS', 'FAILED', 'PENDING')),
    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key),

    -- Foreign Keys
    CONSTRAINT fk_transactions_source_account
        FOREIGN KEY (source_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_transactions_target_account
        FOREIGN KEY (target_account_id) REFERENCES accounts(id)
);

-- ─────────────────────────────────────────────────────────────
-- IDEMPOTENCY KEYS TABLE
-- ─────────────────────────────────────────────────────────────
-- Stores previously processed request/response pairs.
-- When a duplicate request arrives (same idempotency key),
-- the cached response is returned without re-processing.
-- Records expire after a configurable TTL (default: 24 hours).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    idempotency_key     VARCHAR(100)    NOT NULL,
    request_hash        VARCHAR(64)     NOT NULL,
    response_body       TEXT            NOT NULL,
    status_code         INT             NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMP       NOT NULL,

    -- Constraints
    CONSTRAINT uq_idempotency_keys_key UNIQUE (idempotency_key)
);

-- ─────────────────────────────────────────────────────────────
-- INDEXES
-- ─────────────────────────────────────────────────────────────
-- These indexes optimize the most common query patterns:
-- 1. Looking up accounts by account number
-- 2. Fetching transaction history for an account
-- 3. Checking idempotency keys
-- 4. Cleaning up expired idempotency records
-- ─────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_accounts_account_number
    ON accounts(account_number);

CREATE INDEX IF NOT EXISTS idx_accounts_status
    ON accounts(status);

CREATE INDEX IF NOT EXISTS idx_transactions_source_account_id
    ON transactions(source_account_id);

CREATE INDEX IF NOT EXISTS idx_transactions_target_account_id
    ON transactions(target_account_id);

CREATE INDEX IF NOT EXISTS idx_transactions_idempotency_key
    ON transactions(idempotency_key);

CREATE INDEX IF NOT EXISTS idx_transactions_created_at
    ON transactions(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_key
    ON idempotency_keys(idempotency_key);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at
    ON idempotency_keys(expires_at);
