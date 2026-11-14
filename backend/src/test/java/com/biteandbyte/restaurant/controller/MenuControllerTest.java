package com.biteandbyte.restaurant.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.repository.CategoryRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import com.biteandbyte.restaurant.support.MenuFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The public menu: search, filters, paging and what customers must not see. */
@SpringBootTest
@AutoConfigureMockMvc
class MenuControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categories;
    @Autowired private MenuItemRepository menuItems;

    private Category category;
    private MenuItem paneerRoll;
    private MenuItem chickenRoll;
    private MenuItem soldOutRice;

    @BeforeEach
    void setUp() {
        category = MenuFixtures.category(categories);
        paneerRoll = MenuFixtures.item(menuItems, category, "Zesty Paneer Roll", "120.00", 10, true);
        chickenRoll = MenuFixtures.item(menuItems, category, "Zesty Chicken Roll", "150.00", 10, false);
        soldOutRice = MenuFixtures.item(menuItems, category, "Plain Rice", "80.00", 0, true);
    }

    @Test
    void listsTheMenuInPages() throws Exception {
        mockMvc.perform(get("/api/menu").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(24)))
                // Default order: category display order, so the seeded Starters come first.
                .andExpect(jsonPath("$.data.content[0].categoryName").value("Starters"));
    }

    @Test
    void filtersByCategoryVegAvailabilityAndText() throws Exception {
        String cat = category.getId().toString();

        mockMvc.perform(get("/api/menu").param("category", cat))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mockMvc.perform(get("/api/menu").param("category", cat).param("veg", "true"))
                .andExpect(jsonPath("$.data.content[*].name", containsInAnyOrder("Zesty Paneer Roll", "Plain Rice")));
        mockMvc.perform(get("/api/menu").param("category", cat).param("q", "ZESTY").param("veg", "false"))
                .andExpect(jsonPath("$.data.content[*].name", contains("Zesty Chicken Roll")));
        mockMvc.perform(get("/api/menu").param("category", cat).param("availableOnly", "true"))
                .andExpect(jsonPath("$.data.content[*].name", not(hasItem("Plain Rice"))))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        // Matches the description too.
        mockMvc.perform(get("/api/menu").param("category", cat).param("q", "test dish plain"))
                .andExpect(jsonPath("$.data.content[*].name", contains("Plain Rice")));
    }

    @Test
    void likeWildcardsInSearchAreLiteral() throws Exception {
        mockMvc.perform(get("/api/menu").param("category", category.getId().toString()).param("q", "%"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/menu").param("category", category.getId().toString()).param("q", "_"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void sortsByWhitelistedFields() throws Exception {
        mockMvc.perform(get("/api/menu").param("category", category.getId().toString()).param("sort", "price,desc"))
                .andExpect(jsonPath("$.data.content[*].name",
                        contains("Zesty Chicken Roll", "Zesty Paneer Roll", "Plain Rice")));
    }

    @Test
    void showsSoldOutDishesAsNotOrderable() throws Exception {
        mockMvc.perform(get("/api/menu/" + soldOutRice.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.stockQuantity").value(0))
                .andExpect(jsonPath("$.data.orderable").value(false));
        mockMvc.perform(get("/api/menu/" + paneerRoll.getId()))
                .andExpect(jsonPath("$.data.orderable").value(true))
                .andExpect(jsonPath("$.data.price").value(120.0))
                .andExpect(jsonPath("$.data.categoryName").value(category.getName()));
    }

    @Test
    void hidesDeletedDishesAndInactiveCategories() throws Exception {
        chickenRoll.setDeleted(true);
        menuItems.save(chickenRoll);
        mockMvc.perform(get("/api/menu/" + chickenRoll.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mockMvc.perform(get("/api/menu").param("category", category.getId().toString()))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        category.setActive(false);
        categories.save(category);
        mockMvc.perform(get("/api/menu").param("category", category.getId().toString()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/menu/" + paneerRoll.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$.data[*].name", not(hasItem(category.getName()))));
        mockMvc.perform(get("/api/categories/" + category.getId())).andExpect(status().isNotFound());
    }

    @Test
    void listsActiveCategoriesWithItemCounts() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Starters"))
                .andExpect(jsonPath("$.data[?(@.name == 'Starters')].itemCount", contains(3)))
                .andExpect(jsonPath("$.data[?(@.name == '%s')].itemCount".formatted(category.getName()), contains(3)));
    }

    @Test
    void rejectsBadListParameters() throws Exception {
        mockMvc.perform(get("/api/menu").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/menu").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/menu").param("sort", "version,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/menu").param("category", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/menu").param("q", "x".repeat(101))).andExpect(status().isBadRequest());
    }
}
