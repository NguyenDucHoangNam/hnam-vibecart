-- Migration for Transactional Outbox Pattern in Search Module
CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL, -- e.g. 'PRODUCT'
    aggregate_id VARCHAR(36) NOT NULL, -- UUID of product
    event_type VARCHAR(50) NOT NULL, -- 'PRODUCT_CREATED', 'PRODUCT_UPDATED', 'PRODUCT_DELETED'
    payload TEXT NOT NULL, -- JSON payload of the sync event
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL, -- PENDING, PROCESSED, FAILED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Optimize polling query performance for pending events
CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at);
