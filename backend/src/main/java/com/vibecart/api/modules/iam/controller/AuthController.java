package com.vibecart.api.modules.iam.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.iam.dto.request.*;
import com.vibecart.api.modules.iam.dto.response.*;
import com.vibecart.api.modules.iam.service.AuthService;
import com.vibecart.api.modules.iam.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý xác thực, đăng ký, đăng nhập, quản lý tài khoản người dùng.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

        private final AuthService authService;
        private final UserService userService;

        /**
         * Đăng ký tài khoản mới.
         */
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

        /**
         * Gửi lại mã OTP xác thực qua email.
         */
        public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
                log.info("API request to resend OTP for email: {}", request.getEmail());
                authService.resendOtp(request);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .code(1000)
                                .message("Mã OTP mới đã được gửi thành công, vui lòng kiểm tra hòm thư của bạn")
                                .build();

                return ResponseEntity.ok(response);
        }

        /**
         * Xác thực mã OTP để kích hoạt tài khoản.
         */
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

        /**
         * Đăng nhập bằng tài khoản nội bộ.
         */
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

        /**
         * Đăng nhập bằng tài khoản Google.
         */
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

        /**
         * Đăng nhập bằng tài khoản Facebook.
         */
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

        /**
         * Gia hạn access token bằng refresh token.
         */
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

        /**
         * Đăng xuất và vô hiệu hóa token.
         */
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

        /**
         * Lấy thông tin cá nhân của người dùng đang đăng nhập.
         */
        @GetMapping("/me")
        public ResponseEntity<ApiResponse<UserResponse>> getMyProfile() {
                log.info("API request to fetch current user profile");
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                UserResponse result = userService.getProfile(username);

                ApiResponse<UserResponse> response = ApiResponse.<UserResponse>builder()
                                .code(1000)
                                .message("Lấy thông tin cá nhân thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }

        /**
         * Đổi mật khẩu tài khoản.
         */
        @PostMapping("/change-password")
        public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
                log.info("API request to change password");
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                userService.changePassword(username, request);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .code(1000)
                                .message("Thay đổi mật khẩu thành công. Các thiết bị khác đã được đăng xuất để bảo mật")
                                .build();

                return ResponseEntity.ok(response);
        }

        /**
         * Cập nhật hồ sơ cá nhân.
         */
        @PutMapping("/profile")
        public ResponseEntity<ApiResponse<AuthResponse>> updateProfile(
                        @Valid @RequestBody UpdateProfileRequest request) {
                log.info("API request to update profile");
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                AuthResponse result = userService.updateProfile(username, request);

                ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                                .code(1000)
                                .message("Cập nhật thông tin cá nhân thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }

        /**
         * Gửi link khôi phục mật khẩu qua email.
         */
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

        /**
         * Đặt lại mật khẩu bằng token khôi phục.
         */
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

        /**
         * Yêu cầu xóa tài khoản (soft delete, đóng băng 30 ngày).
         */
        @DeleteMapping("/account")
        public ResponseEntity<ApiResponse<Void>> deleteAccount() {
                log.info("API request to delete account");
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                userService.deleteAccount(username);

                ApiResponse<Void> response = ApiResponse.<Void>builder()
                                .code(1000)
                                .message(
                                                "Yêu cầu xóa tài khoản thành công. Dữ liệu sẽ được đóng băng trong 30 ngày trước khi bị ẩn danh hóa vĩnh viễn.")
                                .build();

                return ResponseEntity.ok(response);
        }
}
