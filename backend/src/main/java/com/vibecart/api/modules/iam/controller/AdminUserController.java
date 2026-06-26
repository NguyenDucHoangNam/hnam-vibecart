package com.vibecart.api.modules.iam.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.iam.dto.request.UpdateUserRolesRequest;
import com.vibecart.api.modules.iam.dto.request.UpdateUserStatusRequest;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.service.AdminUserService;
import com.vibecart.api.common.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

        private final AdminUserService adminUserService;
        @GetMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Page<UserResponse>>> searchUsers(
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String role,
                        @PageableDefault(size = 10) Pageable pageable) {

                log.info("Admin request to search users - search: {}, status: {}, role: {}", search, status, role);
                Page<UserResponse> result = adminUserService.searchUsers(search, status, role, pageable);

                ApiResponse<Page<UserResponse>> response = ApiResponse.<Page<UserResponse>>builder()
                                .code(1000)
                                .message("Lấy danh sách người dùng thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PutMapping("/{id}/status")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
                        @PathVariable String id,
                        @Valid @RequestBody UpdateUserStatusRequest request) {

                String adminUsername = SecurityUtils.getCurrentUsername();
                log.info("Admin {} request to update user {} status to {}", adminUsername, id, request.getStatus());

                UserResponse result = adminUserService.updateUserStatus(id, request, adminUsername);

                ApiResponse<UserResponse> response = ApiResponse.<UserResponse>builder()
                                .code(1000)
                                .message("Cập nhật trạng thái tài khoản thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PutMapping("/{id}/roles")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<UserResponse>> updateUserRoles(
                        @PathVariable String id,
                        @Valid @RequestBody UpdateUserRolesRequest request) {

                String adminUsername = SecurityUtils.getCurrentUsername();
                log.info("Admin {} request to update user {} roles to {}", adminUsername, id, request.getRoles());

                UserResponse result = adminUserService.updateUserRoles(id, request, adminUsername);

                ApiResponse<UserResponse> response = ApiResponse.<UserResponse>builder()
                                .code(1000)
                                .message("Cập nhật vai trò thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
}
