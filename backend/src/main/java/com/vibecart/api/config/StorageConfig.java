package com.vibecart.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Cấu hình kết nối AWS S3 và S3 Presigner Client.
 */
@Configuration
public class StorageConfig {

    private final StorageProperties storageProperties;

    public StorageConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    /**
     * Khởi tạo S3Client để tương tác trực tiếp với bộ lưu trữ AWS S3 hoặc MinIO.
     */
    @Bean
    public S3Client s3Client() {
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                storageProperties.getAccessKey(),
                storageProperties.getSecretKey()
        );

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(storageProperties.getRegion()))
                .serviceConfiguration(s3Configuration);

        if (storageProperties.getEndpoint() != null && !storageProperties.getEndpoint().isEmpty()) {
            builder.endpointOverride(URI.create(storageProperties.getEndpoint()));
        }

        return builder.build();
    }

    /**
     * Khởi tạo S3Presigner để ký số các đường dẫn tải lên/tải xuống trực tiếp.
     */
    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                storageProperties.getAccessKey(),
                storageProperties.getSecretKey()
        );

        S3Presigner.Builder builder = S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(storageProperties.getRegion()));

        if (storageProperties.getEndpoint() != null && !storageProperties.getEndpoint().isEmpty()) {
            builder.endpointOverride(URI.create(storageProperties.getEndpoint()));
        }

        return builder.build();
    }
}
