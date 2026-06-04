package com.vibecart.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.config.RateLimiterProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiter Filter sử dụng Bucket4j (Token Bucket Algorithm).
 * <p>
 * Chạy trước {@link JwtAuthenticationFilter} trong Security Filter Chain.
 * Áp dụng 2 tầng giới hạn:
 * <ul>
 *   <li><b>Global:</b> Giới hạn tổng request từ một IP (mặc định 100 req/phút).</li>
 *   <li><b>Sensitive:</b> Giới hạn chặt hơn cho endpoints nhạy cảm (mặc định 10 req/phút).</li>
 * </ul>
 * <p>
 * Khi vượt quá giới hạn, trả về HTTP 429 (Too Many Requests) với response chuẩn {@link ApiResponse}
 * và header {@code Retry-After} cho biết thời gian chờ (giây).
 */
@Component
@Slf4j
public class RateLimiterFilter extends OncePerRequestFilter {

    private final RateLimiterProperties properties;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Bucket> globalBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> sensitiveBuckets = new ConcurrentHashMap<>();

    private static final String GLOBAL_PREFIX = "global:";
    private static final String SENSITIVE_PREFIX = "sensitive:";

    public RateLimiterFilter(RateLimiterProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Bypass nếu Rate Limiter bị tắt
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String requestUri = request.getRequestURI();

        // 1. Kiểm tra Global Rate Limit
        Bucket globalBucket = globalBuckets.computeIfAbsent(
                GLOBAL_PREFIX + clientIp,
                key -> createBucket(properties.getGlobal())
        );

        ConsumptionProbe globalProbe = globalBucket.tryConsumeAndReturnRemaining(1);
        if (!globalProbe.isConsumed()) {
            long retryAfterSeconds = globalProbe.getNanosToWaitForRefill() / 1_000_000_000 + 1;
            log.warn("Rate limit exceeded (GLOBAL) for IP: {} on URI: {}. Retry after {}s", clientIp, requestUri, retryAfterSeconds);
            sendRateLimitResponse(response, retryAfterSeconds, globalProbe.getRemainingTokens());
            return;
        }

        // 2. Kiểm tra Sensitive Rate Limit (nếu là endpoint nhạy cảm)
        if (isSensitivePath(requestUri)) {
            Bucket sensitiveBucket = sensitiveBuckets.computeIfAbsent(
                    SENSITIVE_PREFIX + clientIp,
                    key -> createBucket(properties.getSensitive())
            );

            ConsumptionProbe sensitiveProbe = sensitiveBucket.tryConsumeAndReturnRemaining(1);
            if (!sensitiveProbe.isConsumed()) {
                long retryAfterSeconds = sensitiveProbe.getNanosToWaitForRefill() / 1_000_000_000 + 1;
                log.warn("Rate limit exceeded (SENSITIVE) for IP: {} on URI: {}. Retry after {}s", clientIp, requestUri, retryAfterSeconds);
                sendRateLimitResponse(response, retryAfterSeconds, sensitiveProbe.getRemainingTokens());
                return;
            }
        }

        // Thêm headers thông tin rate limit cho client
        response.setHeader("X-RateLimit-Remaining", String.valueOf(globalProbe.getRemainingTokens()));

        filterChain.doFilter(request, response);
    }

    /**
     * Trích xuất IP thực của client, hỗ trợ reverse proxy (Nginx, Load Balancer).
     * Thứ tự ưu tiên: X-Forwarded-For → X-Real-IP → remoteAddr.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            // X-Forwarded-For có thể chứa nhiều IP, lấy IP đầu tiên (client gốc)
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Kiểm tra xem URI có thuộc danh sách endpoint nhạy cảm không.
     */
    private boolean isSensitivePath(String requestUri) {
        return properties.getSensitive().getPaths().stream()
                .anyMatch(requestUri::startsWith);
    }

    /**
     * Tạo Bucket4j bucket từ cấu hình.
     */
    private Bucket createBucket(RateLimiterProperties.BucketConfig config) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(config.getCapacity())
                .refillGreedy(config.getRefillTokens(), Duration.ofSeconds(config.getRefillDurationSeconds()))
                .build();
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Gửi response HTTP 429 Too Many Requests với format ApiResponse chuẩn.
     */
    private void sendRateLimitResponse(HttpServletResponse response, long retryAfterSeconds, long remainingTokens)
            throws IOException {

        ErrorCode errorCode = ErrorCode.RATE_LIMIT_EXCEEDED;

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remainingTokens));

        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }

    /**
     * Dọn dẹp các bucket hết hạn mỗi 10 phút để tránh memory leak.
     * Bucket không còn token đang refill sẽ bị xóa, tạo lại khi request mới đến.
     */
    @Scheduled(fixedRate = 600_000) // 10 phút
    public void cleanupExpiredBuckets() {
        long beforeGlobal = globalBuckets.size();
        long beforeSensitive = sensitiveBuckets.size();

        // Xóa bucket đã đầy token (không hoạt động gần đây)
        globalBuckets.entrySet().removeIf(entry ->
                entry.getValue().getAvailableTokens() >= properties.getGlobal().getCapacity()
        );
        sensitiveBuckets.entrySet().removeIf(entry ->
                entry.getValue().getAvailableTokens() >= properties.getSensitive().getCapacity()
        );

        long removedGlobal = beforeGlobal - globalBuckets.size();
        long removedSensitive = beforeSensitive - sensitiveBuckets.size();

        if (removedGlobal > 0 || removedSensitive > 0) {
            log.info("Rate limiter cleanup: removed {} global buckets, {} sensitive buckets. " +
                            "Remaining: {} global, {} sensitive",
                    removedGlobal, removedSensitive, globalBuckets.size(), sensitiveBuckets.size());
        }
    }
}
