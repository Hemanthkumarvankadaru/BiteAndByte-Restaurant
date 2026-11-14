package com.biteandbyte.restaurant.controller;

import static com.biteandbyte.restaurant.support.ShopTestSupport.addToCart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.repository.CategoryRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import com.biteandbyte.restaurant.support.AuthTestSupport;
import com.biteandbyte.restaurant.support.AuthTestSupport.TestUser;
import com.biteandbyte.restaurant.support.MenuFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CartControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categories;
    @Autowired private MenuItemRepository menuItems;

    private TestUser customer;
    private Category category;
    private MenuItem wrap;

    @BeforeEach
    void setUp() throws Exception {
        customer = AuthTestSupport.registerCustomer(mockMvc);
        category = MenuFixtures.category(categories);
        wrap = MenuFixtures.item(menuItems, category, "Cart Wrap", "250.00", 5);
    }

    @Test
    void newCustomerHasAnEmptyCart() throws Exception {
        mockMvc.perform(get("/api/cart").header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andExpect(jsonPath("$.data.grandTotal").value(0.0))
                .andExpect(jsonPath("$.data.deliveryCharge").value(0.0))
                .andExpect(jsonPath("$.data.canCheckout").value(false));
    }

    @Test
    void addingTheSameDishTwiceMergesTheLineAndPricesTheCart() throws Exception {
        addToCart(mockMvc, customer, wrap.getId(), 1).andExpect(status().isOk());
        addToCart(mockMvc, customer, wrap.getId(), 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Cart Wrap"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(500.0))
                .andExpect(jsonPath("$.data.items[0].problem").doesNotExist())
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(500.0))
                .andExpect(jsonPath("$.data.taxAmount").value(25.0))
                .andExpect(jsonPath("$.data.deliveryCharge").value(40.0))
                .andExpect(jsonPath("$.data.grandTotal").value(565.0))
                .andExpect(jsonPath("$.data.canCheckout").value(true));
    }

    @Test
    void rejectsMoreThanTheStock() throws Exception {
        addToCart(mockMvc, customer, wrap.getId(), 6)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Only 5 left of Cart Wrap"));

        addToCart(mockMvc, customer, wrap.getId(), 4).andExpect(status().isOk());
        // 4 already in the cart + 2 more = 6, over the stock of 5.
        addToCart(mockMvc, customer, wrap.getId(), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void rejectsUnavailableSoldOutAndUnknownDishes() throws Exception {
        MenuItem off = MenuFixtures.item(menuItems, category, "Switched Off", "100.00", 5);
        off.setAvailable(false);
        menuItems.save(off);
        MenuItem soldOut = MenuFixtures.item(menuItems, category, "Sold Out", "100.00", 0);

        addToCart(mockMvc, customer, off.getId(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ITEM_UNAVAILABLE"));
        addToCart(mockMvc, customer, soldOut.getId(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OUT_OF_STOCK"));
        addToCart(mockMvc, customer, 999_999L, 1)
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesQuantity() throws Exception {
        addToCart(mockMvc, customer, wrap.getId(), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
        addToCart(mockMvc, customer, wrap.getId(), 51).andExpect(status().isBadRequest());
    }

    @Test
    void changesQuantityRemovesAndClears() throws Exception {
        MenuItem lassi = MenuFixtures.item(menuItems, category, "Cart Lassi", "60.00", 20);
        addToCart(mockMvc, customer, wrap.getId(), 1).andExpect(status().isOk());
        addToCart(mockMvc, customer, lassi.getId(), 1).andExpect(status().isOk());

        mockMvc.perform(put("/api/cart/items/" + lassi.getId()).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(4))
                .andExpect(jsonPath("$.data.subtotal").value(430.0));

        mockMvc.perform(put("/api/cart/items/" + lassi.getId()).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":21}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/cart/items/" + lassi.getId()).header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
        // Removing again is not an error.
        mockMvc.perform(delete("/api/cart/items/" + lassi.getId()).header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
        // Changing a dish that is not in the cart is.
        mockMvc.perform(put("/api/cart/items/" + lassi.getId()).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/cart").header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void cartFlagsLinesThatBecameUnorderable() throws Exception {
        addToCart(mockMvc, customer, wrap.getId(), 3).andExpect(status().isOk());
        wrap.setStockQuantity(2);
        menuItems.save(wrap);

        mockMvc.perform(get("/api/cart").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.items[0].problem").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.data.items[0].problemMessage").value("Only 2 left of Cart Wrap"))
                .andExpect(jsonPath("$.data.canCheckout").value(false));
    }

    @Test
    void eachCustomerHasTheirOwnCart() throws Exception {
        TestUser other = AuthTestSupport.registerCustomer(mockMvc);
        addToCart(mockMvc, customer, wrap.getId(), 1).andExpect(status().isOk());

        mockMvc.perform(get("/api/cart").header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(delete("/api/cart/items/" + wrap.getId()).header("Authorization", other.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cart").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }
}
