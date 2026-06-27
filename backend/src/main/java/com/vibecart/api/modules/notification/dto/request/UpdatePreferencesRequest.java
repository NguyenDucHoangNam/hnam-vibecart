package com.vibecart.api.modules.notification.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferencesRequest {
    @NotNull
    @Size(min = 1, max = 20)
    private Map<String, ChannelPreferenceDto> preferences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelPreferenceDto {
        private boolean inApp;
        private boolean sound;
        private boolean push;
    }
}

