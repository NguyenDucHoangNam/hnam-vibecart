package com.vibecart.api.common.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cấu hình tự động theo dõi và điền thông tin người thực hiện thao tác (CreatedBy, LastModifiedBy) cho JPA Auditing.
 */
@Component("springSecurityAuditorAware")
public class SpringSecurityAuditorAware implements AuditorAware<String> {

    /**
     * Lấy username của người dùng hiện tại đang đăng nhập từ Security Context.
     */
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() 
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.of("SYSTEM");
        }

        return Optional.of(authentication.getName());
    }
}
