package com.vibecart.api.modules.chat.service;

import com.vibecart.api.modules.chat.dto.response.PresenceResponse;
import com.vibecart.api.modules.social.dto.response.FollowResponse;

import java.util.List;

/**
 * Interface định nghĩa các nghiệp vụ kiểm soát trạng thái trực tuyến (Presence) của người dùng.
 */
public interface PresenceService {

    /**
     * Cập nhật trạng thái người dùng sang trực tuyến (Online).
     */
    void setOnline(String username);

    /**
     * Cập nhật trạng thái người dùng sang ngoại tuyến (Offline).
     */
    void setOffline(String username);

    /**
     * Lấy trạng thái trực tuyến chi tiết của người dùng cụ thể.
     */
    PresenceResponse getUserPresence(String userId);
    
    /**
     * Lấy danh sách bạn bè/người đang theo dõi đang trực tuyến.
     */
    List<FollowResponse> getActiveUsers(String currentUsername);
}
