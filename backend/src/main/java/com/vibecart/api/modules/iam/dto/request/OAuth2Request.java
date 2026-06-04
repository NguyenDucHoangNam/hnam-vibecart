package com.vibecart.api.modules.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2Request {

    @NotBlank(message = "INVALID_INPUT")
    private String token;
}
