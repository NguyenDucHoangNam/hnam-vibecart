package com.vibecart.api.modules.notification.dto.response;

import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferencesResponse {
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
