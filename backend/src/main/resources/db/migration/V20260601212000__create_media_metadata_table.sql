-- ===================================================================
-- Create Media Metadata Table to track S3 files ownership and details
-- Version: V20260601212000__create_media_metadata_table.sql
-- Description: Enable IDOR prevention on S3 file deletion
-- ===================================================================

CREATE TABLE media_metadata (
    id VARCHAR(36) PRIMARY KEY,
    s3_key VARCHAR(255) UNIQUE NOT NULL,
    uploaded_by VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) DEFAULT 'system',
    updated_by VARCHAR(50) DEFAULT 'system',
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_media_metadata_key ON media_metadata(s3_key);
CREATE INDEX idx_media_metadata_uploader ON media_metadata(uploaded_by);
