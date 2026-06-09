package com.vibecart.api.modules.shortener.service;

import com.vibecart.api.modules.shortener.dto.request.ShortlinkCreateRequest;
import com.vibecart.api.modules.shortener.dto.response.ShortlinkResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * Service quản lý tạo shortlink và xử lý chuyển hướng liên kết tiếp thị.
 */
public interface ShortLinkService {
    /**
     * Tạo shortlink tiếp thị liên kết cho sản phẩm.
     */
    ShortlinkResponse createShortLink(ShortlinkCreateRequest request, String currentUsername);

    /**
     * Lấy danh sách shortlink của Creator hiện tại.
     */
    List<ShortlinkResponse> getMyShortLinks(String currentUsername);

    /**
     * Xử lý chuyển hướng từ shortcode sang URL gốc và ghi nhận sự kiện click.
     */
    void handleRedirect(String shortCode, HttpServletRequest request, HttpServletResponse response);
}
