package com.vibecart.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Cấu hình kích hoạt cơ chế tự động theo dõi thực thể JPA (JPA Auditing).
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
public class JpaConfig {
}
