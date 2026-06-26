package com.vibecart.api.modules.shortener.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.shortener.dto.request.PayoutRequestDto;
import com.vibecart.api.modules.shortener.dto.request.ShortlinkCreateRequest;
import com.vibecart.api.modules.shortener.dto.response.DashboardResponse;
import com.vibecart.api.modules.shortener.dto.response.PayoutResponse;
import com.vibecart.api.modules.shortener.dto.response.ShortlinkResponse;
import com.vibecart.api.modules.shortener.service.DashboardService;
import com.vibecart.api.modules.shortener.service.PayoutService;
import com.vibecart.api.modules.shortener.service.ShortLinkService;
import com.vibecart.api.common.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/v1/affiliate")
@RequiredArgsConstructor
@Slf4j
public class AffiliateController {

    private final ShortLinkService shortLinkService;
    private final DashboardService dashboardService;
    private final PayoutService payoutService;
    @PostMapping("/shortlinks")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<ShortlinkResponse>> createShortLink(
            @Valid @RequestBody ShortlinkCreateRequest request) {

        String username = SecurityUtils.getCurrentUsername();
        log.info("Creator {} is creating a short link for product {}", username, request.getProductId());

        ShortlinkResponse result = shortLinkService.createShortLink(request, username);

        ApiResponse<ShortlinkResponse> response = ApiResponse.<ShortlinkResponse>builder()
                .code(1000)
                .message("Tạo link tiếp thị liên kết thành công")
                .result(result)
                .build();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/shortlinks")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<List<ShortlinkResponse>>> getMyShortLinks() {

        String username = SecurityUtils.getCurrentUsername();
        log.info("Creator {} is fetching short links list", username);

        List<ShortlinkResponse> result = shortLinkService.getMyShortLinks(username);

        ApiResponse<List<ShortlinkResponse>> response = ApiResponse.<List<ShortlinkResponse>>builder()
                .code(1000)
                .message("Lấy danh sách link tiếp thị thành công")
                .result(result)
                .build();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboardMetrics() {

        String username = SecurityUtils.getCurrentUsername();
        log.info("Creator {} is fetching affiliate dashboard metrics", username);

        DashboardResponse result = dashboardService.getDashboardMetrics(username);

        ApiResponse<DashboardResponse> response = ApiResponse.<DashboardResponse>builder()
                .code(1000)
                .message("Lấy thông tin dashboard tiếp thị thành công")
                .result(result)
                .build();

        return ResponseEntity.ok(response);
    }
    @PostMapping("/payouts")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<PayoutResponse>> requestPayout(
            @Valid @RequestBody PayoutRequestDto requestDto) {

        String username = SecurityUtils.getCurrentUsername();
        log.info("Creator {} requesting withdrawal of {} VND", username, requestDto.getAmount());

        PayoutResponse result = payoutService.createPayoutRequest(requestDto, username);

        ApiResponse<PayoutResponse> response = ApiResponse.<PayoutResponse>builder()
                .code(1000)
                .message("Gửi yêu cầu rút tiền thành công")
                .result(result)
                .build();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/payouts")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> getMyPayoutRequests() {

        String username = SecurityUtils.getCurrentUsername();
        log.info("Creator {} is fetching payout request history", username);

        List<PayoutResponse> result = payoutService.getMyPayoutRequests(username);

        ApiResponse<List<PayoutResponse>> response = ApiResponse.<List<PayoutResponse>>builder()
                .code(1000)
                .message("Lấy danh sách lịch sử yêu cầu rút tiền thành công")
                .result(result)
                .build();

        return ResponseEntity.ok(response);
    }
}
