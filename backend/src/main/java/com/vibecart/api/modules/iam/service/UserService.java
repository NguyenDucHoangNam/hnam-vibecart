package com.vibecart.api.modules.iam.service;

import com.vibecart.api.modules.iam.dto.request.ChangePasswordRequest;
import com.vibecart.api.modules.iam.dto.request.UpdateProfileRequest;
import com.vibecart.api.modules.iam.dto.response.AuthResponse;
import com.vibecart.api.modules.iam.dto.response.UserResponse;

/**
 * Service quản lý thông tin cá nhân của User.
 */
public interface UserService {
    UserResponse getProfile(String username);
    AuthResponse updateProfile(String oldUsername, UpdateProfileRequest request);
    void changePassword(String username, ChangePasswordRequest request);
    void deleteAccount(String username);
}
