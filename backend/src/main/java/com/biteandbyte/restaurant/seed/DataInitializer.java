package com.biteandbyte.restaurant.seed;

import com.biteandbyte.restaurant.config.AdminProperties;
import com.biteandbyte.restaurant.config.SeedProperties;
import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.model.Role;
import com.biteandbyte.restaurant.model.User;
import com.biteandbyte.restaurant.repository.CategoryRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import com.biteandbyte.restaurant.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the admin account and the sample menu on start-up. Safe to run on every start:
 * the admin is created only if missing, and the menu only when there are no categories.
 * An existing admin's password is never changed here.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AdminPasswordGuard adminPasswordGuard;
    private final AdminProperties admin;
    private final SeedProperties seed;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final MenuItemRepository menuItems;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AdminPasswordGuard adminPasswordGuard, AdminProperties admin, SeedProperties seed,
                           UserRepository users, CategoryRepository categories, MenuItemRepository menuItems,
                           PasswordEncoder passwordEncoder) {
        this.adminPasswordGuard = adminPasswordGuard;
        this.admin = admin;
        this.seed = seed;
        this.users = users;
        this.categories = categories;
        this.menuItems = menuItems;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        adminPasswordGuard.check();
        seedAdmin();
        if (seed.menu()) {
            seedMenu();
        }
    }

    private void seedAdmin() {
        if (users.existsByUsernameIgnoreCase(admin.username())) {
            return;
        }
        User user = new User();
        user.setUsername(admin.username());
        user.setEmail(admin.email());
        user.setPhone(admin.phone());
        user.setFullName(admin.fullName());
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setRole(Role.ADMIN);
        users.save(user);
        if (admin.usesDefaultPassword()) {
            log.warn("Created admin account '{}' with the default password 'admin'. Local dev only.", admin.username());
        } else {
            log.info("Created admin account '{}'.", admin.username());
        }
    }

    private void seedMenu() {
        if (categories.count() > 0) {
            return;
        }
        int order = 1;
        int itemCount = 0;
        for (MenuSeedData.SeedCategory seedCategory : MenuSeedData.CATEGORIES) {
            Category category = new Category();
            category.setName(seedCategory.name());
            category.setDescription(seedCategory.description());
            category.setImageUrl(MenuSeedData.placeholderImage(seedCategory.name()));
            category.setDisplayOrder(order++);
            categories.save(category);

            for (MenuSeedData.SeedItem seedItem : seedCategory.items()) {
                MenuItem item = new MenuItem();
                item.setCategory(category);
                item.setName(seedItem.name());
                item.setDescription(seedItem.description());
                item.setPrice(seedItem.priceValue());
                item.setImageUrl(MenuSeedData.placeholderImage(seedItem.name()));
                item.setVeg(seedItem.veg());
                item.setStockQuantity(MenuSeedData.DEFAULT_STOCK);
                menuItems.save(item);
                itemCount++;
            }
        }
        log.info("Seeded {} categories and {} menu items.", MenuSeedData.CATEGORIES.size(), itemCount);
    }
}
