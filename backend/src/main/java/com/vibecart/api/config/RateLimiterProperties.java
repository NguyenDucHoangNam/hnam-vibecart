package com.vibecart.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình giới hạn tần suất yêu cầu (Rate Limiter) cho hệ thống API.
 */
@Configuration
@ConfigurationProperties(prefix = "app.rate-limiter")
@Getter
@Setter
public class RateLimiterProperties {

    /**
     * Bật hoặc tắt chức năng Rate Limiter.
     */
    private boolean enabled = true;

    /**
     * Cấu hình giới hạn tần suất chung cho toàn bộ hệ thống (Global).
     */
    private BucketConfig global = new BucketConfig(100, 100, 60);

    /**
     * Cấu hình giới hạn tần suất riêng cho các endpoint nhạy cảm (Sensitive).
     */
    private SensitiveBucketConfig sensitive = new SensitiveBucketConfig();

    /**
     * Cấu hình chi tiết của Token Bucket.
     */
    @Getter
    @Setter
    public static class BucketConfig {
        private int capacity;
        private int refillTokens;
        private int refillDurationSeconds;

        public BucketConfig() {
        }

        public BucketConfig(int capacity, int refillTokens, int refillDurationSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillDurationSeconds = refillDurationSeconds;
        }
    }

    /**
     * Cấu hình danh sách endpoint nhạy cảm cần áp dụng tần suất riêng.
     */
    @Getter
    @Setter
    public static class SensitiveBucketConfig extends BucketConfig {
        private List<String> paths = List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/verify-otp",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password"
        );

        public SensitiveBucketConfig() {
            super(10, 10, 60);
        }
    }
}
