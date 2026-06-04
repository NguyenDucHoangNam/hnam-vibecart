package com.vibecart.api.modules.iam.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "INVALID_INPUT")
    private String token;

    @NotBlank(message = "INVALID_PASSWORD")
    @Size(min = 8, max = 100, message = "INVALID_PASSWORD")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$",
            message = "INVALID_PASSWORD"
    )
    private String newPassword;
}
