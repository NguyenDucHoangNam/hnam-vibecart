package com.vibecart.api.common.service;

import java.io.InputStream;

/**
 * Interface định nghĩa các thao tác lưu trữ tệp tin cơ bản.
 */
public interface StorageService {

    /**
     * Tải tệp tin lên máy chủ lưu trữ.
     */
    String uploadFile(String key, InputStream content, long contentLength, String contentType);

    /**
     * Xóa tệp tin khỏi máy chủ lưu trữ.
     */
    void deleteFile(String key);

    /**
     * Tạo Pre-signed URL để Client upload tệp tin trực tiếp.
     */
    String generatePresignedUploadUrl(String key, String contentType, long contentLength, int expirationMinutes);

    /**
     * Lấy URL tĩnh công khai của tệp tin.
     */
    String getFileUrl(String key);

    /**
     * Kiểm tra tệp tin có tồn tại và khớp kích thước yêu cầu không.
     */
    boolean verifyFile(String key, long expectedSize);
}
