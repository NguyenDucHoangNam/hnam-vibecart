package com.vibecart.api.modules.shortener.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.shortener.dto.response.DashboardResponse;
import com.vibecart.api.modules.shortener.entity.Commission;
import com.vibecart.api.modules.shortener.repository.ClickEventRepository;
import com.vibecart.api.modules.shortener.repository.CommissionRepository;
import com.vibecart.api.modules.shortener.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementation của {@link DashboardService} tính toán các chỉ số dashboard tiếp thị liên kết.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final ClickEventRepository clickEventRepository;
    private final CommissionRepository commissionRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboardMetrics(String currentUsername) {
        log.info("Fetching dashboard metrics for user: {}", currentUsername);

        User creator = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String creatorId = creator.getId();

        long totalClicks = clickEventRepository.countByShortLinkCreatorId(creatorId);

        List<Commission> commissions = commissionRepository.findByCreatorId(creatorId);

        long totalOrders = commissions.stream()
                .filter(c -> "PENDING".equals(c.getStatus()) || "APPROVED".equals(c.getStatus()))
                .count();

        double conversionRate = totalClicks == 0 ? 0.0 : ((double) totalOrders / totalClicks) * 100.0;

        BigDecimal pendingCommission = commissions.stream()
                .filter(c -> "PENDING".equals(c.getStatus()))
                .map(Commission::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal approvedCommission = commissions.stream()
                .filter(c -> "APPROVED".equals(c.getStatus()))
                .map(Commission::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardResponse.builder()
                .creatorId(creatorId)
                .totalClicks(totalClicks)
                .totalOrders(totalOrders)
                .conversionRate(conversionRate)
                .pendingCommission(pendingCommission)
                .approvedCommission(approvedCommission)
                .build();
    }
}
