package com.vibecart.api.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Filter xác thực yêu cầu bằng cách kiểm tra tính hợp lệ của JWT Token từ request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * Thực hiện kiểm tra JWT token, xác thực quyền truy cập và đưa thông tin vào Security Context.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String blacklistKey = "blacklist_token:" + jwt;
                Boolean isBlacklisted = redisTemplate.hasKey(blacklistKey);
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    log.warn("Access attempt with blacklisted JWT token");
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = tokenProvider.getUsernameFromToken(jwt);
                String userId = tokenProvider.getUserIdFromToken(jwt);
                String rolesString = tokenProvider.getRolesFromToken(jwt);

                List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesString.split(","))
                        .filter(role -> !role.trim().isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        username, null, authorities);

                Map<String, String> details = new HashMap<>();
                details.put("userId", userId);
                authentication.setDetails(details);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user '{}' (id={}) with authorities {}", username, userId, authorities);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Lấy token JWT từ header 'Authorization' (tiền tố 'Bearer ').
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
