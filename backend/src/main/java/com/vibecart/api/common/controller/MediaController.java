package com.vibecart.api.common.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.dto.MediaUploadResponse;
import com.vibecart.api.common.dto.PresignedUrlResponse;
import com.vibecart.api.common.service.MediaService;
import com.vibecart.api.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {

        private final MediaService mediaService;
        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<MediaUploadResponse>> uploadFile(
                        @RequestParam("file") MultipartFile file,
                        @RequestParam(value = "folder", defaultValue = "general") String folder) {

                MediaUploadResponse response = mediaService.uploadFile(file, folder, SecurityUtils.getCurrentUsername());

                return ResponseEntity.status(HttpStatus.CREATED).body(
                                ApiResponse.<MediaUploadResponse>builder()
                                                .code(1000)
                                                .message("Tải lên file thành công")
                                                .result(response)
                                                .build());
        }
        @Deprecated(since = "2026-06-27", forRemoval = true)
        @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<List<MediaUploadResponse>>> uploadFiles(
                        @RequestParam("files") List<MultipartFile> files,
                        @RequestParam(value = "folder", defaultValue = "general") String folder) {

                List<MediaUploadResponse> results = mediaService.uploadFiles(files, folder, SecurityUtils.getCurrentUsername());

                return ResponseEntity.status(HttpStatus.CREATED).body(
                                ApiResponse.<List<MediaUploadResponse>>builder()
                                                .code(1000)
                                                .message("Tải lên " + files.size() + " file thành công")
                                                .result(results)
                                                .build());
        }
        @PostMapping("/presigned-url")
        public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
                        @RequestParam("fileName") String fileName,
                        @RequestParam("contentType") String contentType,
                        @RequestParam("fileSize") long fileSize,
                        @RequestParam(value = "folder", defaultValue = "general") String folder) {

                PresignedUrlResponse response = mediaService.generatePresignedUrl(
                                contentType, fileSize, folder, SecurityUtils.getCurrentUsername());

                return ResponseEntity.ok(
                                ApiResponse.<PresignedUrlResponse>builder()
                                                .code(1000)
                                                .message("Tạo pre-signed URL thành công")
                                                .result(response)
                                                .build());
        }
        @DeleteMapping
        public ResponseEntity<ApiResponse<Void>> deleteFile(@RequestParam("key") String key) {
                mediaService.deleteFile(
                                key,
                                SecurityUtils.getCurrentUsername(),
                                SecurityContextHolder.getContext().getAuthentication().getAuthorities());

                return ResponseEntity.ok(
                                ApiResponse.<Void>builder()
                                                .code(1000)
                                                .message("Đã xóa file thành công")
                                                .build());
        }
        @PostMapping("/confirm")
        public ResponseEntity<ApiResponse<Void>> confirmUpload(@RequestParam("key") String key) {
                mediaService.confirmUpload(key, SecurityUtils.getCurrentUsername());

                return ResponseEntity.ok(
                                ApiResponse.<Void>builder()
                                                .code(1000)
                                                .message("Xác nhận tải lên thành công")
                                                .build());
        }
}
