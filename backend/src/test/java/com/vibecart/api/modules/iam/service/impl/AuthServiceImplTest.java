package com.vibecart.api.modules.iam.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.common.security.JwtTokenProvider;
import com.vibecart.api.modules.iam.dto.request.*;
import com.vibecart.api.modules.iam.dto.response.AuthResponse;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.mapper.UserMapper;
import com.vibecart.api.modules.iam.repository.RoleRepository;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.search.repository.OutboxEventRepository;
import com.vibecart.api.modules.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private UserMapper userMapper;

    @Mock
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private SearchService searchService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private Role defaultRole;
    private Role creatorRole;
    private User testUser;
    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() {
        // Set the @Value field via reflection
        ReflectionTestUtils.setField(authService, "jwtExpirationInMs", 3600000L);

        defaultRole = Role.builder()
                .name("ROLE_USER")
                .description("Standard user")
                .build();
        defaultRole.setId("role-1");

        creatorRole = Role.builder()
                .name("ROLE_CREATOR")
                .description("Creator user")
                .build();
        creatorRole.setId("role-2");

        testUser = User.builder()
                .username("testuser")
                .email("test@email.com")
                .password("encodedPassword")
                .fullName("Test User")
                .oauthProvider("LOCAL")
                .status("ACTIVE")
                .roles(Set.of(defaultRole))
                .build();
        testUser.setId("user-100");

        testUserResponse = UserResponse.builder()
                .id("user-100")
                .username("testuser")
                .email("test@email.com")
                .fullName("Test User")
                .status("ACTIVE")
                .oauthProvider("LOCAL")
                .roles(Set.of("ROLE_USER"))
                .build();
    }

    // ====================================================================
    // Helper: stub Redis operations used by generateAuthResponse
    // ====================================================================
    private void stubRedisForGenerateAuthResponse() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(setOperations.size(anyString())).thenReturn(0L);
        lenient().when(setOperations.add(anyString(), any(String[].class))).thenReturn(1L);
        lenient().when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        lenient().doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    // Helper: stub Redis operations used by generateAndStoreOtp (called by register)
    private void stubRedisForGenerateAndStoreOtp() {
        lenient().when(redisTemplate.hasKey(startsWith("otp_cooldown:"))).thenReturn(false);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        try {
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception e) {}
    }

    // Helper: stub Redis operations used by incrementLoginAttempts
    private void stubRedisForIncrementLoginAttempts() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.increment(anyString())).thenReturn(1L);
        lenient().when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
    }

    // Helper: stub Redis for purgeAllSessions
    private void stubRedisForPurgeAllSessions() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(setOperations.members(anyString())).thenReturn(Set.of("some-refresh-token"));
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
    }

    // ====================================================================
    // 1. REGISTER TESTS
    // ====================================================================
    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("Success - registers a new user with default SHOPPER role")
        void register_success_returnsUserResponse() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("testuser")
                    .email("test@email.com")
                    .password("RawP@ss1")
                    .fullName("Test User")
                    .build();

            when(userRepository.existsByUsernameAnywhere("testuser")).thenReturn(false);
            when(userRepository.existsByEmailAnywhere("test@email.com")).thenReturn(false);
            when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(defaultRole));
            when(passwordEncoder.encode("RawP@ss1")).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toUserResponse(testUser)).thenReturn(testUserResponse);
            stubRedisForGenerateAndStoreOtp();

            UserResponse response = authService.register(request);

            assertNotNull(response);
            assertEquals("testuser", response.getUsername());
            assertEquals("test@email.com", response.getEmail());
            verify(userRepository).save(any(User.class));
            verify(roleRepository).findByName("ROLE_USER");
        }

        @Test
        @DisplayName("Success - registers with CREATOR role")
        void register_creatorRole_usesRoleCreator() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("creator01")
                    .email("creator@email.com")
                    .password("RawP@ss1")
                    .fullName("Creator User")
                    .role("CREATOR")
                    .build();

            User creatorUser = User.builder()
                    .username("creator01")
                    .email("creator@email.com")
                    .password("encodedPassword")
                    .fullName("Creator User")
                    .oauthProvider("LOCAL")
                    .status("PENDING_VERIFICATION")
                    .roles(Set.of(creatorRole))
                    .build();
            creatorUser.setId("user-200");

            UserResponse creatorResponse = UserResponse.builder()
                    .id("user-200")
                    .username("creator01")
                    .email("creator@email.com")
                    .fullName("Creator User")
                    .roles(Set.of("ROLE_CREATOR"))
                    .build();

            when(userRepository.existsByUsernameAnywhere("creator01")).thenReturn(false);
            when(userRepository.existsByEmailAnywhere("creator@email.com")).thenReturn(false);
            when(roleRepository.findByName("ROLE_CREATOR")).thenReturn(Optional.of(creatorRole));
            when(passwordEncoder.encode("RawP@ss1")).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenReturn(creatorUser);
            when(userMapper.toUserResponse(creatorUser)).thenReturn(creatorResponse);
            stubRedisForGenerateAndStoreOtp();

            UserResponse response = authService.register(request);

            assertNotNull(response);
            assertEquals("creator01", response.getUsername());
            verify(roleRepository).findByName("ROLE_CREATOR");
        }

        @Test
        @DisplayName("Fail - username already exists")
        void register_usernameExists_throwsUsernameExisted() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("testuser")
                    .email("test@email.com")
                    .password("RawP@ss1")
                    .fullName("Test User")
                    .build();

            when(userRepository.existsByUsernameAnywhere("testuser")).thenReturn(true);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.register(request));
            assertEquals(ErrorCode.USERNAME_EXISTED, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Fail - email already exists")
        void register_emailExists_throwsEmailExisted() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("newuser")
                    .email("test@email.com")
                    .password("RawP@ss1")
                    .fullName("Test User")
                    .build();

            when(userRepository.existsByUsernameAnywhere("newuser")).thenReturn(false);
            when(userRepository.existsByEmailAnywhere("test@email.com")).thenReturn(true);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.register(request));
            assertEquals(ErrorCode.EMAIL_EXISTED, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Fail - disposable email domain is rejected")
        void register_disposableEmail_throwsDisposableEmailNotAllowed() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("newuser")
                    .email("test@tempmail.com")
                    .password("RawP@ss1")
                    .fullName("Test User")
                    .build();

            AppException exception = assertThrows(AppException.class,
                    () -> authService.register(request));
            assertEquals(ErrorCode.DISPOSABLE_EMAIL_NOT_ALLOWED, exception.getErrorCode());
            verify(userRepository, never()).existsByUsernameAnywhere(anyString());
        }
    }

    // ====================================================================
    // 2. VERIFY OTP TESTS
    // ====================================================================
    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtpTests {

        @Test
        @DisplayName("Success - correct OTP activates account and returns tokens")
        void verifyOtp_success_returnsAuthResponse() {
            VerifyOtpRequest request = VerifyOtpRequest.builder()
                    .email("test@email.com")
                    .otpCode("123456")
                    .build();

            User pendingUser = User.builder()
                    .username("testuser")
                    .email("test@email.com")
                    .password("encodedPassword")
                    .fullName("Test User")
                    .oauthProvider("LOCAL")
                    .status("PENDING_VERIFICATION")
                    .roles(Set.of(defaultRole))
                    .build();
            pendingUser.setId("user-100");

            stubRedisForGenerateAuthResponse();
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("otp_attempts:test@email.com")).thenReturn(null);
            when(valueOperations.get("registration_otp:test@email.com")).thenReturn("123456");
            when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.of(pendingUser));
            when(userRepository.save(any(User.class))).thenReturn(pendingUser);
            when(redisTemplate.delete(anyString())).thenReturn(true);
            when(jwtTokenProvider.generateToken(eq("testuser"), anyString())).thenReturn("mockAccessToken");
            when(userMapper.toUserResponse(any(User.class))).thenReturn(testUserResponse);

            AuthResponse response = authService.verifyOtp(request);

            assertNotNull(response);
            assertNotNull(response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals("ACTIVE", pendingUser.getStatus());
        }

        @Test
        @DisplayName("Fail - wrong OTP code increments attempts")
        void verifyOtp_wrongOtp_throwsInvalidOtp() {
            VerifyOtpRequest request = VerifyOtpRequest.builder()
                    .email("test@email.com")
                    .otpCode("000000")
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("otp_attempts:test@email.com")).thenReturn("0");
            when(valueOperations.get("registration_otp:test@email.com")).thenReturn("123456");
            when(valueOperations.increment("otp_attempts:test@email.com")).thenReturn(1L);
            lenient().when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.verifyOtp(request));
            assertEquals(ErrorCode.INVALID_OTP, exception.getErrorCode());
        }

        @Test
        @DisplayName("Fail - OTP expired (not found in Redis)")
        void verifyOtp_expired_throwsOtpExpired() {
            VerifyOtpRequest request = VerifyOtpRequest.builder()
                    .email("test@email.com")
                    .otpCode("123456")
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("otp_attempts:test@email.com")).thenReturn(null);
            when(valueOperations.get("registration_otp:test@email.com")).thenReturn(null);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.verifyOtp(request));
            assertEquals(ErrorCode.OTP_EXPIRED, exception.getErrorCode());
        }

        @Test
        @DisplayName("Fail - max OTP attempts exceeded")
        void verifyOtp_maxAttempts_throwsOtpAttemptsExceeded() {
            VerifyOtpRequest request = VerifyOtpRequest.builder()
                    .email("test@email.com")
                    .otpCode("000000")
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("otp_attempts:test@email.com")).thenReturn("5");
            when(redisTemplate.delete("registration_otp:test@email.com")).thenReturn(true);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.verifyOtp(request));
            assertEquals(ErrorCode.OTP_ATTEMPTS_EXCEEDED, exception.getErrorCode());
        }
    }

    // ====================================================================
    // 3. LOGIN TESTS
    // ====================================================================
    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("Success - valid credentials return tokens")
        void login_success_returnsAuthResponse() {
            LoginRequest request = LoginRequest.builder()
                    .usernameOrEmail("testuser")
                    .password("RawP@ss1")
                    .build();

            when(redisTemplate.hasKey("login_lockout:testuser")).thenReturn(false);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("RawP@ss1", "encodedPassword")).thenReturn(true);
            when(redisTemplate.delete("login_attempts:testuser")).thenReturn(true);
            when(jwtTokenProvider.generateToken(eq("testuser"), anyString())).thenReturn("mockAccessToken");
            stubRedisForGenerateAuthResponse();
            when(userMapper.toUserResponse(testUser)).thenReturn(testUserResponse);

            AuthResponse response = authService.login(request);

            assertNotNull(response);
            assertEquals("mockAccessToken", response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals("testuser", response.getUser().getUsername());
            verify(redisTemplate).delete("login_attempts:testuser");
        }

        @Test
        @DisplayName("Fail - wrong password throws UNAUTHENTICATED")
        void login_wrongPassword_throwsUnauthenticated() {
            LoginRequest request = LoginRequest.builder()
                    .usernameOrEmail("testuser")
                    .password("wrongPassword")
                    .build();

            when(redisTemplate.hasKey("login_lockout:testuser")).thenReturn(false);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);
            stubRedisForIncrementLoginAttempts();

            AppException exception = assertThrows(AppException.class,
                    () -> authService.login(request));
            assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
        }

        @Test
        @DisplayName("Fail - user not found throws UNAUTHENTICATED")
        void login_userNotFound_throwsUnauthenticated() {
            LoginRequest request = LoginRequest.builder()
                    .usernameOrEmail("unknown")
                    .password("RawP@ss1")
                    .build();

            when(redisTemplate.hasKey("login_lockout:unknown")).thenReturn(false);
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("unknown")).thenReturn(Optional.empty());
            stubRedisForIncrementLoginAttempts();

            AppException exception = assertThrows(AppException.class,
                    () -> authService.login(request));
            assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
        }

        @Test
        @DisplayName("Fail - account pending verification")
        void login_accountPending_throwsAccountPendingVerification() {
            LoginRequest request = LoginRequest.builder()
                    .usernameOrEmail("testuser")
                    .password("RawP@ss1")
                    .build();

            User pendingUser = User.builder()
                    .username("testuser")
                    .email("test@email.com")
                    .password("encodedPassword")
                    .status("PENDING_VERIFICATION")
                    .roles(Set.of(defaultRole))
                    .build();

            when(redisTemplate.hasKey("login_lockout:testuser")).thenReturn(false);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(pendingUser));
            when(passwordEncoder.matches("RawP@ss1", "encodedPassword")).thenReturn(true);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.login(request));
            assertEquals(ErrorCode.ACCOUNT_PENDING_VERIFICATION, exception.getErrorCode());
        }

        @Test
        @DisplayName("Fail - account banned")
        void login_accountBanned_throwsAccountBanned() {
            LoginRequest request = LoginRequest.builder()
                    .usernameOrEmail("testuser")
                    .password("RawP@ss1")
                    .build();

            User bannedUser = User.builder()
                    .username("testuser")
                    .email("test@email.com")
                    .password("encodedPassword")
                    .status("BANNED")
                    .roles(Set.of(defaultRole))
                    .build();

            when(redisTemplate.hasKey("login_lockout:testuser")).thenReturn(false);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(bannedUser));
            when(passwordEncoder.matches("RawP@ss1", "encodedPassword")).thenReturn(true);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.login(request));
            assertEquals(ErrorCode.ACCOUNT_BANNED, exception.getErrorCode());
        }

        @Test
        @DisplayName("Fail - account temporarily locked")
        void login_accountLocked_throwsAccountTemporarilyLocked() {
            LoginRequest request = LoginRequest.builder()
                    .usernameOrEmail("testuser")
                    .password("RawP@ss1")
                    .build();

            when(redisTemplate.hasKey("login_lockout:testuser")).thenReturn(true);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.login(request));
            assertEquals(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED, exception.getErrorCode());
            verify(userRepository, never()).findByUsername(anyString());
        }
    }

    // ====================================================================
    // 4. REFRESH TESTS
    // ====================================================================
    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("Success - valid refresh token returns new token pair")
        void refresh_success_returnsNewAuthResponse() {
            RefreshRequest request = RefreshRequest.builder()
                    .refreshToken("old-refresh-token")
                    .build();

            stubRedisForGenerateAuthResponse();
            when(valueOperations.get("refresh_token:old-refresh-token")).thenReturn("testuser");
            when(redisTemplate.delete("refresh_token:old-refresh-token")).thenReturn(true);
            when(setOperations.remove("user_sessions:testuser", "old-refresh-token")).thenReturn(1L);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(jwtTokenProvider.generateToken(eq("testuser"), anyString())).thenReturn("newAccessToken");
            when(userMapper.toUserResponse(testUser)).thenReturn(testUserResponse);

            AuthResponse response = authService.refresh(request);

            assertNotNull(response);
            assertEquals("newAccessToken", response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertNotEquals("old-refresh-token", response.getRefreshToken());
            verify(redisTemplate).delete("refresh_token:old-refresh-token");
        }

        @Test
        @DisplayName("Fail - invalid/expired refresh token throws UNAUTHENTICATED")
        void refresh_invalidToken_throwsUnauthenticated() {
            RefreshRequest request = RefreshRequest.builder()
                    .refreshToken("expired-token")
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refresh_token:expired-token")).thenReturn(null);
            when(valueOperations.get("rotated_token:expired-token")).thenReturn(null);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.refresh(request));
            assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
            verify(userRepository, never()).findByUsername(anyString());
        }

        @Test
        @DisplayName("Fail - refresh token theft reuse purges all sessions and throws UNAUTHENTICATED")
        void refresh_tokenTheftReuse_purgesAllSessionsAndThrowsUnauthenticated() {
            RefreshRequest request = RefreshRequest.builder()
                    .refreshToken("stolen-token")
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refresh_token:stolen-token")).thenReturn(null);
            // Rotated history check: stolen-token was rotated previously by "testuser"
            when(valueOperations.get("rotated_token:stolen-token")).thenReturn("testuser");
            
            // Stub for purgeAllSessions
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("user_sessions:testuser")).thenReturn(Set.of("another-token"));
            when(redisTemplate.delete(anyString())).thenReturn(true);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.refresh(request));
            
            assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
            // Verify all sessions were purged
            verify(redisTemplate).delete("user_sessions:testuser");
            verify(redisTemplate).delete("refresh_token:another-token");
        }
    }

    // ====================================================================
    // 5. LOGOUT TESTS
    // ====================================================================
    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("Success - with valid access token, blacklists it")
        void logout_withAccessToken_blacklistsToken() {
            RefreshRequest request = RefreshRequest.builder()
                    .refreshToken("some-refresh-token")
                    .build();
            String accessToken = "valid-access-token";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(valueOperations.get("refresh_token:some-refresh-token")).thenReturn("testuser");
            when(redisTemplate.delete("refresh_token:some-refresh-token")).thenReturn(true);
            when(setOperations.remove("user_sessions:testuser", "some-refresh-token")).thenReturn(1L);
            when(jwtTokenProvider.validateToken(accessToken)).thenReturn(true);
            when(jwtTokenProvider.getExpirationFromToken(accessToken))
                    .thenReturn(new Date(System.currentTimeMillis() + 3600000));

            authService.logout(request, accessToken);

            verify(redisTemplate).delete("refresh_token:some-refresh-token");
            verify(setOperations).remove("user_sessions:testuser", "some-refresh-token");
            verify(valueOperations).set(eq("blacklist_token:" + accessToken), eq("true"), any(Duration.class));
        }

        @Test
        @DisplayName("Success - without access token, only removes refresh token")
        void logout_withoutAccessToken_onlyRemovesRefreshToken() {
            RefreshRequest request = RefreshRequest.builder()
                    .refreshToken("some-refresh-token")
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(valueOperations.get("refresh_token:some-refresh-token")).thenReturn("testuser");
            when(redisTemplate.delete("refresh_token:some-refresh-token")).thenReturn(true);
            when(setOperations.remove("user_sessions:testuser", "some-refresh-token")).thenReturn(1L);

            authService.logout(request, null);

            verify(redisTemplate).delete("refresh_token:some-refresh-token");
            verify(jwtTokenProvider, never()).validateToken(anyString());
        }
    }

    // ====================================================================
    // 6. GET PROFILE TESTS
    // ====================================================================
    @Nested
    @DisplayName("getProfile()")
    class GetProfileTests {

        @Test
        @DisplayName("Success - returns user profile")
        void getProfile_success_returnsUserResponse() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userMapper.toUserResponse(testUser)).thenReturn(testUserResponse);

            UserResponse response = authService.getProfile("testuser");

            assertNotNull(response);
            assertEquals("testuser", response.getUsername());
            assertEquals("test@email.com", response.getEmail());
            assertEquals("Test User", response.getFullName());
        }

        @Test
        @DisplayName("Fail - user not found")
        void getProfile_userNotFound_throwsUserNotExisted() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class,
                    () -> authService.getProfile("ghost"));
            assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
        }
    }

    // ====================================================================
    // 7. UPDATE PROFILE TESTS
    // ====================================================================
    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfileTests {

        @Test
        @DisplayName("Success - updates fullName")
        void updateProfile_withFullName_updatesAndReturns() {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .fullName("New Name")
                    .build();

            User updatedUser = User.builder()
                    .username("testuser")
                    .email("test@email.com")
                    .password("encodedPassword")
                    .fullName("New Name")
                    .oauthProvider("LOCAL")
                    .status("ACTIVE")
                    .roles(Set.of(defaultRole))
                    .build();
            updatedUser.setId("user-100");

            UserResponse updatedResponse = UserResponse.builder()
                    .id("user-100")
                    .username("testuser")
                    .email("test@email.com")
                    .fullName("New Name")
                    .roles(Set.of("ROLE_USER"))
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);
            when(userMapper.toUserResponse(updatedUser)).thenReturn(updatedResponse);

            AuthResponse response = authService.updateProfile("testuser", request);

            assertNotNull(response);
            assertNull(response.getAccessToken());
            assertEquals("New Name", response.getUser().getFullName());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Success - updates avatarUrl")
        void updateProfile_withAvatarUrl_updatesAndReturns() {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .avatarUrl("https://cdn.example.com/avatar.jpg")
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toUserResponse(testUser)).thenReturn(testUserResponse);

            AuthResponse response = authService.updateProfile("testuser", request);

            assertNotNull(response);
            assertNull(response.getAccessToken());
            assertEquals("https://cdn.example.com/avatar.jpg", testUser.getAvatarUrl());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Success - updates username and performs JWT rotation")
        void updateProfile_withNewUsername_updatesAndRotatesJWT() {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .username("newusername")
                    .build();

            User updatedUser = User.builder()
                    .username("newusername")
                    .email("test@email.com")
                    .password("encodedPassword")
                    .fullName("Test User")
                    .oauthProvider("LOCAL")
                    .status("ACTIVE")
                    .roles(Set.of(defaultRole))
                    .build();
            updatedUser.setId("user-100");

            UserResponse updatedResponse = UserResponse.builder()
                    .id("user-100")
                    .username("newusername")
                    .email("test@email.com")
                    .fullName("Test User")
                    .roles(Set.of("ROLE_USER"))
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.existsByUsernameAnywhere("newusername")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);
            when(userMapper.toUserResponse(updatedUser)).thenReturn(updatedResponse);
            when(jwtTokenProvider.generateToken(eq("newusername"), anyString())).thenReturn("newAccessToken");

            stubRedisForPurgeAllSessions();
            stubRedisForGenerateAuthResponse();

            AuthResponse response = authService.updateProfile("testuser", request);

            assertNotNull(response);
            assertEquals("newAccessToken", response.getAccessToken());
            assertEquals("newusername", response.getUser().getUsername());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Failure - username already exists")
        void updateProfile_usernameExists_throwsException() {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .username("existingusername")
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.existsByUsernameAnywhere("existingusername")).thenReturn(true);

            AppException ex = assertThrows(AppException.class, () ->
                    authService.updateProfile("testuser", request)
            );
            assertEquals(ErrorCode.USERNAME_EXISTED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Failure - username too short")
        void updateProfile_usernameTooShort_throwsException() {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .username("abcd")
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            AppException ex = assertThrows(AppException.class, () ->
                    authService.updateProfile("testuser", request)
            );
            assertEquals(ErrorCode.USERNAME_INVALID, ex.getErrorCode());
        }
    }

    // ====================================================================
    // 8. CHANGE PASSWORD TESTS
    // ====================================================================
    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("Success - changes password and purges sessions")
        void changePassword_success_updatesPasswordAndPurgesSessions() {
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("RawP@ss1")
                    .newPassword("NewP@ss2")
                    .confirmPassword("NewP@ss2")
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("RawP@ss1", "encodedPassword")).thenReturn(true);
            when(passwordEncoder.matches("NewP@ss2", "encodedPassword")).thenReturn(false);
            when(passwordEncoder.encode("NewP@ss2")).thenReturn("newEncodedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            stubRedisForPurgeAllSessions();

            assertDoesNotThrow(() -> authService.changePassword("testuser", request));

            verify(passwordEncoder).encode("NewP@ss2");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Fail - wrong old password")
        void changePassword_wrongOldPassword_throwsOldPasswordIncorrect() {
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("wrongOld")
                    .newPassword("NewP@ss2")
                    .confirmPassword("NewP@ss2")
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongOld", "encodedPassword")).thenReturn(false);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.changePassword("testuser", request));
            assertEquals(ErrorCode.OLD_PASSWORD_INCORRECT, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Fail - new password and confirm password mismatch")
        void changePassword_mismatch_throwsPasswordMismatch() {
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("RawP@ss1")
                    .newPassword("NewP@ss2")
                    .confirmPassword("Different3")
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("RawP@ss1", "encodedPassword")).thenReturn(true);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.changePassword("testuser", request));
            assertEquals(ErrorCode.PASSWORD_MISMATCH, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Fail - new password same as old password")
        void changePassword_sameAsOld_throwsSamePassword() {
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("RawP@ss1")
                    .newPassword("RawP@ss1")
                    .confirmPassword("RawP@ss1")
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("RawP@ss1", "encodedPassword")).thenReturn(true);
            // matches(newPassword, storedPassword) also returns true because it's the same
            // The second call with newPassword against stored hash
            // First call: matches("RawP@ss1", "encodedPassword") → true (old pwd check)
            // Second call: matches("RawP@ss1", "encodedPassword") → true (same pwd check)
            // Since both calls use the same arguments, a single when() handles both

            AppException exception = assertThrows(AppException.class,
                    () -> authService.changePassword("testuser", request));
            assertEquals(ErrorCode.SAME_PASSWORD, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ====================================================================
    // 9. FORGOT PASSWORD TESTS
    // ====================================================================
    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Success - existing active email generates reset token")
        void forgotPassword_existingEmail_generatesResetToken() {
            ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                    .email("test@email.com")
                    .build();

            when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.of(testUser));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            assertDoesNotThrow(() -> authService.forgotPassword(request));

            verify(valueOperations).set(
                    startsWith("password_reset_token:"),
                    eq("test@email.com"),
                    eq(Duration.ofMinutes(10))
            );
        }

        @Test
        @DisplayName("Success - non-existing email does not reveal absence (anti-enumeration)")
        void forgotPassword_nonExistingEmail_noErrorRevealed() {
            ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                    .email("ghost@email.com")
                    .build();

            when(userRepository.findByEmail("ghost@email.com")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> authService.forgotPassword(request));

            verify(redisTemplate, never()).opsForValue();
        }
    }

    // ====================================================================
    // 10. RESET PASSWORD TESTS
    // ====================================================================
    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("Success - valid reset token resets password and purges sessions")
        void resetPassword_success_updatesPasswordAndPurgesSessions() {
            ResetPasswordRequest request = ResetPasswordRequest.builder()
                    .token("valid-reset-token")
                    .newPassword("NewP@ss2")
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("password_reset_token:valid-reset-token")).thenReturn("test@email.com");
            when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.encode("NewP@ss2")).thenReturn("newEncodedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            lenient().when(redisTemplate.delete("password_reset_token:valid-reset-token")).thenReturn(true);
            stubRedisForPurgeAllSessions();

            assertDoesNotThrow(() -> authService.resetPassword(request));

            verify(passwordEncoder).encode("NewP@ss2");
            verify(userRepository).save(any(User.class));
            verify(redisTemplate).delete("password_reset_token:valid-reset-token");
        }

        @Test
        @DisplayName("Fail - invalid/expired reset token")
        void resetPassword_invalidToken_throwsInvalidResetToken() {
            ResetPasswordRequest request = ResetPasswordRequest.builder()
                    .token("expired-token")
                    .newPassword("NewP@ss2")
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("password_reset_token:expired-token")).thenReturn(null);

            AppException exception = assertThrows(AppException.class,
                    () -> authService.resetPassword(request));
            assertEquals(ErrorCode.INVALID_RESET_TOKEN, exception.getErrorCode());
            verify(userRepository, never()).findByEmail(anyString());
        }
    }

    // ====================================================================
    // 11. DELETE ACCOUNT TESTS
    // ====================================================================
    @Nested
    @DisplayName("deleteAccount()")
    class DeleteAccountTests {

        @Test
        @DisplayName("Success - sets status to PENDING_DELETION and purges sessions")
        void deleteAccount_success_setsPendingDeletionAndPurges() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            stubRedisForPurgeAllSessions();

            assertDoesNotThrow(() -> authService.deleteAccount("testuser"));

            assertEquals("PENDING_DELETION", testUser.getStatus());
            assertTrue(testUser.isDeleted());
            verify(userRepository).save(testUser);
        }
    }

    // ====================================================================
    // 12. UPDATE USER STATUS TESTS (Admin)
    // ====================================================================
    @Nested
    @DisplayName("updateUserStatus()")
    class UpdateUserStatusTests {

        @Test
        @DisplayName("Success - admin updates user status to BANNED")
        void updateUserStatus_success_updatesStatusAndReturns() {
            UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                    .status("BANNED")
                    .build();

            User targetUser = User.builder()
                    .username("targetuser")
                    .email("target@email.com")
                    .status("ACTIVE")
                    .roles(Set.of(defaultRole))
                    .build();
            targetUser.setId("user-200");

            UserResponse targetResponse = UserResponse.builder()
                    .id("user-200")
                    .username("targetuser")
                    .status("BANNED")
                    .roles(Set.of("ROLE_USER"))
                    .build();

            when(userRepository.findById("user-200")).thenReturn(Optional.of(targetUser));
            when(userRepository.save(any(User.class))).thenReturn(targetUser);
            when(userMapper.toUserResponse(targetUser)).thenReturn(targetResponse);
            stubRedisForPurgeAllSessions();

            UserResponse response = authService.updateUserStatus("user-200", request, "adminuser");

            assertNotNull(response);
            assertEquals("BANNED", response.getStatus());
            assertEquals("BANNED", targetUser.getStatus());
        }

        @Test
        @DisplayName("Fail - cannot change own status")
        void updateUserStatus_ownStatus_throwsCannotChangeOwnStatus() {
            UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                    .status("BANNED")
                    .build();

            User adminUser = User.builder()
                    .username("adminuser")
                    .email("admin@email.com")
                    .status("ACTIVE")
                    .roles(Set.of(defaultRole))
                    .build();
            adminUser.setId("admin-1");

            when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));

            AppException exception = assertThrows(AppException.class,
                    () -> authService.updateUserStatus("admin-1", request, "adminuser"));
            assertEquals(ErrorCode.CANNOT_CHANGE_OWN_STATUS, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Fail - invalid status value")
        void updateUserStatus_invalidStatus_throwsInvalidStatus() {
            UpdateUserStatusRequest request = UpdateUserStatusRequest.builder()
                    .status("INVALID_STATUS_VALUE")
                    .build();

            User targetUser = User.builder()
                    .username("targetuser")
                    .email("target@email.com")
                    .status("ACTIVE")
                    .roles(Set.of(defaultRole))
                    .build();
            targetUser.setId("user-200");

            when(userRepository.findById("user-200")).thenReturn(Optional.of(targetUser));

            AppException exception = assertThrows(AppException.class,
                    () -> authService.updateUserStatus("user-200", request, "adminuser"));
            assertEquals(ErrorCode.INVALID_STATUS, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ====================================================================
    // 13. SEARCH USERS TESTS (Admin)
    // ====================================================================
    @Nested
    @DisplayName("searchUsers()")
    class SearchUsersTests {

        @Test
        @DisplayName("Success - search users with parameters")
        void searchUsers_success() {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
            User user1 = User.builder().username("user1").email("u1@email.com").build();
            org.springframework.data.domain.Page<User> mockPage = new org.springframework.data.domain.PageImpl<>(List.of(user1));

            UserResponse mockResponse = UserResponse.builder().id("1").username("user1").email("u1@email.com").build();

            when(userRepository.searchUsers("%test%", "ACTIVE", "ROLE_USER", pageable)).thenReturn(mockPage);
            when(userMapper.toUserResponse(user1)).thenReturn(mockResponse);

            org.springframework.data.domain.Page<UserResponse> result = authService.searchUsers("test", "ACTIVE", "ROLE_USER", pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals("user1", result.getContent().get(0).getUsername());
        }
    }

    // ====================================================================
    // 14. UPDATE USER ROLES TESTS (Admin)
    // ====================================================================
    @Nested
    @DisplayName("updateUserRoles()")
    class UpdateUserRolesTests {

        @Test
        @DisplayName("Success - admin updates user roles")
        void updateUserRoles_success() {
            UpdateUserRolesRequest request = UpdateUserRolesRequest.builder()
                    .roles(Set.of("ROLE_CREATOR"))
                    .build();

            User targetUser = User.builder()
                    .username("targetuser")
                    .email("target@email.com")
                    .roles(new HashSet<>(Set.of(defaultRole)))
                    .build();
            targetUser.setId("user-200");

            UserResponse targetResponse = UserResponse.builder()
                    .id("user-200")
                    .username("targetuser")
                    .roles(Set.of("ROLE_CREATOR"))
                    .build();

            when(userRepository.findById("user-200")).thenReturn(Optional.of(targetUser));
            when(roleRepository.findByName("ROLE_CREATOR")).thenReturn(Optional.of(creatorRole));
            when(userRepository.save(any(User.class))).thenReturn(targetUser);
            when(userMapper.toUserResponse(targetUser)).thenReturn(targetResponse);
            stubRedisForPurgeAllSessions();

            UserResponse response = authService.updateUserRoles("user-200", request, "adminuser");

            assertNotNull(response);
            assertTrue(response.getRoles().contains("ROLE_CREATOR"));
            verify(userRepository).save(targetUser);
        }

        @Test
        @DisplayName("Fail - admin cannot change own roles")
        void updateUserRoles_ownRoles_throwsCannotChangeOwnRole() {
            UpdateUserRolesRequest request = UpdateUserRolesRequest.builder()
                    .roles(Set.of("ROLE_USER"))
                    .build();

            User adminUser = User.builder()
                    .username("adminuser")
                    .email("admin@email.com")
                    .roles(new HashSet<>(Set.of(defaultRole)))
                    .build();
            adminUser.setId("admin-1");

            when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));

            AppException exception = assertThrows(AppException.class,
                    () -> authService.updateUserRoles("admin-1", request, "adminuser"));
            assertEquals(ErrorCode.CANNOT_CHANGE_OWN_ROLE, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }
    }
}
