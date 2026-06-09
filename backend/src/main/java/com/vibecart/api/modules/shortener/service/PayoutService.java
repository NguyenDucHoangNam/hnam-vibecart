package com.vibecart.api.modules.shortener.service;

import com.vibecart.api.modules.shortener.dto.request.PayoutApproveRequest;
import com.vibecart.api.modules.shortener.dto.request.PayoutRequestDto;
import com.vibecart.api.modules.shortener.dto.response.PayoutResponse;

import java.util.List;

/**
 * Service quản lý yêu cầu rút tiền hoa hồng từ chương trình tiếp thị liên kết.
 */
public interface PayoutService {
    /**
     * Tạo yêu cầu rút tiền hoa hồng.
     */
    PayoutResponse createPayoutRequest(PayoutRequestDto requestDto, String currentUsername);

    /**
     * Lấy danh sách yêu cầu rút tiền của Creator hiện tại.
     */
    List<PayoutResponse> getMyPayoutRequests(String currentUsername);

    /**
     * Lấy danh sách yêu cầu rút tiền theo trạng thái (Admin).
     */
    List<PayoutResponse> getPayoutRequestsByStatus(String status);

    /**
     * Phê duyệt hoặc từ chối yêu cầu rút tiền (Admin).
     */
    PayoutResponse approveOrRejectPayout(String requestId, PayoutApproveRequest approveRequest);
}
