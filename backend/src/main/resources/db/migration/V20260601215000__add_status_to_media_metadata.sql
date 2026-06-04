-- ===================================================================
-- Add status column to media_metadata table for Upload Confirmation Workflow
-- Version: V20260601215000__add_status_to_media_metadata.sql
-- Description: Track PENDING vs VERIFIED status of uploaded S3 media files
-- ===================================================================

ALTER TABLE media_metadata ADD COLUMN status VARCHAR(20) DEFAULT 'PENDING' NOT NULL;
