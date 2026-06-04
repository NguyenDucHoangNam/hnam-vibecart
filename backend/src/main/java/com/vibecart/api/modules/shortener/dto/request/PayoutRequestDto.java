package com.vibecart.api.modules.shortener.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequestDto {

    @NotNull(message = "Withdrawal amount is required")
    @Min(value = 50000, message = "Minimum payout amount is 50,000 VND")
    private BigDecimal amount;

    @NotBlank(message = "Bank name is required")
    private String bankName;

    @NotBlank(message = "Bank account number is required")
    private String bankAccountNumber;

    @NotBlank(message = "Bank account name is required")
    private String bankAccountName;
}
