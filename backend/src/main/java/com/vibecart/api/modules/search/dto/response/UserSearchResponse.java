package com.vibecart.api.modules.search.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String status;
    private Set<String> roles;
    private ZonedDateTime createdAt;
    private Boolean isFollowing;
    private Long followerCount;
}
