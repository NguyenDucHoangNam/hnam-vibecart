package com.vibecart.api.common.service;

import java.io.InputStream;
public interface StorageService {
    String uploadFile(String key, InputStream content, long contentLength, String contentType);
    void deleteFile(String key);
    String generatePresignedUploadUrl(String key, String contentType, long contentLength, int expirationMinutes);
    String getFileUrl(String key);
    boolean verifyFile(String key, long expectedSize);
}
