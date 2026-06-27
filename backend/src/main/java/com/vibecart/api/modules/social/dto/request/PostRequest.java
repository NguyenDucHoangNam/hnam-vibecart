package com.vibecart.api.modules.social.dto.request;

import com.vibecart.api.modules.social.enums.PostVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public record PostRequest(
    @NotBlank(message = "INVALID_INPUT")
    @Size(max = 5000, message = "INVALID_INPUT")
    String content,

    List<String> mediaUrls,

    Set<String> taggedProductIds,

    PostVisibility visibility
) {}
