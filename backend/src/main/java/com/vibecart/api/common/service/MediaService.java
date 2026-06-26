package com.vibecart.api.common.service;

import com.vibecart.api.common.dto.MediaUploadResponse;
import com.vibecart.api.common.dto.PresignedUrlResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
public interface MediaService {
    MediaUploadResponse uploadFile(MultipartFile file, String folder, String username);
    List<MediaUploadResponse> uploadFiles(List<MultipartFile> files, String folder, String username);
    PresignedUrlResponse generatePresignedUrl(String contentType, long fileSize, String folder, String username);
    void confirmUpload(String key, String username);
    void deleteFile(String key, String username, Collection<? extends GrantedAuthority> authorities);
}
