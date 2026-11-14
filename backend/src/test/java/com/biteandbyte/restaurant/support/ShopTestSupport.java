package com.biteandbyte.restaurant.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteandbyte.restaurant.support.AuthTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Cart, checkout and order-status steps through the real endpoints. */
public final class ShopTestSupport {

    public static ResultActions addToCart(MockMvc mockMvc, TestUser customer, Long menuItemId, int quantity)
            throws Exception {
        return mockMvc.perform(post("/api/cart/items").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":%d,\"quantity\":%d}".formatted(menuItemId, quantity)));
    }

    public static ResultActions placeOrder(MockMvc mockMvc, TestUser customer, String paymentMethod) throws Exception {
        return mockMvc.perform(post("/api/orders").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"%s\"}".formatted(paymentMethod)));
    }

    /** Adds one dish and checks out with cash on delivery. Returns the new order id. */
    public static long orderOne(MockMvc mockMvc, TestUser customer, Long menuItemId, int quantity) throws Exception {
        addToCart(mockMvc, customer, menuItemId, quantity).andExpect(status().isOk());
        String body = placeOrder(mockMvc, customer, "COD").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    public static ResultActions setStatus(MockMvc mockMvc, TestUser admin, long orderId, String status)
            throws Exception {
        return mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\"}".formatted(status)));
    }

    /** Moves a CONFIRMED order all the way to DELIVERED. */
    public static void deliver(MockMvc mockMvc, TestUser admin, long orderId) throws Exception {
        for (String next : new String[] {"PREPARING", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            setStatus(mockMvc, admin, orderId, next).andExpect(status().isOk());
        }
    }

    /** Reads a JSON number as BigDecimal. JSON numbers arrive as Integer or Double. */
    public static BigDecimal decimal(String json, String path) {
        Object value = JsonPath.read(json, path);
        return new BigDecimal(value.toString());
    }

    private ShopTestSupport() {
    }
}
