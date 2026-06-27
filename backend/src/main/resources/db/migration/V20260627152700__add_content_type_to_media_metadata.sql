-- ===================================================================
-- Add content_type column to media_metadata table
-- Version: V20260627152700__add_content_type_to_media_metadata.sql
-- Description: Track uploaded file content type for verification
-- ===================================================================

ALTER TABLE media_metadata ADD COLUMN content_type VARCHAR(100);
