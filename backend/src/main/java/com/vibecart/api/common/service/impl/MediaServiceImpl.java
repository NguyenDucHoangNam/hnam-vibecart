package com.vibecart.api.common.service.impl;

import com.vibecart.api.common.dto.MediaUploadResponse;
import com.vibecart.api.common.dto.PresignedUrlResponse;
import com.vibecart.api.common.entity.MediaMetadata;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.common.repository.MediaMetadataRepository;
import com.vibecart.api.common.service.MediaService;
import com.vibecart.api.common.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final StorageService storageService;
    private final MediaMetadataRepository mediaMetadataRepository;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm");

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024;

    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 15;
    @Override
    public MediaUploadResponse uploadFile(MultipartFile file, String folder, String username) {
        validateFile(file);

        String key = generateKey(folder, file.getContentType());

        try {
            String url = storageService.uploadFile(
                    key,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType());

            MediaMetadata metadata = MediaMetadata.builder()
                    .s3Key(key)
                    .uploadedBy(username)
                    .fileSize(file.getSize())
                    .status("VERIFIED")
                    .build();
            mediaMetadataRepository.save(metadata);

            log.info("File uploaded by {}: {} ({} bytes)", username, key, file.getSize());

            return new MediaUploadResponse(
                    url, key, file.getContentType(), file.getSize(), ZonedDateTime.now());
        } catch (Exception e) {
            log.error("File upload failed for user {}: {}", username, e.getMessage(), e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }
    @Override
    public List<MediaUploadResponse> uploadFiles(List<MultipartFile> files, String folder, String username) {
        if (files.size() > 10) {
            throw new AppException(ErrorCode.MAX_MEDIA_EXCEEDED);
        }

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
            mediaMetadataRepository.saveAll(metadataList);
        } catch (Exception e) {
            log.error(
                    "Batch upload failed. Initiating fault-tolerant rollback to delete successfully uploaded files. Error: {}",
                    e.getMessage());
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
        return results;
    }
    @Override
    public PresignedUrlResponse generatePresignedUrl(String contentType, long fileSize, String folder,
            String username) {
        if (!isAllowedType(contentType)) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }

        if (ALLOWED_IMAGE_TYPES.contains(contentType) && fileSize > MAX_IMAGE_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        if (ALLOWED_VIDEO_TYPES.contains(contentType) && fileSize > MAX_VIDEO_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        if (fileSize <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        String key = generateKey(folder, contentType);

        String uploadUrl = storageService.generatePresignedUploadUrl(key, contentType, fileSize,
                PRESIGNED_URL_EXPIRATION_MINUTES);
        String publicUrl = storageService.getFileUrl(key);

        MediaMetadata metadata = MediaMetadata.builder()
                .s3Key(key)
                .uploadedBy(username)
                .fileSize(fileSize)
                .status("PENDING")
                .build();
        mediaMetadataRepository.save(metadata);

        log.info("Pre-signed URL generated for user {}: key={}, size={} bytes", username, key, fileSize);

        return new PresignedUrlResponse(uploadUrl, key, publicUrl, PRESIGNED_URL_EXPIRATION_MINUTES);
    }
    @Override
    public void deleteFile(String key, String username,
            Collection<? extends GrantedAuthority> authorities) {
        log.info("File delete requested by {}: {}", username, key);

        Optional<MediaMetadata> metadataOpt = mediaMetadataRepository.findByS3Key(key);
        if (metadataOpt.isPresent()) {
            MediaMetadata metadata = metadataOpt.get();

            boolean isAdmin = authorities.stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            if (!metadata.getUploadedBy().equals(username) && !isAdmin) {
                log.warn("Access denied: User {} tried to delete file owned by {}", username,
                        metadata.getUploadedBy());
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }

            storageService.deleteFile(key);
            mediaMetadataRepository.delete(metadata);
            log.info("Deleted S3 file and metadata key: {} by user: {}", key, username);
        } else {
            log.warn("Legacy file deletion requested (no DB metadata) for key: {} by user: {}", key, username);
            storageService.deleteFile(key);
        }
    }
    @Override
    public void confirmUpload(String key, String username) {
        log.info("Confirm upload requested for key: {} by user: {}", key, username);

        MediaMetadata metadata = mediaMetadataRepository.findByS3Key(key)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT));

        if (!metadata.getUploadedBy().equals(username)) {
            log.warn("Unauthorized confirmation: User {} tried to confirm key owned by {}", username,
                    metadata.getUploadedBy());
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        boolean isValid = storageService.verifyFile(key, metadata.getFileSize());
        if (!isValid) {
            log.warn("File verification failed for key: {} (declared size: {} bytes). Cleaning up zombie resources...",
                    key, metadata.getFileSize());

            try {
                storageService.deleteFile(key);
                log.info("Successfully cleaned up invalid S3 file for key: {}", key);
            } catch (Exception e) {
                log.error("Failed to delete invalid file on S3 for key: {}. Manual cleanup may be required.", key, e);
            }

            mediaMetadataRepository.delete(metadata);
            log.info("Deleted orphan PENDING metadata record for key: {}", key);

            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        metadata.setStatus("VERIFIED");
        mediaMetadataRepository.save(metadata);
        log.info("Successfully confirmed and verified upload for key: {}", key);
    }
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
}
