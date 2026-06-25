package com.vibecart.api.modules.iam.dto.response;

import lombok.*;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String status;
    private String oauthProvider;
    private Set<String> roles;
    private boolean hasPassword;
    private java.time.ZonedDateTime createdAt;
}
