package com.vibecart.api.modules.iam.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRolesRequest {

    @NotEmpty(message = "INVALID_INPUT")
    private Set<String> roles;
}
