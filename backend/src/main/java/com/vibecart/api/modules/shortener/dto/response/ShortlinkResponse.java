package com.vibecart.api.modules.shortener.dto.response;

import lombok.*;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortlinkResponse {
    private String shortcode;
    private String shortlink;
    private String longUrl;
    private ZonedDateTime createdAt;
}
