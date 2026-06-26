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

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String requestUri = request.getRequestURI();

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

        response.setHeader("X-RateLimit-Remaining", String.valueOf(globalProbe.getRemainingTokens()));

        filterChain.doFilter(request, response);
    }
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }
    private boolean isSensitivePath(String requestUri) {
        return properties.getSensitive().getPaths().stream()
                .anyMatch(requestUri::startsWith);
    }
    private Bucket createBucket(RateLimiterProperties.BucketConfig config) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(config.getCapacity())
                .refillGreedy(config.getRefillTokens(), Duration.ofSeconds(config.getRefillDurationSeconds()))
                .build();
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }
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
    @Scheduled(fixedRate = 600_000)
    public void cleanupExpiredBuckets() {
        long beforeGlobal = globalBuckets.size();
        long beforeSensitive = sensitiveBuckets.size();

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
