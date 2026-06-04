package com.vibecart.api.modules.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "INVALID_INPUT")
    private String usernameOrEmail;

    @NotBlank(message = "INVALID_INPUT")
    private String password;
}
