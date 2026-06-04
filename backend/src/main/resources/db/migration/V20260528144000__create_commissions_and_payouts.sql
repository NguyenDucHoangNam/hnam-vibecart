-- ===================================================================
-- VibeCart Shortlink & Affiliate Module Migrations: Commissions & Payouts
-- Version: V20260528144000__create_commissions_and_payouts.sql
-- Description: Create commissions and payout_requests tables for financial tracking.
-- ===================================================================

CREATE TABLE commissions (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    creator_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subtotal_amount DECIMAL(12, 2) NOT NULL,
    commission_rate DECIMAL(4, 2) NOT NULL, -- e.g. 10.00 represents 10%
    commission_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(30) DEFAULT 'PENDING' NOT NULL, -- PENDING, APPROVED, REJECTED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system',
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE payout_requests (
    id VARCHAR(36) PRIMARY KEY,
    creator_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount DECIMAL(12, 2) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    bank_account_number VARCHAR(50) NOT NULL,
    bank_account_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) DEFAULT 'PENDING' NOT NULL, -- PENDING, APPROVED, REJECTED
    admin_note VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system',
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Optimization indexes
CREATE INDEX idx_commissions_creator_status ON commissions(creator_id, status);
CREATE INDEX idx_commissions_order ON commissions(order_id);
CREATE INDEX idx_payout_requests_creator ON payout_requests(creator_id);
CREATE INDEX idx_payout_requests_status ON payout_requests(status);
