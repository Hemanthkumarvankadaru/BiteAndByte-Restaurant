package com.biteandbyte.restaurant.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.biteandbyte.restaurant.config.PricingProperties;
import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.model.Role;
import com.biteandbyte.restaurant.model.User;
import com.biteandbyte.restaurant.repository.CategoryRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import com.biteandbyte.restaurant.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class DataInitializerTest {

    @Autowired private DataInitializer initializer;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private MenuItemRepository menuItems;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PricingProperties pricing;

    /**
     * Checks the seeded rows by name. Other test classes add their own categories and dishes to
     * the shared database, so global counts are not stable; the seeded rows must be untouched.
     */
    @Test
    void seedsEightCategoriesWithThreeItemsEach() {
        assertThat(MenuSeedData.CATEGORIES).hasSize(8);
        int expectedOrder = 1;
        for (MenuSeedData.SeedCategory seedCategory : MenuSeedData.CATEGORIES) {
            Category category = categories.findByNameIgnoreCase(seedCategory.name()).orElseThrow();
            assertThat(category.isActive()).isTrue();
            assertThat(category.getDisplayOrder()).isEqualTo(expectedOrder++);

            List<MenuItem> items = menuItems.findByCategoryIdAndDeletedFalseOrderByNameAsc(category.getId());
            assertThat(items).extracting(MenuItem::getName)
                    .containsExactlyInAnyOrderElementsOf(seedCategory.items().stream().map(MenuSeedData.SeedItem::name).toList());
            assertThat(items).allSatisfy(item -> {
                assertThat(item.getStockQuantity()).isEqualTo(MenuSeedData.DEFAULT_STOCK);
                assertThat(item.getPrice()).isPositive();
                assertThat(item.getImageUrl()).startsWith("https://");
                assertThat(item.isAvailable()).isTrue();
                assertThat(item.isDeleted()).isFalse();
            });
        }
    }

    @Test
    void seedsAdminWithHashedDefaultPassword() {
        User admin = users.findByUsernameIgnoreCase("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPasswordHash()).isNotEqualTo("admin");
        assertThat(passwordEncoder.matches("admin", admin.getPasswordHash())).isTrue();
        assertThat(admin.getCreatedAt()).isNotNull();
    }

    @Test
    void runningAgainDoesNotDuplicateData() {
        long usersBefore = users.count();
        long categoriesBefore = categories.count();
        long itemsBefore = menuItems.count();

        initializer.run(new DefaultApplicationArguments());

        assertThat(users.count()).isEqualTo(usersBefore);
        assertThat(categories.count()).isEqualTo(categoriesBefore);
        assertThat(menuItems.count()).isEqualTo(itemsBefore);
    }

    @Test
    void bindsPricingRules() {
        assertThat(pricing.gstPercent()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(pricing.deliveryCharge()).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(pricing.freeDeliveryAbove()).isEqualByComparingTo(new BigDecimal("500"));
    }
}
