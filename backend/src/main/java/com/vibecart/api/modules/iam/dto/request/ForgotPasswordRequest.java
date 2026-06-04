package com.vibecart.api.modules.iam.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank(message = "INVALID_INPUT")
    @Email(message = "INVALID_INPUT")
    private String email;
}
