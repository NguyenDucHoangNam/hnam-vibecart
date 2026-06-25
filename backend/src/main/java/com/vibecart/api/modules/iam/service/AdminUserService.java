package com.vibecart.api.modules.iam.service;

import com.vibecart.api.modules.iam.dto.request.UpdateUserRolesRequest;
import com.vibecart.api.modules.iam.dto.request.UpdateUserStatusRequest;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service quản trị người dùng dành cho Admin.
 */
public interface AdminUserService {
    Page<UserResponse> searchUsers(String search, String status, String role, Pageable pageable);
    UserResponse updateUserStatus(String userId, UpdateUserStatusRequest request, String adminUsername);
    UserResponse updateUserRoles(String userId, UpdateUserRolesRequest request, String adminUsername);
}
