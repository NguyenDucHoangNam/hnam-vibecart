package com.vibecart.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình Rate Limiter cho VibeCart API.
 * <p>
 * Hỗ trợ 2 tầng giới hạn:
 * <ul>
 *   <li><b>Global:</b> Giới hạn tổng số request từ một IP trên tất cả API.</li>
 *   <li><b>Sensitive:</b> Giới hạn chặt hơn cho các endpoint nhạy cảm (login, register, OTP...).</li>
 * </ul>
 * <p>
 * Cấu hình tại {@code application.yaml} dưới prefix {@code app.rate-limiter}.
 */
@Configuration
@ConfigurationProperties(prefix = "app.rate-limiter")
@Getter
@Setter
public class RateLimiterProperties {

    /**
     * Bật/tắt Rate Limiter. Mặc định: true.
     */
    private boolean enabled = true;

    /**
     * Cấu hình giới hạn toàn cục (Global Rate Limit).
     */
    private BucketConfig global = new BucketConfig(100, 100, 60);

    /**
     * Cấu hình giới hạn cho các endpoint nhạy cảm (Sensitive Rate Limit).
     */
    private SensitiveBucketConfig sensitive = new SensitiveBucketConfig();

    @Getter
    @Setter
    public static class BucketConfig {
        /**
         * Dung lượng tối đa của bucket (số token tối đa).
         */
        private int capacity;

        /**
         * Số token được nạp lại sau mỗi chu kỳ.
         */
        private int refillTokens;

        /**
         * Chu kỳ nạp lại token (đơn vị: giây).
         */
        private int refillDurationSeconds;

        public BucketConfig() {
        }

        public BucketConfig(int capacity, int refillTokens, int refillDurationSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillDurationSeconds = refillDurationSeconds;
        }
    }

    @Getter
    @Setter
    public static class SensitiveBucketConfig extends BucketConfig {
        /**
         * Danh sách các URI path được coi là nhạy cảm, áp dụng rate limit chặt hơn.
         */
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
