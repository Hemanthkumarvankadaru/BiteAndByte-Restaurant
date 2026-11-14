package com.biteandbyte.restaurant.controller;

import static com.biteandbyte.restaurant.support.ShopTestSupport.addToCart;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteandbyte.restaurant.support.AuthTestSupport;
import com.biteandbyte.restaurant.support.AuthTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Admin management of dishes, stock and categories, all through the API. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminMenuControllerTest {

    @Autowired private MockMvc mockMvc;

    private TestUser admin;
    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = AuthTestSupport.loginAdmin(mockMvc);
        categoryId = createCategory("Admin Test " + AuthTestSupport.uniqueSuffix()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(b -> idOf(b));
    }

    // ---- Menu items ----

    @Test
    void validatesNewDishes() throws Exception {
        String body = """
                {"categoryId": %d, "name": " ", "price": 10.999, "imageUrl": "ftp://example.com/x.png",
                 "stockQuantity": -1, "veg": true}
                """.formatted(categoryId);
        mockMvc.perform(post("/api/admin/menu-items").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        containsInAnyOrder("name", "price", "imageUrl", "stockQuantity")));

        mockMvc.perform(post("/api/admin/menu-items").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(itemJson(categoryId, "Zero", "0", 5, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be greater than 0"));

        mockMvc.perform(post("/api/admin/menu-items").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(itemJson(999_999L, "Lost", "10", 5, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNKNOWN_CATEGORY"));
    }

    @Test
    void createsUpdatesRestocksAndSoftDeletesADish() throws Exception {
        long itemId = createItem("Admin Kebab", "199.50", 3)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.orderable").value(true))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn().getResponse().getContentAsString().transform(b -> idOf(b));
        mockMvc.perform(get("/api/menu/" + itemId)).andExpect(jsonPath("$.data.price").value(199.5));

        // Update with the version we loaded.
        mockMvc.perform(put("/api/admin/menu-items/" + itemId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(itemJson(categoryId, "Admin Kebab", "209.00", 3, 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(209.0))
                .andExpect(jsonPath("$.data.version").value(1));
        // A second update still based on version 0 must not overwrite it.
        mockMvc.perform(put("/api/admin/menu-items/" + itemId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(itemJson(categoryId, "Admin Kebab", "1.00", 3, 0L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONCURRENT_UPDATE"));

        // Zero stock: listed, but not orderable. The admin's switch stays on.
        setStock(itemId, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.orderable").value(false));
        TestUser customer = AuthTestSupport.registerCustomer(mockMvc);
        addToCart(mockMvc, customer, itemId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OUT_OF_STOCK"));

        // Restocking makes it orderable again without another step.
        setStock(itemId, 5).andExpect(jsonPath("$.data.orderable").value(true));
        addToCart(mockMvc, customer, itemId, 1).andExpect(status().isOk());

        // Soft delete: gone from the menu and from carts, kept for the admin and for history.
        mockMvc.perform(delete("/api/admin/menu-items/" + itemId).header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/menu/" + itemId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cart").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.items", empty()));
        mockMvc.perform(get("/api/admin/menu-items/" + itemId).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
        mockMvc.perform(get("/api/admin/menu-items").param("category", String.valueOf(categoryId))
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/admin/menu-items").param("category", String.valueOf(categoryId))
                        .param("includeDeleted", "true").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(put("/api/admin/menu-items/" + itemId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(itemJson(categoryId, "Back", "10", 1, null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/menu-items/" + itemId).header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void switchedOffDishIsListedForAdminButNotOrderable() throws Exception {
        long itemId = idOf(mockMvc.perform(post("/api/admin/menu-items").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(categoryId, "Off Dish", "50", 10, null).replace("\"veg\"", "\"available\": false, \"veg\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.available").value(false))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/menu/" + itemId)).andExpect(jsonPath("$.data.orderable").value(false));
        mockMvc.perform(get("/api/menu").param("category", String.valueOf(categoryId)).param("availableOnly", "true"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ---- Categories ----

    @Test
    void categoryNamesAreUnique() throws Exception {
        createCategory("starters")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CATEGORY_NAME_TAKEN"));
        mockMvc.perform(put("/api/admin/categories/" + categoryId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(categoryJson("Biryani", true)))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivatingACategoryHidesItFromCustomers() throws Exception {
        createItem("Hidden Dish", "100", 5).andExpect(status().isCreated());
        String name = "Renamed " + AuthTestSupport.uniqueSuffix();

        mockMvc.perform(put("/api/admin/categories/" + categoryId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(categoryJson(name, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.itemCount").value(1));

        mockMvc.perform(get("/api/categories")).andExpect(jsonPath("$.data[*].name", not(hasItem(name))));
        mockMvc.perform(get("/api/menu").param("category", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/admin/categories").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data[*].name", hasItem(name)));
    }

    @Test
    void categoryDeleteIsBlockedWhileDishesOrHistoryRemain() throws Exception {
        long itemId = idOf(createItem("Blocking Dish", "100", 5).andReturn().getResponse().getContentAsString());

        deleteCategory(categoryId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CATEGORY_NOT_EMPTY"));

        mockMvc.perform(delete("/api/admin/menu-items/" + itemId).header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        deleteCategory(categoryId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CATEGORY_HAS_HISTORY"));

        long empty = idOf(createCategory("Empty " + AuthTestSupport.uniqueSuffix()).andReturn().getResponse().getContentAsString());
        deleteCategory(empty).andExpect(status().isOk());
        deleteCategory(empty).andExpect(status().isNotFound());
    }

    // ---- Helpers ----

    private ResultActions createCategory(String name) throws Exception {
        return mockMvc.perform(post("/api/admin/categories").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(categoryJson(name, true)));
    }

    private ResultActions deleteCategory(long id) throws Exception {
        return mockMvc.perform(delete("/api/admin/categories/" + id).header("Authorization", admin.bearer()));
    }

    private ResultActions createItem(String name, String price, int stock) throws Exception {
        return mockMvc.perform(post("/api/admin/menu-items").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(itemJson(categoryId, name, price, stock, null)));
    }

    private ResultActions setStock(long itemId, int stock) throws Exception {
        return mockMvc.perform(patch("/api/admin/menu-items/" + itemId + "/stock").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"stockQuantity\": %d}".formatted(stock)));
    }

    private static String categoryJson(String name, boolean active) {
        return """
                {"name": "%s", "description": "Made by a test", "displayOrder": 900, "active": %s}
                """.formatted(name, active);
    }

    private static String itemJson(long categoryId, String name, String price, int stock, Long version) {
        return """
                {"categoryId": %d, "name": "%s", "description": "Admin test dish", "price": %s,
                 "imageUrl": "https://placehold.co/600x400?text=Test", "stockQuantity": %d, "veg": false%s}
                """.formatted(categoryId, name, price, stock, version == null ? "" : ", \"version\": " + version);
    }

    private static long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
