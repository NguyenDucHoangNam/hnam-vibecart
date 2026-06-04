-- Add missing auditing and soft delete fields to outbox_events to match BaseEntity
ALTER TABLE outbox_events 
ADD COLUMN created_by VARCHAR(50),
ADD COLUMN updated_by VARCHAR(50),
ADD COLUMN deleted BOOLEAN DEFAULT FALSE NOT NULL,
ADD COLUMN deleted_at TIMESTAMP;
