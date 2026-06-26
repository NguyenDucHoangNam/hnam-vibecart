package com.vibecart.api.modules.chat.service;

import com.vibecart.api.modules.chat.dto.response.PresenceResponse;
import com.vibecart.api.modules.social.dto.response.FollowResponse;

import java.util.List;
public interface PresenceService {
    void setOnline(String username);
    void setOffline(String username);
    PresenceResponse getUserPresence(String userId);
    List<FollowResponse> getActiveUsers(String currentUsername);
}
