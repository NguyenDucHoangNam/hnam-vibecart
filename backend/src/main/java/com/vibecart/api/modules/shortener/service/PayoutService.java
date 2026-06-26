package com.vibecart.api.modules.shortener.service;

import com.vibecart.api.modules.shortener.dto.request.PayoutApproveRequest;
import com.vibecart.api.modules.shortener.dto.request.PayoutRequestDto;
import com.vibecart.api.modules.shortener.dto.response.PayoutResponse;

import java.util.List;
public interface PayoutService {
    PayoutResponse createPayoutRequest(PayoutRequestDto requestDto, String currentUsername);
    List<PayoutResponse> getMyPayoutRequests(String currentUsername);
    List<PayoutResponse> getPayoutRequestsByStatus(String status);
    PayoutResponse approveOrRejectPayout(String requestId, PayoutApproveRequest approveRequest);
}
