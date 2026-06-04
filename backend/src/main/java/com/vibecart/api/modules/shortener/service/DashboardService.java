package com.vibecart.api.modules.shortener.service;

import com.vibecart.api.modules.shortener.dto.response.DashboardResponse;

public interface DashboardService {
    DashboardResponse getDashboardMetrics(String currentUsername);
}
