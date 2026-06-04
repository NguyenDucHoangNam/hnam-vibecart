package com.vibecart.api.modules.shortener.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.shortener.dto.request.PayoutApproveRequest;
import com.vibecart.api.modules.shortener.dto.request.PayoutRequestDto;
import com.vibecart.api.modules.shortener.dto.response.PayoutResponse;
import com.vibecart.api.modules.shortener.entity.Commission;
import com.vibecart.api.modules.shortener.entity.PayoutRequest;
import com.vibecart.api.modules.shortener.repository.CommissionRepository;
import com.vibecart.api.modules.shortener.repository.PayoutRequestRepository;
import com.vibecart.api.modules.shortener.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutServiceImpl implements PayoutService {

    private final UserRepository userRepository;
    private final CommissionRepository commissionRepository;
    private final PayoutRequestRepository payoutRequestRepository;

    @Override
    @Transactional
    public PayoutResponse createPayoutRequest(PayoutRequestDto requestDto, String currentUsername) {
        log.info("Creator {} is requesting a payout of {} VND", currentUsername, requestDto.getAmount());

        User creator = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String creatorId = creator.getId();

        // 1. Calculate available balance (Sum of APPROVED commissions minus sum of PENDING or APPROVED payout requests)
        BigDecimal totalApprovedCommission = commissionRepository.findByCreatorIdAndStatus(creatorId, "APPROVED")
                .stream()
                .map(Commission::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PayoutRequest> existingPayouts = payoutRequestRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId);

        BigDecimal totalDeductedPayouts = existingPayouts.stream()
                .filter(p -> "PENDING".equals(p.getStatus()) || "APPROVED".equals(p.getStatus()))
                .map(PayoutRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal availableBalance = totalApprovedCommission.subtract(totalDeductedPayouts);

        if (availableBalance.compareTo(requestDto.getAmount()) < 0) {
            log.warn("Creator {} has insufficient balance. Available: {} VND, Requested: {} VND", 
                    currentUsername, availableBalance, requestDto.getAmount());
            throw new AppException(ErrorCode.INVALID_INPUT); // Insufficient balance
        }

        // 2. Build and save PayoutRequest
        PayoutRequest payoutRequest = PayoutRequest.builder()
                .creatorId(creatorId)
                .amount(requestDto.getAmount())
                .bankName(requestDto.getBankName())
                .bankAccountNumber(requestDto.getBankAccountNumber())
                .bankAccountName(requestDto.getBankAccountName())
                .status("PENDING")
                .build();
        payoutRequest.setId(UUID.randomUUID().toString()); // Explicitly set string UUID

        payoutRequest = payoutRequestRepository.save(payoutRequest);

        log.info("Successfully created PENDING PayoutRequest with ID: {} for creator: {}", 
                payoutRequest.getId(), creatorId);

        return convertToResponse(payoutRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutResponse> getMyPayoutRequests(String currentUsername) {
        User creator = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return payoutRequestRepository.findByCreatorIdOrderByCreatedAtDesc(creator.getId())
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutResponse> getPayoutRequestsByStatus(String status) {
        log.info("Fetching payout requests with status: {}", status);
        List<PayoutRequest> requests;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            requests = payoutRequestRepository.findAll();
        } else {
            requests = payoutRequestRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        return requests.stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Override
    @Transactional
    public PayoutResponse approveOrRejectPayout(String requestId, PayoutApproveRequest approveRequest) {
        log.info("Admin is processing payout request ID: {}, decision: {}", requestId, approveRequest.getStatus());

        PayoutRequest request = payoutRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT)); // Payout request not found

        if (!"PENDING".equals(request.getStatus())) {
            log.warn("Payout request {} is already processed (Status: {})", requestId, request.getStatus());
            throw new AppException(ErrorCode.INVALID_INPUT); // Already processed
        }

        request.setStatus(approveRequest.getStatus().toUpperCase());
        request.setAdminNote(approveRequest.getAdminNote());

        request = payoutRequestRepository.save(request);
        log.info("Successfully transitioned payout request {} to status: {}", requestId, request.getStatus());

        return convertToResponse(request);
    }

    private PayoutResponse convertToResponse(PayoutRequest payoutRequest) {
        return PayoutResponse.builder()
                .id(payoutRequest.getId())
                .creatorId(payoutRequest.getCreatorId())
                .amount(payoutRequest.getAmount())
                .bankName(payoutRequest.getBankName())
                .bankAccountNumber(payoutRequest.getBankAccountNumber())
                .bankAccountName(payoutRequest.getBankAccountName())
                .status(payoutRequest.getStatus())
                .adminNote(payoutRequest.getAdminNote())
                .createdAt(payoutRequest.getCreatedAt())
                .build();
    }
}
