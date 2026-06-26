package com.vibecart.api.modules.iam.service;

import com.vibecart.api.modules.iam.dto.request.*;
import com.vibecart.api.modules.iam.dto.response.*;
public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthResponse verifyOtp(VerifyOtpRequest request);
    void resendOtp(ResendOtpRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse loginGoogle(OAuth2Request request);
    AuthResponse loginFacebook(OAuth2Request request);
    AuthResponse refresh(RefreshRequest request);
    void logout(RefreshRequest request, String accessToken);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
