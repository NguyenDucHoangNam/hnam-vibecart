-- Add missing auditing and soft delete fields to categories to match BaseEntity
ALTER TABLE categories
ADD COLUMN created_by VARCHAR(50),
ADD COLUMN updated_by VARCHAR(50),
ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
