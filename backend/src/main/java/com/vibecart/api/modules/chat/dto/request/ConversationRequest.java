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
    @Pattern(regexp = "DIRECT", message = "Type must be DIRECT")
    private String type;

    @NotEmpty(message = "memberIds must not be empty")
    private Set<String> memberIds;
}
