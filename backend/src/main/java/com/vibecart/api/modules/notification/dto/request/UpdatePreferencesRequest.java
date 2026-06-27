package com.vibecart.api.modules.notification.dto.request;

import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferencesRequest {
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
