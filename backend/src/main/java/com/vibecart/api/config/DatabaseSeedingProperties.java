package com.vibecart.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Configuration
@ConfigurationProperties(prefix = "app.seeding")
@Getter
@Setter
public class DatabaseSeedingProperties {
    private boolean enabled = true;
    private UserSeed user;
    private UserSeed creator;
    private UserSeed admin;
    @Getter
    @Setter
    public static class UserSeed {
        private String username;
        private String password;
        private String email;
        private String fullName;
    }
}
