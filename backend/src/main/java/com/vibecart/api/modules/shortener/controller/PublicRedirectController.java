package com.vibecart.api.modules.shortener.controller;

import com.vibecart.api.modules.shortener.service.ShortLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller công khai xử lý chuyển hướng (redirect) từ shortlink sang URL gốc.
 */
@RestController
@RequestMapping("/v")
@RequiredArgsConstructor
public class PublicRedirectController {

    private final ShortLinkService shortLinkService;

    /**
     * Chuyển hướng người dùng từ mã shortcode sang URL gốc và ghi nhận sự kiện click.
     */
    @GetMapping("/{shortcode}")
    public void redirect(@PathVariable("shortcode") String shortcode, HttpServletRequest request, HttpServletResponse response) {
        shortLinkService.handleRedirect(shortcode, request, response);
    }
}
