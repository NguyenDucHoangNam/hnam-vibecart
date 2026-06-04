package com.vibecart.api.modules.iam.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    @NotBlank(message = "INVALID_INPUT")
    @Email(message = "INVALID_INPUT")
    private String email;

    @NotBlank(message = "INVALID_OTP")
    @Size(min = 6, max = 6, message = "INVALID_OTP")
    private String otpCode;
}
