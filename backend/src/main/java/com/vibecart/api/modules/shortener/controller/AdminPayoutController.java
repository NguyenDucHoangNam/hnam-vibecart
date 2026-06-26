package com.vibecart.api.modules.shortener.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.shortener.dto.request.PayoutApproveRequest;
import com.vibecart.api.modules.shortener.dto.response.PayoutResponse;
import com.vibecart.api.modules.shortener.service.PayoutService;
import com.vibecart.api.common.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/v1/admin/payouts")
@RequiredArgsConstructor
@Slf4j
public class AdminPayoutController {

        private final PayoutService payoutService;
        @GetMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<List<PayoutResponse>>> getPayoutRequests(
                        @RequestParam(value = "status", required = false, defaultValue = "ALL") String status) {

                String adminUsername = SecurityUtils.getCurrentUsername();
                log.info("Admin {} is listing payout requests with status filtering: {}", adminUsername, status);

                List<PayoutResponse> result = payoutService.getPayoutRequestsByStatus(status);

                ApiResponse<List<PayoutResponse>> response = ApiResponse.<List<PayoutResponse>>builder()
                                .code(1000)
                                .message("Lấy danh sách yêu cầu rút tiền thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
        @PutMapping("/{id}/approve")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<PayoutResponse>> approvePayout(
                        @PathVariable("id") String id,
                        @Valid @RequestBody PayoutApproveRequest approveRequest) {

                String adminUsername = SecurityUtils.getCurrentUsername();
                log.info("Admin {} is approving/rejecting payout request ID: {} with decision: {}",
                                adminUsername, id, approveRequest.getStatus());

                PayoutResponse result = payoutService.approveOrRejectPayout(id, approveRequest);

                ApiResponse<PayoutResponse> response = ApiResponse.<PayoutResponse>builder()
                                .code(1000)
                                .message("Xử lý yêu cầu rút tiền thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
}
