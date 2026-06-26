package com.vibecart.api.modules.shortener.service;

import com.vibecart.api.modules.shortener.dto.request.ShortlinkCreateRequest;
import com.vibecart.api.modules.shortener.dto.response.ShortlinkResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
public interface ShortLinkService {
    ShortlinkResponse createShortLink(ShortlinkCreateRequest request, String currentUsername);
    List<ShortlinkResponse> getMyShortLinks(String currentUsername);
    void handleRedirect(String shortCode, HttpServletRequest request, HttpServletResponse response);
}
