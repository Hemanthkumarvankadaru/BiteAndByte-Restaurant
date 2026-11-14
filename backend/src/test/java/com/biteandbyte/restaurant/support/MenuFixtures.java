package com.biteandbyte.restaurant.support;

import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.repository.CategoryRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import java.math.BigDecimal;

/**
 * Creates categories and dishes for one test. Tests must never change the seeded menu, because
 * {@code DataInitializerTest} checks it and every test class shares one in-memory database.
 * Names are unique per call. Display order 900 keeps fixtures after the seeded categories.
 */
public final class MenuFixtures {

    public static Category category(CategoryRepository categories) {
        Category category = new Category();
        category.setName("Test " + AuthTestSupport.uniqueSuffix());
        category.setDisplayOrder(900);
        category.setActive(true);
        return categories.save(category);
    }

    public static MenuItem item(MenuItemRepository menuItems, Category category, String name, String price, int stock) {
        return item(menuItems, category, name, price, stock, true);
    }

    public static MenuItem item(MenuItemRepository menuItems, Category category, String name, String price, int stock,
                                boolean veg) {
        MenuItem item = new MenuItem();
        item.setCategory(category);
        item.setName(name);
        item.setDescription("Test dish " + name);
        item.setPrice(new BigDecimal(price));
        item.setStockQuantity(stock);
        item.setVeg(veg);
        return menuItems.save(item);
    }

    private MenuFixtures() {
    }
}
