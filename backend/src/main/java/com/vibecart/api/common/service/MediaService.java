package com.vibecart.api.common.service;

import com.vibecart.api.common.dto.MediaUploadResponse;
import com.vibecart.api.common.dto.PresignedUrlResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

/**
 * Dịch vụ xử lý logic nghiệp vụ cho tệp tin media (hình ảnh, video).
 */
public interface MediaService {

    /**
     * Tải tệp tin đơn lẻ lên bộ lưu trữ (Server-side upload).
     */
    MediaUploadResponse uploadFile(MultipartFile file, String folder, String username);

    /**
     * Tải đồng thời nhiều tệp tin lên bộ lưu trữ (Batch upload).
     */
    List<MediaUploadResponse> uploadFiles(List<MultipartFile> files, String folder, String username);

    /**
     * Tạo Pre-signed URL cho phép Client tải trực tiếp tệp tin lên S3.
     */
    PresignedUrlResponse generatePresignedUrl(String contentType, long fileSize, String folder, String username);

    /**
     * Xác thực tệp tin và cập nhật trạng thái sau khi Client tải lên thành công qua Pre-signed URL.
     */
    void confirmUpload(String key, String username);

    /**
     * Kiểm tra quyền sở hữu và tiến hành xóa tệp tin media khỏi hệ thống.
     */
    void deleteFile(String key, String username, Collection<? extends GrantedAuthority> authorities);
}
