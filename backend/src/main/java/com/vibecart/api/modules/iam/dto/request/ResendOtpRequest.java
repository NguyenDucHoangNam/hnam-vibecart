package com.vibecart.api.modules.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendOtpRequest {

    @NotBlank(message = "INVALID_INPUT")
    @Email(message = "INVALID_INPUT")
    private String email;
}
