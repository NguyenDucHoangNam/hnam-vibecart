package com.vibecart.api.common.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.dto.MediaUploadResponse;
import com.vibecart.api.common.dto.PresignedUrlResponse;
import com.vibecart.api.common.entity.MediaMetadata;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.common.repository.MediaMetadataRepository;
import com.vibecart.api.common.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Optional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controller chung cho upload/quản lý media files lên S3/MinIO.
 *
 * <p>
 * Hỗ trợ 2 chiến lược upload:
 * </p>
 * Server-side upload: Frontend gửi file → Backend upload lên S3 → trả URL.</li>
 * Pre-signed URL: Backend trả pre-signed URL → Frontend upload trực tiếp lên
 * S3,
 * sau đó gọi {@code /confirm} để xác thực.</li>
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {

    private final StorageService storageService;
    private final MediaMetadataRepository mediaMetadataRepository;

    // Allowed MIME types
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm");

    // Max file sizes
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024; // 50 MB

    // Pre-signed URL expiration
    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 15;

    // ==================== 1. UPLOAD FILE (SERVER-SIDE) ====================
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {

        String username = getCurrentUsername();

        // Validate file
        validateFile(file);

        // Generate unique key: {folder}/{year}/{month}/{uuid}.{ext} based on content
        // type
        String key = generateKey(folder, file.getContentType());

        try {
            String url = storageService.uploadFile(
                    key,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType());

            // Save metadata to track ownership and enforce IDOR check
            MediaMetadata metadata = MediaMetadata.builder()
                    .s3Key(key)
                    .uploadedBy(username)
                    .fileSize(file.getSize())
                    .status("VERIFIED")
                    .build();
            mediaMetadataRepository.save(metadata);

            MediaUploadResponse response = new MediaUploadResponse(
                    url, key, file.getContentType(), file.getSize(), ZonedDateTime.now());

            log.info("File uploaded by {}: {} ({} bytes)", username, key, file.getSize());

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<MediaUploadResponse>builder()
                            .code(1000)
                            .message("Tải lên file thành công")
                            .result(response)
                            .build());
        } catch (Exception e) {
            log.error("File upload failed for user {}: {}", username, e.getMessage(), e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    // ==================== 2. UPLOAD NHIỀU FILES ====================
    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<MediaUploadResponse>>> uploadFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {

        String username = getCurrentUsername();

        if (files.size() > 10) {
            throw new AppException(ErrorCode.MAX_MEDIA_EXCEEDED);
        }

        // Validate all files first before attempting upload
        for (MultipartFile file : files) {
            validateFile(file);
        }

        List<MediaUploadResponse> results = new ArrayList<>();
        List<String> uploadedKeys = new ArrayList<>();
        List<MediaMetadata> metadataList = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                String key = generateKey(folder, file.getContentType());
                String url = storageService.uploadFile(
                        key, file.getInputStream(), file.getSize(), file.getContentType());
                uploadedKeys.add(key);

                metadataList.add(MediaMetadata.builder()
                        .s3Key(key)
                        .uploadedBy(username)
                        .fileSize(file.getSize())
                        .status("VERIFIED")
                        .build());

                results.add(new MediaUploadResponse(
                        url, key, file.getContentType(), file.getSize(), ZonedDateTime.now()));
            }
            // Save all metadata only after all uploads in batch are successful
            mediaMetadataRepository.saveAll(metadataList);
        } catch (Exception e) {
            log.error(
                    "Batch upload failed. Initiating fault-tolerant rollback to delete successfully uploaded files. Error: {}",
                    e.getMessage());
            // Rollback successfully uploaded S3 files with individual try-catch blocks
            // (Fault Tolerance)
            for (String key : uploadedKeys) {
                try {
                    storageService.deleteFile(key);
                    log.info("Rolled back: Deleted file '{}' from S3.", key);
                } catch (Exception rollbackEx) {
                    log.error("CRITICAL: Failed to rollback/delete zombie file '{}' on S3: {}", key,
                            rollbackEx.getMessage());
                }
            }
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        log.info("Batch upload by {}: {} files to folder {}", username, files.size(), folder);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<List<MediaUploadResponse>>builder()
                        .code(1000)
                        .message("Tải lên " + files.size() + " file thành công")
                        .result(results)
                        .build());
    }

    // ==================== 3. PRE-SIGNED URL (CLIENT-SIDE UPLOAD)
    // ====================
    @PostMapping("/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @RequestParam("fileName") String fileName,
            @RequestParam("contentType") String contentType,
            @RequestParam("fileSize") long fileSize,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {

        String username = getCurrentUsername();

        // Validate content type
        if (!isAllowedType(contentType)) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }

        // Validate file size limit on server
        if (ALLOWED_IMAGE_TYPES.contains(contentType) && fileSize > MAX_IMAGE_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        if (ALLOWED_VIDEO_TYPES.contains(contentType) && fileSize > MAX_VIDEO_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        if (fileSize <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        // Generate unique key based on Content-Type (No extension spoofing)
        String key = generateKey(folder, contentType);

        // Generate pre-signed PUT upload URL with contentLength enforced on S3
        String uploadUrl = storageService.generatePresignedUploadUrl(key, contentType, fileSize,
                PRESIGNED_URL_EXPIRATION_MINUTES);
        String publicUrl = storageService.getFileUrl(key);

        // Save metadata to DB
        MediaMetadata metadata = MediaMetadata.builder()
                .s3Key(key)
                .uploadedBy(username)
                .fileSize(fileSize)
                .status("PENDING")
                .build();
        mediaMetadataRepository.save(metadata);

        log.info("Pre-signed URL generated for user {}: key={}, size={} bytes", username, key, fileSize);

        PresignedUrlResponse response = new PresignedUrlResponse(
                uploadUrl, key, publicUrl, PRESIGNED_URL_EXPIRATION_MINUTES);

        return ResponseEntity.ok(
                ApiResponse.<PresignedUrlResponse>builder()
                        .code(1000)
                        .message("Tạo pre-signed URL thành công")
                        .result(response)
                        .build());
    }

    // ==================== 4. XÓA FILE ====================
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteFile(@RequestParam("key") String key) {
        String username = getCurrentUsername();
        log.info("File delete requested by {}: {}", username, key);

        // Retrieve metadata from DB to check ownership (IDOR Protection)
        Optional<MediaMetadata> metadataOpt = mediaMetadataRepository.findByS3Key(key);
        if (metadataOpt.isPresent()) {
            MediaMetadata metadata = metadataOpt.get();

            // Check if user is the owner or has ROLE_ADMIN role
            boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                    .stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            if (!metadata.getUploadedBy().equals(username) && !isAdmin) {
                log.warn("Access denied: User {} tried to delete file owned by {}", username, metadata.getUploadedBy());
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }

            // Delete from S3
            storageService.deleteFile(key);

            // Delete metadata from DB
            mediaMetadataRepository.delete(metadata);
            log.info("Deleted S3 file and metadata key: {} by user: {}", key, username);
        } else {
            // Graceful fallback for legacy files: log warning and let it delete
            log.warn("Legacy file deletion requested (no DB metadata) for key: {} by user: {}", key, username);
            storageService.deleteFile(key);
        }

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã xóa file thành công")
                        .build());
    }

    // ==================== 5. XÁC NHẬN FILE UPLOAD XONG (ACTIVE CLEANUP)
    // ====================
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmUpload(@RequestParam("key") String key) {
        String username = getCurrentUsername();
        log.info("Confirm upload requested for key: {} by user: {}", key, username);

        MediaMetadata metadata = mediaMetadataRepository.findByS3Key(key)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT));

        // Check ownership (IDOR Protection)
        if (!metadata.getUploadedBy().equals(username)) {
            log.warn("Unauthorized confirmation: User {} tried to confirm key owned by {}", username,
                    metadata.getUploadedBy());
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Verify tệp tin thực tế trên S3 bằng StorageService
        boolean isValid = storageService.verifyFile(key, metadata.getFileSize());
        if (!isValid) {
            log.warn("File verification failed for key: {} (declared size: {} bytes). Cleaning up zombie resources...",
                    key, metadata.getFileSize());

            // Active Cleanup: Xóa file rác trên S3
            try {
                storageService.deleteFile(key);
                log.info("Successfully cleaned up invalid S3 file for key: {}", key);
            } catch (Exception e) {
                log.error("Failed to delete invalid file on S3 for key: {}. Manual cleanup may be required.", key, e);
            }

            // Active Cleanup: Xóa bản ghi PENDING mồ côi trong DB
            mediaMetadataRepository.delete(metadata);
            log.info("Deleted orphan PENDING metadata record for key: {}", key);

            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        // Cập nhật trạng thái sang VERIFIED
        metadata.setStatus("VERIFIED");
        mediaMetadataRepository.save(metadata);
        log.info("Successfully confirmed and verified upload for key: {}", key);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Xác nhận tải lên thành công")
                        .build());
    }

    // ==================== HELPER METHODS ====================

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        String contentType = file.getContentType();
        if (!isAllowedType(contentType)) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }

        if (ALLOWED_IMAGE_TYPES.contains(contentType) && file.getSize() > MAX_IMAGE_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        if (ALLOWED_VIDEO_TYPES.contains(contentType) && file.getSize() > MAX_VIDEO_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private boolean isAllowedType(String contentType) {
        return contentType != null
                && (ALLOWED_IMAGE_TYPES.contains(contentType) || ALLOWED_VIDEO_TYPES.contains(contentType));
    }

    /**
     * Tạo unique S3 key: {folder}/{yyyy}/{MM}/{uuid}.{extension}
     * Đuôi file dựa hoàn toàn vào Content-Type để tránh Spoofing
     */
    private String generateKey(String folder, String contentType) {
        String extension = getExtension(contentType);
        ZonedDateTime now = ZonedDateTime.now();
        return String.format("%s/%d/%02d/%s.%s",
                folder,
                now.getYear(),
                now.getMonthValue(),
                UUID.randomUUID(),
                extension);
    }

    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            default -> "bin";
        };
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
