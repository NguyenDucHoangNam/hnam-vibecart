package com.vibecart.api.modules.iam.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "USERNAME_INVALID")
    @Size(min = 5, max = 30, message = "USERNAME_INVALID")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "USERNAME_INVALID")
    private String username;

    @NotBlank(message = "INVALID_INPUT")
    @Email(message = "INVALID_INPUT")
    @Size(max = 100, message = "INVALID_INPUT")
    private String email;

    @NotBlank(message = "INVALID_PASSWORD")
    @Size(min = 8, max = 100, message = "INVALID_PASSWORD")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$",
            message = "INVALID_PASSWORD"
    )
    private String password;

    @NotBlank(message = "INVALID_INPUT")
    @Size(min = 2, max = 100, message = "INVALID_INPUT")
    @Pattern(regexp = "^[\\p{L} ]+$", message = "INVALID_INPUT")
    private String fullName;


    private String role;
}
