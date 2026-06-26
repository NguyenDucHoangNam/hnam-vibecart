package com.vibecart.api.modules.ecommerce.service;

import com.vibecart.api.modules.ecommerce.entity.Voucher;
import java.math.BigDecimal;
public interface VoucherService {
    Voucher validateVoucher(String voucherCode, BigDecimal orderTotal);
    BigDecimal calculateDiscount(Voucher voucher, BigDecimal orderTotal);
    void markVoucherUsed(String voucherId);
}
