package com.vibecart.api.modules.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRequest {

    @NotBlank(message = "Conversation type must not be blank")
    @Pattern(regexp = "DIRECT|GROUP", message = "Type must be either DIRECT or GROUP")
    private String type;

    private String name; // Required only if GROUP

    @NotEmpty(message = "memberIds must not be empty")
    private Set<String> memberIds;
}
