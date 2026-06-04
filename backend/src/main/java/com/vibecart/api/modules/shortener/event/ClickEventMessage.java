package com.vibecart.api.modules.shortener.event;

import lombok.*;

import java.io.Serializable;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEventMessage implements Serializable {
    private String shortLinkId;
    private String ipAddress;
    private String userAgent;
    private String referrer;
    private String deviceType;
    private String browser;
    private String country;
    private ZonedDateTime clickTime;
}
