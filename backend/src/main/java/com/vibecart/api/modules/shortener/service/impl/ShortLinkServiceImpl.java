package com.vibecart.api.modules.shortener.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.repository.ProductRepository;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.shortener.dto.request.ShortlinkCreateRequest;
import com.vibecart.api.modules.shortener.dto.response.ShortlinkResponse;
import com.vibecart.api.modules.shortener.entity.ShortLink;
import com.vibecart.api.modules.shortener.event.AffiliateClickProducer;
import com.vibecart.api.modules.shortener.repository.ShortLinkRepository;
import com.vibecart.api.modules.shortener.service.ShortLinkService;
import com.vibecart.api.modules.shortener.util.Base62Encoder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortLinkServiceImpl implements ShortLinkService {

    private final ShortLinkRepository shortLinkRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final AffiliateClickProducer affiliateClickProducer;
    private final ObjectMapper objectMapper;

    @Value("${app.shortlink.domain:http://localhost:8080}")
    private String domainPrefix;

    @Override
    @Transactional
    public ShortlinkResponse createShortLink(ShortlinkCreateRequest request, String currentUsername) {
        log.info("Creating short link for product ID: {} by user: {}", request.getProductId(), currentUsername);

        User creator = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // Generate UUID first
        String uuid = UUID.randomUUID().toString();
        String shortCode = Base62Encoder.generateShortCode(uuid);

        // Ensure shortCode is unique, fallback check
        int attempts = 0;
        while (shortLinkRepository.findByShortCode(shortCode).isPresent() && attempts < 5) {
            uuid = UUID.randomUUID().toString();
            shortCode = Base62Encoder.generateShortCode(uuid);
            attempts++;
        }

        ShortLink shortLink = ShortLink.builder()
                .shortCode(shortCode)
                .originalUrl(request.getLongUrl())
                .product(product)
                .creator(creator)
                .build();
        shortLink.setId(uuid); // Explicitly set generated UUID to match PostgreSQL key structure

        shortLink = shortLinkRepository.save(shortLink);

        // Save to Redis cache: shortlink:cache:{shortcode}
        try {
            Map<String, String> cacheMap = new HashMap<>();
            cacheMap.put("id", shortLink.getId());
            cacheMap.put("originalUrl", shortLink.getOriginalUrl());
            cacheMap.put("creatorId", creator.getId());
            cacheMap.put("productId", product.getId());

            String jsonVal = objectMapper.writeValueAsString(cacheMap);
            String cacheKey = "shortlink:cache:" + shortCode;
            redisTemplate.opsForValue().set(cacheKey, jsonVal, 30, TimeUnit.DAYS);
            log.info("Cached shortlink in Redis with key: {}", cacheKey);
        } catch (Exception e) {
            log.error("Failed to serialize and cache shortlink in Redis", e);
        }

        String fullShortLink = domainPrefix.endsWith("/") 
                ? domainPrefix + "v/" + shortCode 
                : domainPrefix + "/v/" + shortCode;

        return ShortlinkResponse.builder()
                .shortcode(shortCode)
                .shortlink(fullShortLink)
                .longUrl(shortLink.getOriginalUrl())
                .createdAt(shortLink.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShortlinkResponse> getMyShortLinks(String currentUsername) {
        User creator = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        List<ShortLink> shortLinks = shortLinkRepository.findByCreatorIdOrderByCreatedAtDesc(creator.getId());

        return shortLinks.stream().map(sl -> {
            String fullShortLink = domainPrefix.endsWith("/") 
                    ? domainPrefix + "v/" + sl.getShortCode() 
                    : domainPrefix + "/v/" + sl.getShortCode();

            return ShortlinkResponse.builder()
                    .shortcode(sl.getShortCode())
                    .shortlink(fullShortLink)
                    .longUrl(sl.getOriginalUrl())
                    .createdAt(sl.getCreatedAt())
                    .build();
        }).toList();
    }

    @Override
    public void handleRedirect(String shortCode, HttpServletRequest request, HttpServletResponse response) {
        log.info("Redirect requested for shortCode: {}", shortCode);

        String cacheKey = "shortlink:cache:" + shortCode;
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);

        String shortLinkId = null;
        String originalUrl = null;
        String creatorId = null;
        String productId = null;

        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                Map<String, String> cacheMap = objectMapper.readValue(cachedJson, Map.class);
                shortLinkId = cacheMap.get("id");
                originalUrl = cacheMap.get("originalUrl");
                creatorId = cacheMap.get("creatorId");
                productId = cacheMap.get("productId");
                log.info("Redis cache hit for shortCode: {}", shortCode);
            } catch (Exception e) {
                log.error("Failed to parse cached shortlink JSON", e);
            }
        }

        // On miss or parse failure, read from DB
        if (originalUrl == null) {
            log.info("Redis cache miss for shortCode: {}. Reading from DB...", shortCode);
            ShortLink shortLink = shortLinkRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND)); // product/link not found

            shortLinkId = shortLink.getId();
            originalUrl = shortLink.getOriginalUrl();
            creatorId = shortLink.getCreator().getId();
            productId = shortLink.getProduct().getId();

            // Write through to Redis
            try {
                Map<String, String> cacheMap = new HashMap<>();
                cacheMap.put("id", shortLinkId);
                cacheMap.put("originalUrl", originalUrl);
                cacheMap.put("creatorId", creatorId);
                cacheMap.put("productId", productId);

                String jsonVal = objectMapper.writeValueAsString(cacheMap);
                redisTemplate.opsForValue().set(cacheKey, jsonVal, 30, TimeUnit.DAYS);
            } catch (Exception e) {
                log.error("Failed to serialize and write through to Redis", e);
            }
        }

        // Set affiliate KOL creator ID cookie on the response. The cookie will expire in 30 days.
        Cookie cookie = new Cookie("affiliate_creator_id", creatorId);
        cookie.setPath("/");
        cookie.setMaxAge(30 * 24 * 60 * 60); // 30 days in seconds
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // Asynchronously emit click event using Kafka
        String browser = parseBrowser(userAgent);
        String deviceType = parseDeviceType(userAgent);
        String country = "Vietnam"; // Simple default country mapping or resolver

        com.vibecart.api.modules.shortener.event.ClickEventMessage clickMsg = 
            com.vibecart.api.modules.shortener.event.ClickEventMessage.builder()
                .shortLinkId(shortLinkId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .browser(browser)
                .deviceType(deviceType)
                .country(country)
                .clickTime(java.time.ZonedDateTime.now())
                .build();

        try {
            affiliateClickProducer.sendClickEvent(clickMsg);
        } catch (Exception e) {
            log.error("Failed to emit click event via Kafka", e);
        }

        // Perform redirect
        try {
            response.sendRedirect(originalUrl);
        } catch (IOException e) {
            log.error("Failed to redirect to {}", originalUrl, e);
            throw new RuntimeException("Redirect failed", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("chrome")) return "Chrome";
        if (ua.contains("firefox")) return "Firefox";
        if (ua.contains("safari") && !ua.contains("chrome")) return "Safari";
        if (ua.contains("edge")) return "Edge";
        return "Other";
    }

    private String parseDeviceType(String userAgent) {
        if (userAgent == null) return "Desktop";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "Mobile";
        if (ua.contains("tablet") || ua.contains("ipad")) return "Tablet";
        return "Desktop";
    }
}
