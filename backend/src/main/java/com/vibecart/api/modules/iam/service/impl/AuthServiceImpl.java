package com.vibecart.api.modules.iam.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.common.security.JwtTokenProvider;
import com.vibecart.api.modules.iam.dto.request.*;
import com.vibecart.api.modules.iam.dto.response.*;
import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.mapper.UserMapper;
import com.vibecart.api.modules.iam.repository.RoleRepository;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.iam.service.AuthService;
import com.vibecart.api.modules.search.entity.OutboxEvent;
import com.vibecart.api.modules.search.repository.OutboxEventRepository;
import com.vibecart.api.modules.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import com.vibecart.api.modules.jobs.dto.NotificationEvent;
import com.vibecart.api.config.KafkaTopicConfig;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation của {@link AuthService} xử lý đăng ký, đăng nhập, OAuth2, OTP,
 * token, quản lý tài khoản.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SearchService searchService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.jwt.expiration-ms:3600000}")
    private long jwtExpirationInMs;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String KEY_REGISTRATION_OTP = "registration_otp:";
    private static final String KEY_OTP_COOLDOWN = "otp_cooldown:";
    private static final String KEY_OTP_ATTEMPTS = "otp_attempts:";
    private static final String KEY_LOGIN_ATTEMPTS = "login_attempts:";
    private static final String KEY_LOGIN_LOCKOUT = "login_lockout:";
    private static final String KEY_REFRESH_TOKEN = "refresh_token:";
    private static final String KEY_USER_SESSIONS = "user_sessions:";
    private static final String KEY_BLACKLIST_TOKEN = "blacklist_token:";
    private static final String KEY_PASSWORD_RESET_TOKEN = "password_reset_token:";
    private static final String KEY_GRACE_REFRESH = "grace_refresh:";

    private static final long OTP_TTL_MINUTES = 5;
    private static final long OTP_COOLDOWN_SECONDS = 60;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_LOCKOUT_MINUTES = 15;
    private static final int MAX_CONCURRENT_SESSIONS = 3;
    private static final long REFRESH_TOKEN_TTL_DAYS = 7;
    private static final long PASSWORD_RESET_TTL_MINUTES = 10;
    private static final long GRACE_PERIOD_SECONDS = 10;

    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "tempmail.com", "10minutemail.com", "yopmail.com",
            "mailinator.com", "guerrillamail.com", "throwaway.email");

    private static final Set<String> VALID_STATUSES = Set.of(
            "ACTIVE", "BANNED", "PENDING_VERIFICATION", "PENDING_DELETION");

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        validateNotDisposableEmail(email);

        userRepository.findByUsername(request.getUsername())
                .filter(u -> "PENDING_VERIFICATION".equals(u.getStatus()))
                .ifPresent(u -> {
                    log.info("Hard deleting clashing unverified user by username: {}", u.getUsername());
                    userRepository.hardDeleteUserRolesByUserId(u.getId());
                    userRepository.hardDeleteUserByUserId(u.getId());
                });

        userRepository.findByEmail(email)
                .filter(u -> "PENDING_VERIFICATION".equals(u.getStatus()))
                .ifPresent(u -> {
                    log.info("Hard deleting clashing unverified user by email: {}", u.getEmail());
                    userRepository.hardDeleteUserRolesByUserId(u.getId());
                    userRepository.hardDeleteUserByUserId(u.getId());
                });

        if (userRepository.existsByUsernameAnywhere(request.getUsername())) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
        }
        if (userRepository.existsByEmailAnywhere(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        String roleName = (request.getRole() != null && request.getRole().equalsIgnoreCase("CREATOR"))
                ? "ROLE_CREATOR"
                : "ROLE_USER";
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        User user = User.builder()
                .username(request.getUsername())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .status("PENDING_VERIFICATION")
                .oauthProvider("LOCAL")
                .roles(Set.of(role))
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered: {} with status PENDING_VERIFICATION", savedUser.getUsername());

        String otp = generateAndStoreOtp(email);

        publishOtpEvent(email, otp);

        return userMapper.toUserResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        String attemptsKey = KEY_OTP_ATTEMPTS + email;
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_OTP_ATTEMPTS) {
            redisTemplate.delete(KEY_REGISTRATION_OTP + email);
            throw new AppException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }

        String storedOtp = redisTemplate.opsForValue().get(KEY_REGISTRATION_OTP + email);
        if (storedOtp == null) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (!storedOtp.equals(request.getOtpCode())) {
            redisTemplate.opsForValue().increment(attemptsKey);
            if (attempts == 0) {
                redisTemplate.expire(attemptsKey, Duration.ofMinutes(OTP_TTL_MINUTES));
            }
            throw new AppException(ErrorCode.INVALID_OTP);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        user.setStatus("ACTIVE");
        User activatedUser = userRepository.save(user);
        searchService.indexUser(activatedUser);

        redisTemplate.delete(KEY_REGISTRATION_OTP + email);
        redisTemplate.delete(attemptsKey);
        redisTemplate.delete(KEY_OTP_COOLDOWN + email);

        log.info("User {} activated via OTP verification", user.getUsername());

        return generateAuthResponse(user);
    }

    @Override
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!"PENDING_VERIFICATION".equals(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        String otp = generateAndStoreOtp(email);

        publishOtpEvent(email, otp);

        log.info("OTP resent for user: {} with email {}", user.getUsername(), email);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getUsernameOrEmail().trim().toLowerCase();

        String lockoutKey = KEY_LOGIN_LOCKOUT + identifier;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
            throw new AppException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
        }

        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> {
                    incrementLoginAttempts(identifier);
                    return new AppException(ErrorCode.UNAUTHENTICATED);
                });

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            incrementLoginAttempts(identifier);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        switch (user.getStatus()) {
            case "PENDING_VERIFICATION" -> throw new AppException(ErrorCode.ACCOUNT_PENDING_VERIFICATION);
            case "BANNED" -> throw new AppException(ErrorCode.ACCOUNT_BANNED);
            case "ACTIVE", "PENDING_DELETION" -> {
                /* OK — Frontend sẽ kiểm tra status trong response để điều hướng */ }
            default -> throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        redisTemplate.delete(KEY_LOGIN_ATTEMPTS + identifier);

        log.info("User logged in: {}", user.getUsername());

        return generateAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse loginGoogle(OAuth2Request request) {
        log.info("Google OAuth2 login attempt");
        String verifyUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + request.getToken();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(verifyUrl, Map.class);
            if (response == null || !response.containsKey("email")) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            String email = ((String) response.get("email")).trim().toLowerCase();
            String name = (String) response.get("name");
            String picture = (String) response.get("picture");
            String oauthId = (String) response.get("sub");

            return processOAuthUser("GOOGLE", oauthId, email, name, picture);
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Google authentication failed: ", ex);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    @Override
    @Transactional
    public AuthResponse loginFacebook(OAuth2Request request) {
        log.info("Facebook OAuth2 login attempt");
        String verifyUrl = "https://graph.facebook.com/me?fields=id,name,email,picture.type(large)&access_token="
                + request.getToken();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(verifyUrl, Map.class);
            if (response == null || !response.containsKey("id")) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            String oauthId = (String) response.get("id");
            String name = (String) response.get("name");
            String email = response.containsKey("email")
                    ? ((String) response.get("email")).trim().toLowerCase()
                    : oauthId + "@facebook.com";

            String picture = null;
            if (response.containsKey("picture")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pictureData = (Map<String, Object>) response.get("picture");
                if (pictureData != null && pictureData.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) pictureData.get("data");
                    if (data != null) {
                        picture = (String) data.get("url");
                    }
                }
            }

            return processOAuthUser("FACEBOOK", oauthId, email, name, picture);
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Facebook authentication failed: ", ex);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String oldRefreshToken = request.getRefreshToken();
        String redisKey = KEY_REFRESH_TOKEN + oldRefreshToken;

        String username = redisTemplate.opsForValue().get(redisKey);
        if (username == null) {
            String graceKey = KEY_GRACE_REFRESH + oldRefreshToken;
            String gracedTokensJson = redisTemplate.opsForValue().get(graceKey);
            if (gracedTokensJson != null) {
                log.info(
                        "Grace Period hit: returning cached tokens for rotated refresh token (race condition, not theft)");
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> cachedTokens = objectMapper.readValue(gracedTokensJson, Map.class);
                    String cachedUsername = cachedTokens.get("username");
                    User user = userRepository.findByUsername(cachedUsername)
                            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
                    return AuthResponse.builder()
                            .accessToken(cachedTokens.get("accessToken"))
                            .refreshToken(cachedTokens.get("refreshToken"))
                            .expiresIn(jwtExpirationInMs / 1000)
                            .user(userMapper.toUserResponse(user))
                            .build();
                } catch (Exception e) {
                    log.error("Failed to parse grace period cached tokens", e);
                    throw new AppException(ErrorCode.UNAUTHENTICATED);
                }
            }

            String rotatedTokenKey = "rotated_token:" + oldRefreshToken;
            String rotatedUser = redisTemplate.opsForValue().get(rotatedTokenKey);
            if (rotatedUser != null) {
                log.error("CẢNH BÁO BẢO MẬT: Phát hiện hành vi tái sử dụng Refresh Token đã xoay vòng (nguy cơ rò rỉ)! "
                        + "Token cũ: {}. Thu hồi tất cả các phiên hiện hoạt của người dùng: {}", oldRefreshToken,
                        rotatedUser);
                purgeAllSessions(rotatedUser);
            } else {
                log.warn("Refresh token không tồn tại hoặc đã hết hạn: {}", oldRefreshToken);
            }
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        redisTemplate.delete(redisKey);
        redisTemplate.opsForSet().remove(KEY_USER_SESSIONS + username, oldRefreshToken);

        redisTemplate.opsForValue().set("rotated_token:" + oldRefreshToken, username, Duration.ofHours(1));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!"ACTIVE".equals(user.getStatus()) && !"PENDING_DELETION".equals(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        log.info("Token refreshed for user: {}", username);

        AuthResponse authResponse = generateAuthResponse(user);

        try {
            Map<String, String> graceData = Map.of(
                    "accessToken", authResponse.getAccessToken(),
                    "refreshToken", authResponse.getRefreshToken(),
                    "username", user.getUsername());
            String graceJson = objectMapper.writeValueAsString(graceData);
            redisTemplate.opsForValue().set(
                    KEY_GRACE_REFRESH + oldRefreshToken, graceJson,
                    Duration.ofSeconds(GRACE_PERIOD_SECONDS));
        } catch (Exception e) {
            log.warn("Failed to save grace period shadow key (non-critical): {}", e.getMessage());
        }

        return authResponse;
    }

    @Override
    public void logout(RefreshRequest request, String accessToken) {
        log.info("Processing logout request");

        String refreshToken = request.getRefreshToken();
        String username = redisTemplate.opsForValue().get(KEY_REFRESH_TOKEN + refreshToken);
        redisTemplate.delete(KEY_REFRESH_TOKEN + refreshToken);
        if (username != null) {
            redisTemplate.opsForSet().remove(KEY_USER_SESSIONS + username, refreshToken);
        }

        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            try {
                Date expiration = jwtTokenProvider.getExpirationFromToken(accessToken);
                long remainingMs = expiration.getTime() - System.currentTimeMillis();
                if (remainingMs > 0) {
                    redisTemplate.opsForValue().set(
                            KEY_BLACKLIST_TOKEN + accessToken, "true",
                            Duration.ofMillis(remainingMs));
                    log.info("Blacklisted access token for {} ms", remainingMs);
                }
            } catch (Exception ex) {
                log.error("Failed to blacklist access token: ", ex);
            }
        }

        log.info("User logged out successfully");
    }



    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && "ACTIVE".equals(user.getStatus())) {
            String resetToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    KEY_PASSWORD_RESET_TOKEN + resetToken,
                    email,
                    Duration.ofMinutes(PASSWORD_RESET_TTL_MINUTES));
            log.info("Password reset token generated for email: {}", email);

            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
            String subject = "[VibeCart] Yêu cầu đặt lại mật khẩu tài khoản";

            NotificationEvent event = NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .recipientEmail(email)
                    .subject(subject)
                    .templateType("FORGOT_PASSWORD")
                    .templateParams(Map.of("resetLink", resetLink))
                    .build();

            try {
                String payload = objectMapper.writeValueAsString(event);
                OutboxEvent outboxEvent = OutboxEvent.builder()
                        .aggregateType("IAM")
                        .aggregateId(email)
                        .eventType("FORGOT_PASSWORD")
                        .payload(payload)
                        .status("PENDING")
                        .build();
                outboxEventRepository.save(outboxEvent);
                log.info("Saved Forgot Password Notification Outbox Event for email: {}", email);
            } catch (Exception e) {
                log.error("Failed to save Forgot Password Outbox Event for email: {}", email, e);
            }
        }

        log.info("Forgot password request processed for email: {}", email);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String redisKey = KEY_PASSWORD_RESET_TOKEN + request.getToken();
        String email = redisTemplate.opsForValue().get(redisKey);

        if (email == null) {
            throw new AppException(ErrorCode.INVALID_RESET_TOKEN);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        redisTemplate.delete(redisKey);

        purgeAllSessions(user.getUsername());

        log.info("Password reset completed for user: {}. All sessions purged.", user.getUsername());
    }



    /**
     * Generate 6-digit OTP, store in Redis, and set cooldown.
     */
    private String generateAndStoreOtp(String email) {
        String cooldownKey = KEY_OTP_COOLDOWN + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new AppException(ErrorCode.OTP_COOLDOWN);
        }

        String otp = String.format("%06d", secureRandom.nextInt(999999));

        redisTemplate.opsForValue().set(
                KEY_REGISTRATION_OTP + email, otp,
                Duration.ofMinutes(OTP_TTL_MINUTES));

        redisTemplate.opsForValue().set(
                cooldownKey, "true",
                Duration.ofSeconds(OTP_COOLDOWN_SECONDS));

        log.info("OTP generated for {}: {}", email, otp);
        return otp;
    }

    /**
     * Validate email domain is not in disposable blocklist.
     */
    private void validateNotDisposableEmail(String email) {
        String domain = email.substring(email.indexOf("@") + 1);
        if (DISPOSABLE_DOMAINS.contains(domain)) {
            throw new AppException(ErrorCode.DISPOSABLE_EMAIL_NOT_ALLOWED);
        }
    }

    /**
     * Publish OTP registration/resend event to Kafka.
     */
    private void publishOtpEvent(String email, String otp) {
        String eventId = UUID.randomUUID().toString();
        String subject = "[VibeCart] Mã xác thực OTP kích hoạt tài khoản";

        NotificationEvent event = NotificationEvent.builder()
                .eventId(eventId)
                .recipientEmail(email)
                .subject(subject)
                .templateType("REGISTRATION_OTP")
                .templateParams(Map.of("otp", otp))
                .build();

        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("IAM")
                    .aggregateId(email)
                    .eventType("REGISTRATION_OTP")
                    .payload(payload)
                    .status("PENDING")
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.info("Saved OTP Notification Outbox Event for email: {}", email);
        } catch (Exception e) {
            log.error("Failed to save Notification Outbox Event for email: {}", email, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    /**
     * Increment login attempts in Redis and lock out if necessary.
     */
    private void incrementLoginAttempts(String identifier) {
        String attemptsKey = KEY_LOGIN_ATTEMPTS + identifier;
        String lockoutKey = KEY_LOGIN_LOCKOUT + identifier;

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, Duration.ofMinutes(LOGIN_LOCKOUT_MINUTES));
        }

        if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
            redisTemplate.opsForValue().set(lockoutKey, "true", Duration.ofMinutes(LOGIN_LOCKOUT_MINUTES));
            redisTemplate.delete(attemptsKey);
            log.warn("Account locked out due to multiple failed login attempts: {}", identifier);
        }
    }

    /**
     * Process Google/Facebook OAuth2 user authentication.
     */
    private AuthResponse processOAuthUser(String provider, String oauthId, String email, String name, String picture) {
        email = email.trim().toLowerCase();

        final String normalizedEmail = email;
        User user = userRepository.findByOauthProviderAndOauthId(provider, oauthId)
                .orElseGet(() -> {
                    Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
                    if (existingUser.isPresent()) {
                        User u = existingUser.get();
                        if ("PENDING_VERIFICATION".equals(u.getStatus())) {
                            log.info("Hard deleting clashing unverified user before OAuth2 mapping: {}", u.getEmail());
                            userRepository.hardDeleteUserRolesByUserId(u.getId());
                            userRepository.hardDeleteUserByUserId(u.getId());
                            // Clean up OTP state in Redis
                            redisTemplate.delete(KEY_REGISTRATION_OTP + normalizedEmail);
                            redisTemplate.delete(KEY_OTP_COOLDOWN + normalizedEmail);
                            redisTemplate.delete(KEY_OTP_ATTEMPTS + normalizedEmail);
                        } else {
                            u.setOauthProvider(provider);
                            u.setOauthId(oauthId);
                            if (u.getAvatarUrl() == null) {
                                u.setAvatarUrl(picture);
                            }
                            User saved = userRepository.save(u);
                            searchService.indexUser(saved);
                            return saved;
                        }
                    }

                    String username = normalizedEmail.substring(0, normalizedEmail.indexOf("@"));
                    if (userRepository.existsByUsernameAnywhere(username)) {
                        username = username + "_" + (System.currentTimeMillis() % 10000);
                    }

                    Role userRole = roleRepository.findByName("ROLE_USER")
                            .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

                    User newUser = User.builder()
                            .username(username)
                            .email(normalizedEmail)
                            .fullName(name)
                            .avatarUrl(picture)
                            .status("ACTIVE")
                            .oauthProvider(provider)
                            .oauthId(oauthId)
                            .roles(Set.of(userRole))
                            .build();

                    User saved = userRepository.save(newUser);
                    searchService.indexUser(saved);
                    return saved;
                });

        if ("BANNED".equals(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_BANNED);
        }

        return generateAuthResponse(user);
    }

    /**
     * Generate access token, refresh token and session info for AuthResponse.
     */
    private AuthResponse generateAuthResponse(User user) {
        String rolesStr = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.joining(","));

        String accessToken = jwtTokenProvider.generateToken(user.getUsername(), rolesStr);
        String refreshToken = UUID.randomUUID().toString();

        String sessionKey = KEY_USER_SESSIONS + user.getUsername();
        Long sessionCount = redisTemplate.opsForSet().size(sessionKey);
        if (sessionCount != null && sessionCount >= MAX_CONCURRENT_SESSIONS) {
            String poppedToken = redisTemplate.opsForSet().pop(sessionKey);
            if (poppedToken != null) {
                redisTemplate.delete(KEY_REFRESH_TOKEN + poppedToken);
            }
        }

        redisTemplate.opsForValue().set(
                KEY_REFRESH_TOKEN + refreshToken,
                user.getUsername(),
                Duration.ofDays(REFRESH_TOKEN_TTL_DAYS));

        redisTemplate.opsForSet().add(sessionKey, refreshToken);
        redisTemplate.expire(sessionKey, Duration.ofDays(REFRESH_TOKEN_TTL_DAYS));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtExpirationInMs / 1000)
                .user(userMapper.toUserResponse(user))
                .build();
    }

    /**
     * Purge all active sessions for a user from Redis.
     */
    private void purgeAllSessions(String username) {
        String sessionKey = KEY_USER_SESSIONS + username;
        Set<String> tokens = redisTemplate.opsForSet().members(sessionKey);

        if (tokens != null) {
            for (String token : tokens) {
                redisTemplate.delete(KEY_REFRESH_TOKEN + token);
            }
        }
        redisTemplate.delete(sessionKey);

        log.info("All sessions purged for user: {}", username);
    }
}
