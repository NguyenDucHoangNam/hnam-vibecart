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

    private final RestTemplate restTemplate = new RestTemplate();
    private final SecureRandom secureRandom = new SecureRandom();

    // Redis key prefixes (matching technical design doc)
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

    // Constants
    private static final long OTP_TTL_MINUTES = 5;
    private static final long OTP_COOLDOWN_SECONDS = 60;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_LOCKOUT_MINUTES = 15;
    private static final int MAX_CONCURRENT_SESSIONS = 3;
    private static final long REFRESH_TOKEN_TTL_DAYS = 7;
    private static final long PASSWORD_RESET_TTL_MINUTES = 10;
    private static final long GRACE_PERIOD_SECONDS = 10;

    // Disposable email domains blocklist
    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "tempmail.com", "10minutemail.com", "yopmail.com",
            "mailinator.com", "guerrillamail.com", "throwaway.email"
    );

    // Valid user statuses for admin operations
    private static final Set<String> VALID_STATUSES = Set.of(
            "ACTIVE", "BANNED", "PENDING_VERIFICATION", "PENDING_DELETION"
    );

    // ====================================================================
    // 1. ĐĂNG KÝ TÀI KHOẢN (Register)
    // ====================================================================
    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // 1. Normalize email
        String email = request.getEmail().trim().toLowerCase();

        // 2. Check disposable email
        validateNotDisposableEmail(email);

        // 2.b Clean up clashing unverified accounts (PENDING_VERIFICATION) to prevent registration deadlock
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

        // 3. Check uniqueness (including soft-deleted accounts in PENDING_DELETION grace period)
        if (userRepository.existsByUsernameAnywhere(request.getUsername())) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
        }
        if (userRepository.existsByEmailAnywhere(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        // 4. Determine role (SHOPPER → ROLE_USER, CREATOR → ROLE_CREATOR)
        String roleName = (request.getRole() != null && request.getRole().equalsIgnoreCase("CREATOR"))
                ? "ROLE_CREATOR" : "ROLE_USER";
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        // 5. Build & save User entity
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

        // 6. Generate OTP and store in Redis (with cooldown)
        String otp = generateAndStoreOtp(email);

        // Publish registration OTP event to Kafka for asynchronous email dispatch
        publishOtpEvent(email, otp);

        return userMapper.toUserResponse(savedUser);
    }

    // ====================================================================
    // 2. XÁC THỰC MÃ OTP (Verify OTP)
    // ====================================================================
    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // 1. Check brute-force attempt counter
        String attemptsKey = KEY_OTP_ATTEMPTS + email;
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_OTP_ATTEMPTS) {
            // Invalidate OTP immediately
            redisTemplate.delete(KEY_REGISTRATION_OTP + email);
            throw new AppException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }

        // 2. Read OTP from Redis
        String storedOtp = redisTemplate.opsForValue().get(KEY_REGISTRATION_OTP + email);
        if (storedOtp == null) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        // 3. Verify OTP
        if (!storedOtp.equals(request.getOtpCode())) {
            // Increment attempt counter
            redisTemplate.opsForValue().increment(attemptsKey);
            if (attempts == 0) {
                redisTemplate.expire(attemptsKey, Duration.ofMinutes(OTP_TTL_MINUTES));
            }
            throw new AppException(ErrorCode.INVALID_OTP);
        }

        // 4. OTP correct → Activate account
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        user.setStatus("ACTIVE");
        User activatedUser = userRepository.save(user);
        searchService.indexUser(activatedUser);

        // 5. Cleanup Redis keys
        redisTemplate.delete(KEY_REGISTRATION_OTP + email);
        redisTemplate.delete(attemptsKey);
        redisTemplate.delete(KEY_OTP_COOLDOWN + email);

        log.info("User {} activated via OTP verification", user.getUsername());

        // 6. Auto-login: return tokens
        return generateAuthResponse(user);
    }

    // ====================================================================
    // 1.b GỬI LẠI MÃ OTP (Resend OTP)
    // ====================================================================
    @Override
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // 1. Check user exists
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // 2. Must be PENDING_VERIFICATION to resend OTP
        if (!"PENDING_VERIFICATION".equals(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // 3. Generate and store OTP (with automatic cooldown checks)
        String otp = generateAndStoreOtp(email);

        // Publish resent OTP event to Kafka for asynchronous email dispatch
        publishOtpEvent(email, otp);

        log.info("OTP resent for user: {} with email {}", user.getUsername(), email);
    }

    // ====================================================================
    // 3. ĐĂNG NHẬP CỤC BỘ (Login)
    // ====================================================================
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getUsernameOrEmail().trim().toLowerCase();

        // 1. Check temporary account lockout
        String lockoutKey = KEY_LOGIN_LOCKOUT + identifier;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
            throw new AppException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
        }

        // 2. Find user by username or email
        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> {
                    incrementLoginAttempts(identifier);
                    return new AppException(ErrorCode.UNAUTHENTICATED);
                });

        // 3. Verify password
        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            incrementLoginAttempts(identifier);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // 4. Check account status
        switch (user.getStatus()) {
            case "PENDING_VERIFICATION" -> throw new AppException(ErrorCode.ACCOUNT_PENDING_VERIFICATION);
            case "BANNED" -> throw new AppException(ErrorCode.ACCOUNT_BANNED);
            case "ACTIVE", "PENDING_DELETION" -> { /* OK — Frontend sẽ kiểm tra status trong response để điều hướng */ }
            default -> throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // 5. Clear login attempts on success
        redisTemplate.delete(KEY_LOGIN_ATTEMPTS + identifier);

        log.info("User logged in: {}", user.getUsername());

        // 6. Generate tokens with session control
        return generateAuthResponse(user);
    }

    // ====================================================================
    // 4. ĐĂNG NHẬP GOOGLE (OAuth2)
    // ====================================================================
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

    // ====================================================================
    // 5. ĐĂNG NHẬP FACEBOOK (OAuth2)
    // ====================================================================
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

    // ====================================================================
    // 6. GIA HẠN TOKEN (Refresh Token Rotation)
    // ====================================================================
    @Override
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String oldRefreshToken = request.getRefreshToken();
        String redisKey = KEY_REFRESH_TOKEN + oldRefreshToken;

        // 1. Validate refresh token in Redis
        String username = redisTemplate.opsForValue().get(redisKey);
        if (username == null) {
            // Step 2a: Check Grace Period — tolerate duplicate requests from flaky networks
            String graceKey = KEY_GRACE_REFRESH + oldRefreshToken;
            String gracedTokensJson = redisTemplate.opsForValue().get(graceKey);
            if (gracedTokensJson != null) {
                // Race condition detected (not theft) — return the tokens already generated
                log.info("Grace Period hit: returning cached tokens for rotated refresh token (race condition, not theft)");
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

            // Step 2b: Token Theft Detection — token is not in grace period
            String rotatedTokenKey = "rotated_token:" + oldRefreshToken;
            String rotatedUser = redisTemplate.opsForValue().get(rotatedTokenKey);
            if (rotatedUser != null) {
                log.error("CẢNH BÁO BẢO MẬT: Phát hiện hành vi tái sử dụng Refresh Token đã xoay vòng (nguy cơ rò rỉ)! "
                        + "Token cũ: {}. Thu hồi tất cả các phiên hiện hoạt của người dùng: {}", oldRefreshToken, rotatedUser);
                purgeAllSessions(rotatedUser);
            } else {
                log.warn("Refresh token không tồn tại hoặc đã hết hạn: {}", oldRefreshToken);
            }
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // 2. Revoke old refresh token immediately (Token Rotation)
        redisTemplate.delete(redisKey);
        redisTemplate.opsForSet().remove(KEY_USER_SESSIONS + username, oldRefreshToken);

        // Save to rotated token history (TTL 1 hour) to detect potential theft reuse later
        redisTemplate.opsForValue().set("rotated_token:" + oldRefreshToken, username, Duration.ofHours(1));

        // 3. Load user and verify status
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!"ACTIVE".equals(user.getStatus()) && !"PENDING_DELETION".equals(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        log.info("Token refreshed for user: {}", username);

        // 4. Generate new token pair
        AuthResponse authResponse = generateAuthResponse(user);

        // 5. Store Grace Period Shadow Key (TTL 10s) — cache the new tokens
        //    so duplicate requests within 10s get the same response instead of triggering theft detection
        try {
            Map<String, String> graceData = Map.of(
                    "accessToken", authResponse.getAccessToken(),
                    "refreshToken", authResponse.getRefreshToken(),
                    "username", user.getUsername()
            );
            String graceJson = objectMapper.writeValueAsString(graceData);
            redisTemplate.opsForValue().set(
                    KEY_GRACE_REFRESH + oldRefreshToken, graceJson,
                    Duration.ofSeconds(GRACE_PERIOD_SECONDS)
            );
        } catch (Exception e) {
            log.warn("Failed to save grace period shadow key (non-critical): {}", e.getMessage());
        }

        return authResponse;
    }

    // ====================================================================
    // 7. ĐĂNG XUẤT (Logout)
    // ====================================================================
    @Override
    public void logout(RefreshRequest request, String accessToken) {
        log.info("Processing logout request");

        // 1. Delete Refresh Token from Redis
        String refreshToken = request.getRefreshToken();
        String username = redisTemplate.opsForValue().get(KEY_REFRESH_TOKEN + refreshToken);
        redisTemplate.delete(KEY_REFRESH_TOKEN + refreshToken);
        if (username != null) {
            redisTemplate.opsForSet().remove(KEY_USER_SESSIONS + username, refreshToken);
        }

        // 2. Blacklist Access Token in Redis (TTL = remaining JWT lifetime)
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            try {
                Date expiration = jwtTokenProvider.getExpirationFromToken(accessToken);
                long remainingMs = expiration.getTime() - System.currentTimeMillis();
                if (remainingMs > 0) {
                    redisTemplate.opsForValue().set(
                            KEY_BLACKLIST_TOKEN + accessToken, "true",
                            Duration.ofMillis(remainingMs)
                    );
                    log.info("Blacklisted access token for {} ms", remainingMs);
                }
            } catch (Exception ex) {
                log.error("Failed to blacklist access token: ", ex);
            }
        }

        log.info("User logged out successfully");
    }

    // ====================================================================
    // 8. LẤY THÔNG TIN CÁ NHÂN (Get Profile)
    // ====================================================================
    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return userMapper.toUserResponse(user);
    }

    // ====================================================================
    // 9. CẬP NHẬT HỒ SƠ CÁ NHÂN (Update Profile)
    // ====================================================================
    @Override
    @Transactional
    public AuthResponse updateProfile(String oldUsername, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(oldUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean usernameChanged = false;
        String newUsername = request.getUsername();
        if (newUsername != null && !newUsername.trim().isEmpty() && !newUsername.equals(oldUsername)) {
            newUsername = newUsername.trim();
            // Validate username constraint (min=5, max=30, pattern matching)
            if (newUsername.length() < 5 || newUsername.length() > 30) {
                throw new AppException(ErrorCode.USERNAME_INVALID);
            }
            if (!newUsername.matches("^[a-zA-Z0-9._-]+$")) {
                throw new AppException(ErrorCode.USERNAME_INVALID);
            }
            if (userRepository.existsByUsernameAnywhere(newUsername)) {
                throw new AppException(ErrorCode.USERNAME_EXISTED);
            }
            user.setUsername(newUsername);
            usernameChanged = true;
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User updatedUser = userRepository.save(user);
        searchService.indexUser(updatedUser);
        log.info("Profile updated for user: {}. Username changed: {}", oldUsername, usernameChanged);

        UserResponse userResponse = userMapper.toUserResponse(updatedUser);

        if (usernameChanged) {
            purgeAllSessions(oldUsername);
            return generateAuthResponse(updatedUser);
        } else {
            return AuthResponse.builder()
                    .user(userResponse)
                    .build();
        }
    }

    // ====================================================================
    // 10. ĐỔI MẬT KHẨU (Change Password)
    // ====================================================================
    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // 1. Verify old password
        if (user.getPassword() == null || !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }

        // 2. Confirm new password matches
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 3. New password must differ from old
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.SAME_PASSWORD);
        }

        // 4. Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 5. Kick out all other sessions (keep only current session implicit via client)
        purgeAllSessions(username);

        log.info("Password changed for user: {}. All other sessions purged.", username);
    }

    // ====================================================================
    // 11. QUÊN MẬT KHẨU (Forgot Password)
    // ====================================================================
    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Anti-enumeration: always respond the same regardless of email existence
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && "ACTIVE".equals(user.getStatus())) {
            // Generate UUID reset token
            String resetToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    KEY_PASSWORD_RESET_TOKEN + resetToken,
                    email,
                    Duration.ofMinutes(PASSWORD_RESET_TTL_MINUTES)
            );
            log.info("Password reset token generated for email: {}", email);

            // Send email via Kafka (Outbox Pattern) with reset link
            String resetLink = "http://localhost:3000/reset-password?token=" + resetToken;
            String subject = "[VibeCart] Yêu cầu đặt lại mật khẩu tài khoản";
            
            String body = "<div style=\"font-family: 'Inter', system-ui, sans-serif; background-color: #f4fbf7; padding: 40px 20px; text-align: center; min-height: 100%;\">"
                    + "  <div style=\"max-width: 500px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; padding: 40px; box-shadow: 0 10px 30px rgba(16, 185, 129, 0.08); border: 1px solid #e6f7ee; text-align: left;\">"
                    + "    <div style=\"text-align: center; margin-bottom: 30px;\">"
                    + "      <div style=\"display: inline-block; background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: white; padding: 12px 24px; border-radius: 12px; font-weight: 800; font-size: 22px; letter-spacing: 1px; box-shadow: 0 4px 15px rgba(16, 185, 129, 0.25);\">VibeCart</div>"
                    + "    </div>"
                    + "    <h2 style=\"color: #065f46; font-size: 20px; font-weight: 700; margin-top: 0; margin-bottom: 16px; text-align: center;\">Yêu cầu đặt lại mật khẩu</h2>"
                    + "    <p style=\"color: #374151; font-size: 15px; line-height: 1.6; margin-bottom: 24px; text-align: center;\">Chào bạn, chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn tại VibeCart. Vui lòng bấm vào liên kết bảo mật bên dưới để thiết lập mật khẩu mới:</p>"
                    + "    <div style=\"margin: 30px 0; text-align: center;\">"
                    + "      <a href=\"" + resetLink + "\" style=\"display: inline-block; background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: white; padding: 14px 28px; border-radius: 12px; font-weight: 700; font-size: 15px; text-decoration: none; box-shadow: 0 4px 15px rgba(16, 185, 129, 0.25);\">Đặt lại mật khẩu</a>"
                    + "    </div>"
                    + "    <p style=\"color: #6b7280; font-size: 13px; line-height: 1.5; text-align: center; margin-bottom: 30px;\">Liên kết này chỉ có hiệu lực trong vòng <b>10 phút</b> và chỉ sử dụng được một lần duy nhất. Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.</p>"
                    + "    <hr style=\"border: none; border-top: 1px solid #f0fdf4; margin-bottom: 24px;\">"
                    + "    <div style=\"text-align: center; color: #9ca3af; font-size: 12px;\">"
                    + "      <p style=\"margin: 0;\">Đây là email tự động từ hệ thống VibeCart.</p>"
                    + "      <p style=\"margin: 4px 0 0 0;\">© 2026 VibeCart. All rights reserved.</p>"
                    + "    </div>"
                    + "  </div>"
                    + "</div>";

            NotificationEvent event = NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .recipientEmail(email)
                    .subject(subject)
                    .body(body)
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

        // Always log, never reveal whether email exists
        log.info("Forgot password request processed for email: {}", email);
    }

    // ====================================================================
    // 12. ĐẶT LẠI MẬT KHẨU (Reset Password)
    // ====================================================================
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // 1. Validate reset token from Redis
        String redisKey = KEY_PASSWORD_RESET_TOKEN + request.getToken();
        String email = redisTemplate.opsForValue().get(redisKey);

        if (email == null) {
            throw new AppException(ErrorCode.INVALID_RESET_TOKEN);
        }

        // 2. Find user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // 3. Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 4. Delete reset token
        redisTemplate.delete(redisKey);

        // 5. Kick out ALL sessions for security
        purgeAllSessions(user.getUsername());

        log.info("Password reset completed for user: {}. All sessions purged.", user.getUsername());
    }

    // ====================================================================
    // 13. XÓA TÀI KHOẢN (Delete Account → PENDING_DELETION)
    // ====================================================================
    @Override
    @Transactional
    public void deleteAccount(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Set status to PENDING_DELETION (30-day grace period handled by scheduler)
        user.setStatus("PENDING_DELETION");
        user.setDeleted(true);
        userRepository.save(user);
        searchService.deleteUser(user.getId());

        // Purge all sessions immediately
        purgeAllSessions(username);

        log.info("Account deletion requested for user: {}. Status set to PENDING_DELETION.", username);
    }

    // ====================================================================
    // 14. ADMIN: CẬP NHẬT TRẠNG THÁI (Update User Status)
    // ====================================================================
    @Override
    @Transactional
    public UserResponse updateUserStatus(String userId, UpdateUserStatusRequest request, String adminUsername) {
        // 1. Find target user
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // 2. Cannot change own status
        if (targetUser.getUsername().equals(adminUsername)) {
            throw new AppException(ErrorCode.CANNOT_CHANGE_OWN_STATUS);
        }

        // 3. Validate status
        String newStatus = request.getStatus().toUpperCase();
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        // 4. Update status
        targetUser.setStatus(newStatus);
        User updatedUser = userRepository.save(targetUser);
        searchService.indexUser(updatedUser);

        // 5. If BANNED → purge all sessions immediately
        if ("BANNED".equals(newStatus)) {
            purgeAllSessions(targetUser.getUsername());
            log.info("User {} BANNED by admin {}. All sessions purged.", targetUser.getUsername(), adminUsername);
        } else {
            log.info("User {} status updated to {} by admin {}", targetUser.getUsername(), newStatus, adminUsername);
        }

        return userMapper.toUserResponse(updatedUser);
    }

    @Override
    public org.springframework.data.domain.Page<UserResponse> searchUsers(String search, String status, String role, org.springframework.data.domain.Pageable pageable) {
        String normalizedSearch = (search == null || search.trim().isEmpty()) ? null : "%" + search.trim().toLowerCase() + "%";
        String normalizedStatus = (status == null || status.trim().isEmpty() || "ALL".equalsIgnoreCase(status)) ? null : status.trim();
        String normalizedRole = (role == null || role.trim().isEmpty() || "ALL".equalsIgnoreCase(role)) ? null : role.trim();

        return userRepository.searchUsers(normalizedSearch, normalizedStatus, normalizedRole, pageable)
                .map(userMapper::toUserResponse);
    }

    @Override
    @Transactional
    public UserResponse updateUserRoles(String userId, com.vibecart.api.modules.iam.dto.request.UpdateUserRolesRequest request, String adminUsername) {
        // 1. Find target user
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // 2. Prevent self-demotion / editing own roles
        if (targetUser.getUsername().equals(adminUsername)) {
            throw new AppException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        // 3. Fetch roles from request
        Set<Role> newRoles = new HashSet<>();
        for (String roleName : request.getRoles()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT));
            newRoles.add(role);
        }

        // 4. Update user roles
        targetUser.setRoles(newRoles);
        User updatedUser = userRepository.save(targetUser);
        searchService.indexUser(updatedUser);

        log.info("User {} roles updated to {} by admin {}", targetUser.getUsername(), request.getRoles(), adminUsername);

        // Purge sessions to force token updates
        purgeAllSessions(targetUser.getUsername());

        return userMapper.toUserResponse(updatedUser);
    }

    // ====================================================================
    // PRIVATE HELPER METHODS
    // ====================================================================

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
                Duration.ofMinutes(OTP_TTL_MINUTES)
        );

        redisTemplate.opsForValue().set(
                cooldownKey, "true",
                Duration.ofSeconds(OTP_COOLDOWN_SECONDS)
        );

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

        String body = "<div style=\"font-family: 'Inter', system-ui, sans-serif; background-color: #f4fbf7; padding: 40px 20px; text-align: center; min-height: 100%;\">"
                + "  <div style=\"max-width: 500px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; padding: 40px; box-shadow: 0 10px 30px rgba(16, 185, 129, 0.08); border: 1px solid #e6f7ee; text-align: left;\">"
                + "    <div style=\"text-align: center; margin-bottom: 30px;\">"
                + "      <div style=\"display: inline-block; background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: white; padding: 12px 24px; border-radius: 12px; font-weight: 800; font-size: 22px; letter-spacing: 1px; box-shadow: 0 4px 15px rgba(16, 185, 129, 0.25);\">VibeCart</div>"
                + "    </div>"
                + "    <h2 style=\"color: #065f46; font-size: 20px; font-weight: 700; margin-top: 0; margin-bottom: 16px; text-align: center;\">Xác thực tài khoản của bạn</h2>"
                + "    <p style=\"color: #374151; font-size: 15px; line-height: 1.6; margin-bottom: 24px; text-align: center;\">Cảm ơn bạn đã lựa chọn mua sắm tại VibeCart. Để hoàn tất quy trình đăng ký, vui lòng sử dụng mã OTP bên dưới:</p>"
                + "    <div style=\"margin: 30px 0; text-align: center;\">"
                + "      <div style=\"display: inline-block; background-color: #f0fdf4; border: 2px dashed #34d399; padding: 16px 36px; border-radius: 12px; font-family: 'Courier New', Courier, monospace; font-size: 36px; font-weight: 800; color: #047857; letter-spacing: 6px; box-shadow: inset 0 2px 4px rgba(4, 120, 87, 0.04);\">" + otp + "</div>"
                + "    </div>"
                + "    <p style=\"color: #6b7280; font-size: 13px; line-height: 1.5; text-align: center; margin-bottom: 30px;\">Mã OTP này có hiệu lực trong vòng <b>5 phút</b> và chỉ được sử dụng một lần duy nhất. Vì sự an toàn của bạn, tuyệt đối không chia sẻ mã này cho bất kỳ ai.</p>"
                + "    <hr style=\"border: none; border-top: 1px solid #f0fdf4; margin-bottom: 24px;\">"
                + "    <div style=\"text-align: center; color: #9ca3af; font-size: 12px;\">"
                + "      <p style=\"margin: 0;\">Đây là email tự động từ hệ thống VibeCart.</p>"
                + "      <p style=\"margin: 4px 0 0 0;\">© 2026 VibeCart. All rights reserved.</p>"
                + "    </div>"
                + "  </div>"
                + "</div>";

        NotificationEvent event = NotificationEvent.builder()
                .eventId(eventId)
                .recipientEmail(email)
                .subject(subject)
                .body(body)
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
        // Normalize email to lowercase before DB lookup (OAuth Email Normalization)
        email = email.trim().toLowerCase();

        final String normalizedEmail = email;
        User user = userRepository.findByOauthProviderAndOauthId(provider, oauthId)
                .orElseGet(() -> {
                    Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
                    if (existingUser.isPresent()) {
                        User u = existingUser.get();
                        u.setOauthProvider(provider);
                        u.setOauthId(oauthId);
                        if (u.getAvatarUrl() == null) {
                            u.setAvatarUrl(picture);
                        }
                        User saved = userRepository.save(u);
                        searchService.indexUser(saved);
                        return saved;
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
                Duration.ofDays(REFRESH_TOKEN_TTL_DAYS)
        );

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

































