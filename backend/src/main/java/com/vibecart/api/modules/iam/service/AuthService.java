package com.vibecart.api.modules.iam.service;

import com.vibecart.api.modules.iam.dto.request.*;
import com.vibecart.api.modules.iam.dto.response.*;

/**
 * Service interface cho Phân hệ Tài khoản & Định danh (IAM).
 * Bao gồm đầy đủ các luồng: Đăng ký, OTP, Đăng nhập, OAuth2, Token, Profile, Password, Admin.
 */
public interface AuthService {



    /** Đăng ký tài khoản mới → trạng thái PENDING_VERIFICATION → sinh OTP gửi email */
    UserResponse register(RegisterRequest request);

    /** Xác thực mã OTP → kích hoạt tài khoản → tự động đăng nhập (trả token) */
    AuthResponse verifyOtp(VerifyOtpRequest request);

    /** Yêu cầu gửi lại mã OTP mới cho email đang chờ xác thực */
    void resendOtp(ResendOtpRequest request);



    /** Đăng nhập bằng Username/Email + Password (có login lockout & session control) */
    AuthResponse login(LoginRequest request);

    /** Đăng nhập bằng Google OAuth2 ID Token */
    AuthResponse loginGoogle(OAuth2Request request);

    /** Đăng nhập bằng Facebook OAuth2 Access Token */
    AuthResponse loginFacebook(OAuth2Request request);



    /** Gia hạn Access Token bằng Refresh Token (Token Rotation) */
    AuthResponse refresh(RefreshRequest request);

    /** Đăng xuất: Blacklist Access Token + Xóa Refresh Token */
    void logout(RefreshRequest request, String accessToken);



    /** Lấy thông tin cá nhân theo username (từ JWT) */
    UserResponse getProfile(String username);

    /** Cập nhật hồ sơ cá nhân (username, fullName, avatarUrl) */
    AuthResponse updateProfile(String username, UpdateProfileRequest request);



    /** Đổi mật khẩu (yêu cầu oldPassword) → kick all other sessions */
    void changePassword(String username, ChangePasswordRequest request);

    /** Yêu cầu khôi phục mật khẩu → sinh UUID Reset Token → gửi email */
    void forgotPassword(ForgotPasswordRequest request);

    /** Đặt lại mật khẩu bằng Reset Token → kick all sessions */
    void resetPassword(ResetPasswordRequest request);



    /** Yêu cầu xóa tài khoản → PENDING_DELETION → purge sessions */
    void deleteAccount(String username);



    /** Admin: Cập nhật trạng thái tài khoản (ACTIVE, BANNED, v.v.) */
    UserResponse updateUserStatus(String userId, UpdateUserStatusRequest request, String adminUsername);

    /** Admin: Tìm kiếm & Lọc danh sách người dùng phân trang */
    org.springframework.data.domain.Page<UserResponse> searchUsers(String search, String status, String role, org.springframework.data.domain.Pageable pageable);

    /** Admin: Cập nhật vai trò (phân quyền) người dùng */
    UserResponse updateUserRoles(String userId, com.vibecart.api.modules.iam.dto.request.UpdateUserRolesRequest request, String adminUsername);
}
