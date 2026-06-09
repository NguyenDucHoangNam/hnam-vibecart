package com.vibecart.api.modules.shortener.service;

import com.vibecart.api.modules.shortener.dto.response.DashboardResponse;

/**
 * Service cung cấp thống kê dashboard cho chương trình tiếp thị liên kết.
 */
public interface DashboardService {
    /**
     * Lấy các chỉ số thống kê dashboard của Creator.
     */
    DashboardResponse getDashboardMetrics(String currentUsername);
}
