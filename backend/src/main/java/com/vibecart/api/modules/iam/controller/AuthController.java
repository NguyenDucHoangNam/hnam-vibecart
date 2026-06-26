package com.vibecart.api.modules.iam.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.iam.dto.request.*;
import com.vibecart.api.modules.iam.dto.response.*;
import com.vibecart.api.modules.iam.service.AuthService;
import com.vibecart.api.modules.iam.service.UserService;
import com.vibecart.api.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

        private final AuthService authService;
        private final UserService userService;
        @PostMapping("/register")
        public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
                log.info("API request to register user: {}", request.getUsername());
                UserResponse result = authService.register(request);

                ApiResponse<UserResponse> response = ApiResponse.<UserResponse>builder()
                                .code(1000)
                                .message("Đăng ký thành công, vui lòng kiểm tra email để lấy mã OTP xác thực")
                                .result(result)
                                .build();

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
                log.info("API request to resend OTP for email: {}", request.getEmail());
                authService.resendOtp(request);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .code(1000)
                                .message("Mã OTP mới đã được gửi thành công, vui lòng kiểm tra hòm thư của bạn")
                                .build();

                return ResponseEntity.ok(response);
        }
        @PostMapping("/verify-otp")
        public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
                log.info("API request to verify OTP for email: {}", request.getEmail());
                AuthResponse result = authService.verifyOtp(request);

                ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                                .code(1000)
                                .message("Xác thực kích hoạt tài khoản thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PostMapping("/login")
        public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
                log.info("API request to login user: {}", request.getUsernameOrEmail());
                AuthResponse result = authService.login(request);

                ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                                .code(1000)
                                .message("Đăng nhập thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PostMapping("/oauth2/google")
        public ResponseEntity<ApiResponse<AuthResponse>> loginGoogle(@Valid @RequestBody OAuth2Request request) {
                log.info("API request for Google social login");
                AuthResponse result = authService.loginGoogle(request);

                ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                                .code(1000)
                                .message("Đăng nhập bằng Google thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PostMapping("/oauth2/facebook")
        public ResponseEntity<ApiResponse<AuthResponse>> loginFacebook(@Valid @RequestBody OAuth2Request request) {
                log.info("API request for Facebook social login");
                AuthResponse result = authService.loginFacebook(request);

                ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                                .code(1000)
                                .message("Đăng nhập bằng Facebook thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PostMapping("/refresh")
        public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
                log.info("API request to refresh JWT access token");
                AuthResponse result = authService.refresh(request);

                ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                                .code(1000)
                                .message("Gia hạn token thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PostMapping("/logout")
        public ResponseEntity<ApiResponse<Void>> logout(
                        @Valid @RequestBody RefreshRequest request,
                        HttpServletRequest httpRequest) {

                log.info("API request to logout user");

                String authHeader = httpRequest.getHeader("Authorization");
                String accessToken = null;
                if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                        accessToken = authHeader.substring(7);
                }

                authService.logout(request, accessToken);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .code(1000)
                                .message("Đăng xuất thành công")
                                .build();

                return ResponseEntity.ok(response);
        }
        @GetMapping("/me")
        public ResponseEntity<ApiResponse<UserResponse>> getMyProfile() {
                log.info("API request to fetch current user profile");
                String username = SecurityUtils.getCurrentUsername();
                UserResponse result = userService.getProfile(username);

                ApiResponse<UserResponse> response = ApiResponse.<UserResponse>builder()
                                .code(1000)
                                .message("Lấy thông tin cá nhân thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PostMapping("/change-password")
        public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
                log.info("API request to change password");
                String username = SecurityUtils.getCurrentUsername();
                userService.changePassword(username, request);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .code(1000)
                                .message("Thay đổi mật khẩu thành công. Các thiết bị khác đã được đăng xuất để bảo mật")
                                .build();

                return ResponseEntity.ok(response);
        }
        @PutMapping("/profile")
        public ResponseEntity<ApiResponse<AuthResponse>> updateProfile(
                        @Valid @RequestBody UpdateProfileRequest request) {
                log.info("API request to update profile");
                String username = SecurityUtils.getCurrentUsername();
                AuthResponse result = userService.updateProfile(username, request);

                ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                                .code(1000)
                                .message("Cập nhật thông tin cá nhân thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PostMapping("/forgot-password")
        public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
                log.info("API request for forgot password");
                authService.forgotPassword(request);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .code(1000)
                                .message(
                                                "Nếu email tồn tại trên hệ thống, chúng tôi đã gửi liên kết khôi phục mật khẩu vào hòm thư của bạn")
                                .build();

                return ResponseEntity.ok(response);
        }
        @PostMapping("/reset-password")
        public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
                log.info("API request to reset password");
                authService.resetPassword(request);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .code(1000)
                                .message("Đặt lại mật khẩu thành công. Các phiên hoạt động khác đã được đăng xuất an toàn")
                                .build();

                return ResponseEntity.ok(response);
        }
        @DeleteMapping("/account")
        public ResponseEntity<ApiResponse<Void>> deleteAccount() {
                log.info("API request to delete account");
                String username = SecurityUtils.getCurrentUsername();
                userService.deleteAccount(username);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .code(1000)
                                .message(
                                                "Yêu cầu xóa tài khoản thành công. Dữ liệu sẽ được đóng băng trong 30 ngày trước khi bị ẩn danh hóa vĩnh viễn.")
                                .build();

                return ResponseEntity.ok(response);
        }
}
