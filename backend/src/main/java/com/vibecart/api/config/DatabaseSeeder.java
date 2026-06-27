package com.vibecart.api.config;

import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.RoleRepository;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.ecommerce.entity.Category;
import com.vibecart.api.modules.ecommerce.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final DatabaseSeedingProperties seedingProperties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository;
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!seedingProperties.isEnabled()) {
            log.info("Database seeding is disabled.");
            return;
        }

        log.info("Initializing database seeding for development environment...");

        try {
            log.info("Cleaning up any corrupted placeholder users from previous failed runs...");
            userRepository.hardDeleteByUsernameLike("$%");
        } catch (Exception e) {
            log.error("Failed to clean up corrupted placeholder users: ", e);
        }

        seedUser(
            seedingProperties.getUser(),
            "ROLE_USER",
            "Customer/Shopper"
        );

        seedUser(
            seedingProperties.getCreator(),
            "ROLE_CREATOR",
            "Creator/Seller"
        );

        seedUser(
            seedingProperties.getAdmin(),
            "ROLE_ADMIN",
            "Platform Administrator"
        );

        seedCategories();

        log.info("Database seeding process completed successfully.");
    }
    private void seedCategories() {
        if (categoryRepository.findBySlug("thoi-trang").isPresent()) {
            log.info("Categories already exist in database. Skipping category seeding.");
            return;
        }

        log.info("Seeding sample category hierarchy...");
        Category fashion = getOrCreateCategory("Thời trang", "thoi-trang", null, 1);
        Category fashionMen = getOrCreateCategory("Thời trang nam", "thoi-trang-nam", fashion, 1);
        getOrCreateCategory("Áo thun", "ao-thun", fashionMen, 1);
        getOrCreateCategory("Áo khoác", "ao-khoac", fashionMen, 2);
        getOrCreateCategory("Quần jeans", "quan-jeans", fashionMen, 3);

        Category fashionWomen = getOrCreateCategory("Thời trang nữ", "thoi-trang-nu", fashion, 2);
        getOrCreateCategory("Váy đầm", "vay-dam", fashionWomen, 1);
        getOrCreateCategory("Chân váy", "chan-vay", fashionWomen, 2);

        Category electronics = getOrCreateCategory("Thiết bị điện tử", "thiet-bi-dien-tu", null, 2);
        Category phones = getOrCreateCategory("Điện thoại & Phụ kiện", "dien-thoai-phu-kien", electronics, 1);
        getOrCreateCategory("Ốp lưng", "op-lung", phones, 1);
        getOrCreateCategory("Cáp sạc", "cap-sac", phones, 2);
        getOrCreateCategory("Tai nghe", "tai-nghe", phones, 3);

        Category otherCategory = getOrCreateCategory("Khác", "khac", null, 999);
        getOrCreateCategory("Sản phẩm khác", "san-pham-khac", otherCategory, 1);
    }
    private Category getOrCreateCategory(String name, String slug, Category parent, Integer sortOrder) {
        return categoryRepository.findBySlug(slug)
                .orElseGet(() -> {
                    log.info("Category '{}' not found in database. Seeding it.", name);
                    Category category = Category.builder()
                            .name(name)
                            .slug(slug)
                            .parent(parent)
                            .sortOrder(sortOrder)
                            .build();
                    return categoryRepository.save(category);
                });
    }
    private Role getOrCreateRole(String roleName, String roleDisplayName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    log.info("Role '{}' not found in database. Automatically creating it for development environment.", roleName);
                    Role role = Role.builder()
                            .name(roleName)
                            .description(roleDisplayName + " role")
                            .build();
                    return roleRepository.save(role);
                });
    }
    private void seedUser(DatabaseSeedingProperties.UserSeed seedData, String roleName, String roleDisplayName) {
        if (seedData == null || seedData.getUsername() == null) {
            log.warn("Seeding data is missing or incomplete for role {}", roleName);
            return;
        }

        String username = seedData.getUsername();
        if (userRepository.existsByUsername(username)) {
            log.info("Sample user '{}' ({}) already exists. Skipping.", username, roleDisplayName);
            return;
        }

        if (userRepository.existsByEmail(seedData.getEmail())) {
            log.warn("Email '{}' already exists for another user. Cannot seed sample '{}'.", seedData.getEmail(), username);
            return;
        }

        try {
            Role role = getOrCreateRole(roleName, roleDisplayName);

            User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(seedData.getPassword()))
                    .email(seedData.getEmail())
                    .fullName(seedData.getFullName())
                    .oauthProvider("LOCAL")
                    .status("ACTIVE")
                    .role(role)
                    .build();

            userRepository.save(user);
            log.info("Successfully seeded sample user '{}' with role '{}'.", username, roleName);
        } catch (Exception e) {
            log.error("Failed to seed sample user '{}' ({}): ", username, roleDisplayName, e);
        }
    }
}

