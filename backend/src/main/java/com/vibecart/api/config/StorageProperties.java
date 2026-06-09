package com.vibecart.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình các thuộc tính truy cập bộ lưu trữ S3/MinIO từ file cấu hình.
 */
@Configuration
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {
    private String endpoint;
    private String bucketName;
    private String accessKey;
    private String secretKey;
    private String region = "us-east-1";
    private String publicUrlPrefix;
}
