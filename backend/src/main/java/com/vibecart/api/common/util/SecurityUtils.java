package com.vibecart.api.common.util;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

/**
 * Utility class truy xuất thông tin người dùng hiện tại từ Spring Security Context.
 */
@UtilityClass
public class SecurityUtils {

    /**
     * Lấy username của người dùng đã đăng nhập (bắt buộc xác thực).
     */
    public static String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /**
     * Lấy userId của người dùng đã đăng nhập từ JWT claim (không query DB).
     */
    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) {
                return userId.toString();
            }
        }
        return auth != null ? auth.getName() : null;
    }

    /**
     * Lấy username nếu đã đăng nhập, trả về null nếu là khách (anonymous).
     */
    public static String getOptionalUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    /**
     * Kiểm tra người dùng hiện tại có role cụ thể hay không.
     */
    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    /**
     * Kiểm tra người dùng hiện tại có phải là Admin hay không.
     */
    public static boolean isAdmin() {
        return hasRole("ROLE_ADMIN");
    }
}
