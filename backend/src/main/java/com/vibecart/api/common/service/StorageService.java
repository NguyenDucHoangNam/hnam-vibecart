package com.vibecart.api.common.service;

import java.io.InputStream;

public interface StorageService {
    /**
     * Uploads a file to the storage server.
     *
     * @param key         Unique key (path) of the file in the bucket.
     * @param content     The file input stream.
     * @param contentLength Size of the file.
     * @param contentType MIME type of the file.
     * @return Public URL of the uploaded file.
     */
    String uploadFile(String key, InputStream content, long contentLength, String contentType);

    /**
     * Deletes a file from the storage server.
     *
     * @param key Unique key (path) of the file in the bucket.
     */
    void deleteFile(String key);

    /**
     * Generates a pre-signed URL for direct browser uploads.
     *
     * @param key               Unique key (path) for the target file.
     * @param contentType       MIME type of the target file.
     * @param contentLength     Size of the target file in bytes.
     * @param expirationMinutes Time until the link expires.
     * @return The pre-signed upload URL.
     */
    String generatePresignedUploadUrl(String key, String contentType, long contentLength, int expirationMinutes);

    /**
     * Formulates the public static URL for a given file key.
     *
     * @param key Unique key (path) of the file.
     * @return The absolute public URL.
     */
    String getFileUrl(String key);

    /**
     * Verifies if a file exists in the storage and matches the expected size.
     *
     * @param key          Unique key (path) of the file.
     * @param expectedSize Expected size in bytes.
     * @return true if file exists and matches size, false otherwise.
     */
    boolean verifyFile(String key, long expectedSize);
}
