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



    /** Yêu cầu khôi phục mật khẩu → sinh UUID Reset Token → gửi email */
    void forgotPassword(ForgotPasswordRequest request);

    /** Đặt lại mật khẩu bằng Reset Token → kick all sessions */
    void resetPassword(ResetPasswordRequest request);
}
