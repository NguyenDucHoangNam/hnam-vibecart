package com.vibecart.api.modules.iam.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.dto.request.UpdateUserRolesRequest;
import com.vibecart.api.modules.iam.dto.request.UpdateUserStatusRequest;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.mapper.UserMapper;
import com.vibecart.api.modules.iam.repository.RoleRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SearchService searchService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private Role defaultRole;
    private Role creatorRole;
    private User testUser;
    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() {
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

    private void stubRedisForPurgeAllSessions() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(setOperations.members(anyString())).thenReturn(Set.of("some-refresh-token"));
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
    }

    // ====================================================================
    // UPDATE USER STATUS TESTS (Admin)
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

            UserResponse response = adminUserService.updateUserStatus("user-200", request, "adminuser");

            assertNotNull(response);
            assertEquals("BANNED", response.getStatus());
            assertEquals("BANNED", targetUser.getStatus());
            verify(searchService).indexUser(targetUser);
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
                    () -> adminUserService.updateUserStatus("admin-1", request, "adminuser"));
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
                    () -> adminUserService.updateUserStatus("user-200", request, "adminuser"));
            assertEquals(ErrorCode.INVALID_STATUS, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ====================================================================
    // SEARCH USERS TESTS (Admin)
    // ====================================================================
    @Nested
    @DisplayName("searchUsers()")
    class SearchUsersTests {

        @Test
        @DisplayName("Success - search users with parameters")
        void searchUsers_success() {
            Pageable pageable = PageRequest.of(0, 10);
            User user1 = User.builder().username("user1").email("u1@email.com").build();
            Page<User> mockPage = new PageImpl<>(List.of(user1));

            UserResponse mockResponse = UserResponse.builder().id("1").username("user1").email("u1@email.com").build();

            when(userRepository.searchUsers("%test%", "ACTIVE", "ROLE_USER", pageable)).thenReturn(mockPage);
            when(userMapper.toUserResponse(user1)).thenReturn(mockResponse);

            Page<UserResponse> result = adminUserService.searchUsers("test", "ACTIVE", "ROLE_USER", pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals("user1", result.getContent().get(0).getUsername());
        }
    }

    // ====================================================================
    // UPDATE USER ROLES TESTS (Admin)
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

            UserResponse response = adminUserService.updateUserRoles("user-200", request, "adminuser");

            assertNotNull(response);
            assertTrue(response.getRoles().contains("ROLE_CREATOR"));
            verify(userRepository).save(targetUser);
            verify(searchService).indexUser(targetUser);
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
                    () -> adminUserService.updateUserRoles("admin-1", request, "adminuser"));
            assertEquals(ErrorCode.CANNOT_CHANGE_OWN_ROLE, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }
    }
}
