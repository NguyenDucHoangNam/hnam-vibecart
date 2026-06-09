package com.vibecart.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Xử lý các yêu cầu truy cập không hợp lệ hoặc thiếu xác thực
 * (Unauthenticated/Anonymous).
 *
 * <p>
 * Khi client gọi một API được bảo mật nhưng không cung cấp JWT token hợp lệ,
 * Spring Security sẽ kích hoạt Entry Point này để trả về phản hồi lỗi JSON
 * đồng nhất dưới dạng {@link ApiResponse} thay vì trang lỗi mặc định của
 * Spring.
 * </p>
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(errorCode.getStatusCode().value());

        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
