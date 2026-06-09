package com.vibecart.api.common.service.impl;

import com.vibecart.api.config.StorageProperties;
import com.vibecart.api.common.service.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

/**
 * Dịch vụ lưu trữ tệp tin tương tác trực tiếp với AWS S3 hoặc MinIO.
 */
@Service
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner, StorageProperties storageProperties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.storageProperties = storageProperties;
    }

    /**
     * Kiểm tra tính khả dụng của S3 Bucket khi ứng dụng khởi động.
     */
    @PostConstruct
    public void init() {
        String bucketName = storageProperties.getBucketName();
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            log.info("S3 storage bucket '{}' verified successfully.", bucketName);
        } catch (S3Exception e) {
            log.error("S3 storage bucket '{}' verification failed (Status {}). Please ensure the bucket exists and credentials are correct: {}", 
                    bucketName, e.statusCode(), e.getMessage());
        } catch (Exception e) {
            log.error("S3 storage bucket '{}' verification failed: {}", bucketName, e.getMessage());
        }
    }

    /**
     * Tải tệp tin lên S3.
     */
    @Override
    public String uploadFile(String key, InputStream content, long contentLength, String contentType) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(content, contentLength));
            log.info("Successfully uploaded file '{}' to S3.", key);
            return getFileUrl(key);
        } catch (Exception e) {
            log.error("Failed to upload file '{}' to S3: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to storage: " + e.getMessage(), e);
        }
    }

    /**
     * Xóa tệp tin khỏi S3.
     */
    @Override
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted file '{}' from S3.", key);
        } catch (Exception e) {
            log.error("Failed to delete file '{}' from S3: {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to delete file from storage: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo Pre-signed URL phục vụ Client tự upload lên S3.
     */
    @Override
    public String generatePresignedUploadUrl(String key, String contentType, long contentLength, int expirationMinutes) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            String presignedUrl = presignedRequest.url().toString();
            log.info("Generated pre-signed upload URL for key '{}' with size '{}' (valid for {} minutes).", key, contentLength, expirationMinutes);
            return presignedUrl;
        } catch (Exception e) {
            log.error("Failed to generate pre-signed upload URL for key '{}': {}", key, e.getMessage(), e);
            throw new RuntimeException("Failed to generate upload URL: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy URL tĩnh công khai của tệp tin trên S3.
     */
    @Override
    public String getFileUrl(String key) {
        String publicUrlPrefix = storageProperties.getPublicUrlPrefix();
        if (publicUrlPrefix != null && !publicUrlPrefix.isEmpty()) {
            String prefix = publicUrlPrefix.endsWith("/") ? publicUrlPrefix.substring(0, publicUrlPrefix.length() - 1) : publicUrlPrefix;
            return String.format("%s/%s", prefix, key);
        }

        String endpoint = storageProperties.getEndpoint();
        String bucket = storageProperties.getBucketName();
        
        if (endpoint != null && !endpoint.isEmpty()) {
            return String.format("%s/%s/%s", endpoint, bucket, key);
        }
        
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, storageProperties.getRegion(), key);
    }

    /**
     * Kiểm tra tệp tin có tồn tại và khớp kích thước trên S3 không.
     */
    @Override
    public boolean verifyFile(String key, long expectedSize) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();
            HeadObjectResponse headObjectResponse = s3Client.headObject(headObjectRequest);
            return headObjectResponse.contentLength() == expectedSize;
        } catch (NoSuchKeyException e) {
            log.warn("File '{}' not found in S3.", key);
            return false;
        } catch (Exception e) {
            log.error("Failed to verify file '{}' in S3: {}", key, e.getMessage(), e);
            return false;
        }
    }
}
