package com.vibecart.api.modules.iam.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.dto.request.ChangePasswordRequest;
import com.vibecart.api.modules.iam.dto.request.UpdateProfileRequest;
import com.vibecart.api.modules.iam.dto.response.AuthResponse;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.mapper.UserMapper;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.search.service.SearchService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SearchService searchService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private UserServiceImpl userService;

    private Role defaultRole;
    private User testUser;
    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() {
        defaultRole = Role.builder()
                .name("ROLE_USER")
                .description("Standard user")
                .build();
        defaultRole.setId("role-1");

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

    private void stubRedisForPurgeAllSessions() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(setOperations.members(anyString())).thenReturn(Set.of("some-refresh-token"));
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
    }

    // ====================================================================
    // GET PROFILE TESTS
    // ====================================================================
    @Nested
    @DisplayName("getProfile()")
    class GetProfileTests {

        @Test
        @DisplayName("Success - returns user profile")
        void getProfile_success_returnsUserResponse() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userMapper.toUserResponse(testUser)).thenReturn(testUserResponse);

            UserResponse response = userService.getProfile("testuser");

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
                    () -> userService.getProfile("ghost"));
            assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
        }
    }

    // ====================================================================
    // UPDATE PROFILE TESTS
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

            AuthResponse response = userService.updateProfile("testuser", request);

            assertNotNull(response);
            assertNull(response.getAccessToken());
            assertEquals("New Name", response.getUser().getFullName());
            verify(userRepository).save(any(User.class));
            verify(searchService).indexUser(updatedUser);
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

            AuthResponse response = userService.updateProfile("testuser", request);

            assertNotNull(response);
            assertNull(response.getAccessToken());
            assertEquals("https://cdn.example.com/avatar.jpg", testUser.getAvatarUrl());
            verify(userRepository).save(any(User.class));
            verify(searchService).indexUser(testUser);
        }

        @Test
        @DisplayName("Success - updates username and performs JWT purge")
        void updateProfile_withNewUsername_updatesAndPurgesJWT() {
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

            stubRedisForPurgeAllSessions();

            AuthResponse response = userService.updateProfile("testuser", request);

            assertNotNull(response);
            assertNull(response.getAccessToken());
            assertEquals("newusername", response.getUser().getUsername());
            verify(userRepository).save(any(User.class));
            verify(searchService).indexUser(updatedUser);
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
                    userService.updateProfile("testuser", request)
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
                    userService.updateProfile("testuser", request)
            );
            assertEquals(ErrorCode.USERNAME_INVALID, ex.getErrorCode());
        }
    }

    // ====================================================================
    // CHANGE PASSWORD TESTS
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

            assertDoesNotThrow(() -> userService.changePassword("testuser", request));

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
                    () -> userService.changePassword("testuser", request));
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
                    () -> userService.changePassword("testuser", request));
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

            AppException exception = assertThrows(AppException.class,
                    () -> userService.changePassword("testuser", request));
            assertEquals(ErrorCode.SAME_PASSWORD, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ====================================================================
    // DELETE ACCOUNT TESTS
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

            assertDoesNotThrow(() -> userService.deleteAccount("testuser"));

            assertEquals("PENDING_DELETION", testUser.getStatus());
            assertTrue(testUser.isDeleted());
            verify(userRepository).save(testUser);
            verify(searchService).deleteUser(testUser.getId());
        }
    }
}
