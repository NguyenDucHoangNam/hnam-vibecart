package com.vibecart.api.modules.iam.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 5, max = 30, message = "USERNAME_INVALID")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "USERNAME_INVALID")
    private String username;

    @Size(min = 2, max = 100, message = "INVALID_INPUT")
    private String fullName;

    private String avatarUrl;
}
