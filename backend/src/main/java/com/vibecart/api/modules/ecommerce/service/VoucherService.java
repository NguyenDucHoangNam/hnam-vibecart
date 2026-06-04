package com.vibecart.api.modules.ecommerce.service;

import com.vibecart.api.modules.ecommerce.entity.Voucher;
import java.math.BigDecimal;

public interface VoucherService {
    /**
     * Validates a voucher code and returns the voucher entity if valid.
     * Throws AppException if voucher is invalid, expired, or usage limit reached.
     */
    Voucher validateVoucher(String voucherCode, BigDecimal orderTotal);

    /**
     * Calculates the discount amount from a valid voucher.
     */
    BigDecimal calculateDiscount(Voucher voucher, BigDecimal orderTotal);

    /**
     * Atomically increments the used count of a voucher.
     */
    void markVoucherUsed(String voucherId);
}
