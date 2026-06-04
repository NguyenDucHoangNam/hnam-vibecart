package com.vibecart.api.modules.ecommerce.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.entity.Voucher;
import com.vibecart.api.modules.ecommerce.repository.VoucherRepository;
import com.vibecart.api.modules.ecommerce.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;

    @Override
    @Transactional(readOnly = true)
    public Voucher validateVoucher(String voucherCode, BigDecimal orderTotal) {
        log.info("Validating voucher: {}", voucherCode);

        Voucher voucher = voucherRepository.findByCode(voucherCode)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        if (!"ACTIVE".equals(voucher.getStatus())) {
            throw new AppException(ErrorCode.VOUCHER_INACTIVE);
        }

        ZonedDateTime now = ZonedDateTime.now();
        if (now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }

        if (voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new AppException(ErrorCode.VOUCHER_USAGE_LIMIT_REACHED);
        }

        if (voucher.getMinOrderValue() != null && orderTotal.compareTo(voucher.getMinOrderValue()) < 0) {
            throw new AppException(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);
        }

        return voucher;
    }

    @Override
    public BigDecimal calculateDiscount(Voucher voucher, BigDecimal orderTotal) {
        BigDecimal discount;

        if ("PERCENTAGE".equals(voucher.getDiscountType())) {
            discount = orderTotal.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Cap at max discount amount if specified
            if (voucher.getMaxDiscountAmount() != null && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discount = voucher.getMaxDiscountAmount();
            }
        } else {
            // FIXED_AMOUNT
            discount = voucher.getDiscountValue();
        }

        // Discount cannot exceed order total
        if (discount.compareTo(orderTotal) > 0) {
            discount = orderTotal;
        }

        return discount;
    }

    @Override
    @Transactional
    public void markVoucherUsed(String voucherId) {
        int rowsUpdated = voucherRepository.incrementUsedCount(voucherId);
        if (rowsUpdated == 0) {
            log.warn("Failed to increment voucher usage count for ID: {}", voucherId);
            throw new AppException(ErrorCode.VOUCHER_USAGE_LIMIT_REACHED);
        }
    }
}
