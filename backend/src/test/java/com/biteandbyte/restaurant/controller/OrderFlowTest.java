package com.biteandbyte.restaurant.controller;

import static com.biteandbyte.restaurant.support.ShopTestSupport.addToCart;
import static com.biteandbyte.restaurant.support.ShopTestSupport.deliver;
import static com.biteandbyte.restaurant.support.ShopTestSupport.orderOne;
import static com.biteandbyte.restaurant.support.ShopTestSupport.placeOrder;
import static com.biteandbyte.restaurant.support.ShopTestSupport.setStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.repository.CartItemRepository;
import com.biteandbyte.restaurant.repository.CategoryRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import com.biteandbyte.restaurant.repository.UserRepository;
import com.biteandbyte.restaurant.support.AuthTestSupport;
import com.biteandbyte.restaurant.support.AuthTestSupport.TestUser;
import com.biteandbyte.restaurant.support.MenuFixtures;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Checkout, order history, cancellation and the admin status flow, end to end. */
@SpringBootTest
@AutoConfigureMockMvc
class OrderFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categories;
    @Autowired private MenuItemRepository menuItems;
    @Autowired private CartItemRepository cartItems;
    @Autowired private UserRepository users;

    private TestUser customer;
    private TestUser admin;
    private Category category;
    private MenuItem curry;

    @BeforeEach
    void setUp() throws Exception {
        customer = AuthTestSupport.registerCustomer(mockMvc);
        admin = AuthTestSupport.loginAdmin(mockMvc);
        category = MenuFixtures.category(categories);
        curry = MenuFixtures.item(menuItems, category, "Order Curry", "250.00", 10);
    }

    // ---- Placing orders ----

    @Test
    void codOrderIsConfirmedTakesStockEmptiesCartAndCopiesTheAddress() throws Exception {
        addToCart(mockMvc, customer, curry.getId(), 2).andExpect(status().isOk());

        placeOrder(mockMvc, customer, "COD")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/orders/\\d+")))
                .andExpect(jsonPath("$.message").value("Order placed"))
                .andExpect(jsonPath("$.data.orderNumber", matchesPattern("^BB-\\d{8}-[2-9A-HJ-NP-Z]{5}$")))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentMethod").value("COD"))
                // ₹500 exactly: delivery is not free.
                .andExpect(jsonPath("$.data.subtotal").value(500.0))
                .andExpect(jsonPath("$.data.taxAmount").value(25.0))
                .andExpect(jsonPath("$.data.deliveryCharge").value(40.0))
                .andExpect(jsonPath("$.data.grandTotal").value(565.0))
                .andExpect(jsonPath("$.data.items[0].itemName").value("Order Curry"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(250.0))
                .andExpect(jsonPath("$.data.deliveryAddress.name").value("Test Customer"))
                .andExpect(jsonPath("$.data.deliveryAddress.phone").value(customer.phone()))
                .andExpect(jsonPath("$.data.deliveryAddress.pincode").value("500034"))
                .andExpect(jsonPath("$.data.payments[0].method").value("COD"))
                .andExpect(jsonPath("$.data.payments[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.payments[0].amount").value(565.0))
                .andExpect(jsonPath("$.data.allowedNextStatuses", contains("CANCELLED")))
                .andExpect(jsonPath("$.data.customer").doesNotExist());

        assertThat(stockOf(curry)).isEqualTo(8);
        mockMvc.perform(get("/api/cart").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.items", empty()));
    }

    @Test
    void deliveryIsFreeOnePaisaAboveTheThreshold() throws Exception {
        MenuItem platter = MenuFixtures.item(menuItems, category, "Big Platter", "500.01", 5);
        addToCart(mockMvc, customer, platter.getId(), 1).andExpect(status().isOk());

        placeOrder(mockMvc, customer, "COD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal").value(500.01))
                .andExpect(jsonPath("$.data.taxAmount").value(25.0))
                .andExpect(jsonPath("$.data.deliveryCharge").value(0.0))
                .andExpect(jsonPath("$.data.grandTotal").value(525.01));
    }

    @Test
    void pricesComeFromTheMenuAtCheckoutTime() throws Exception {
        addToCart(mockMvc, customer, curry.getId(), 1).andExpect(status().isOk());
        MenuItem changed = menuItems.findById(curry.getId()).orElseThrow();
        changed.setPrice(new BigDecimal("270.00"));
        menuItems.save(changed);

        placeOrder(mockMvc, customer, "COD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(270.0))
                .andExpect(jsonPath("$.data.subtotal").value(270.0));
    }

    @Test
    void emptyCartCannotBeCheckedOut() throws Exception {
        placeOrder(mockMvc, customer, "COD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CART_EMPTY"));
    }

    @Test
    void paymentMethodIsRequired() throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("paymentMethod"));
    }

    @Test
    void unavailableDishBlocksCheckoutAndNothingChanges() throws Exception {
        addToCart(mockMvc, customer, curry.getId(), 1).andExpect(status().isOk());
        MenuItem off = menuItems.findById(curry.getId()).orElseThrow();
        off.setAvailable(false);
        menuItems.save(off);

        placeOrder(mockMvc, customer, "COD")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ITEM_UNAVAILABLE"));

        assertThat(stockOf(curry)).isEqualTo(10);
        mockMvc.perform(get("/api/cart").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(get("/api/orders").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void ordersCanOnlyGoToYourOwnAddress() throws Exception {
        TestUser other = AuthTestSupport.registerCustomer(mockMvc);
        String body = mockMvc.perform(get("/api/addresses").header("Authorization", other.bearer()))
                .andReturn().getResponse().getContentAsString();
        int othersAddress = JsonPath.read(body, "$.data[0].id");
        addToCart(mockMvc, customer, curry.getId(), 1).andExpect(status().isOk());

        mockMvc.perform(post("/api/orders").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"COD\",\"addressId\":%d}".formatted(othersAddress)))
                .andExpect(status().isNotFound());
    }

    @Test
    void razorpayOrderWaitsForPayment() throws Exception {
        addToCart(mockMvc, customer, curry.getId(), 1).andExpect(status().isOk());

        String body = placeOrder(mockMvc, customer, "RAZORPAY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.payments", empty()))
                .andExpect(jsonPath("$.data.allowedNextStatuses", contains("CANCELLED")))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        // Only the payment step (Phase 4) may confirm it.
        setStatus(mockMvc, admin, id, "PREPARING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
        assertThat(stockOf(curry)).isEqualTo(9);
    }

    // ---- History and ownership ----

    @Test
    void customersOnlySeeTheirOwnOrders() throws Exception {
        long orderId = orderOne(mockMvc, customer, curry.getId(), 1);
        TestUser other = AuthTestSupport.registerCustomer(mockMvc);

        mockMvc.perform(get("/api/orders").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(orderId))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(1));
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", customer.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders").header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyFiltersByStatus() throws Exception {
        long first = orderOne(mockMvc, customer, curry.getId(), 1);
        orderOne(mockMvc, customer, curry.getId(), 1);
        mockMvc.perform(post("/api/orders/" + first + "/cancel").header("Authorization", customer.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders").param("status", "CANCELLED").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(first));
        mockMvc.perform(get("/api/orders").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    // ---- Cancelling ----

    @Test
    void customerCancelPutsStockBackAndFailsThePayment() throws Exception {
        long orderId = orderOne(mockMvc, customer, curry.getId(), 3);
        assertThat(stockOf(curry)).isEqualTo(7);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ordered by mistake\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Ordered by mistake"))
                .andExpect(jsonPath("$.data.cancelledAt", notNullValue()))
                .andExpect(jsonPath("$.data.payments[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.allowedNextStatuses", empty()));
        assertThat(stockOf(curry)).isEqualTo(10);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", customer.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
        assertThat(stockOf(curry)).isEqualTo(10);
    }

    @Test
    void customerCannotCancelOnceTheKitchenStarts() throws Exception {
        long orderId = orderOne(mockMvc, customer, curry.getId(), 1);
        setStatus(mockMvc, admin, orderId, "PREPARING").andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", customer.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This order can no longer be cancelled because it is preparing"));
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.allowedNextStatuses", empty()));
    }

    // ---- Admin status flow ----

    @Test
    void adminMovesTheOrderThroughTheFlow() throws Exception {
        long orderId = orderOne(mockMvc, customer, curry.getId(), 1);

        mockMvc.perform(get("/api/admin/orders/" + orderId).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customer.username").value(customer.username()))
                .andExpect(jsonPath("$.data.allowedNextStatuses", contains("PREPARING", "CANCELLED")));

        setStatus(mockMvc, admin, orderId, "DELIVERED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An order cannot move from confirmed to delivered"));

        deliver(mockMvc, admin, orderId);

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.deliveredAt", notNullValue()))
                .andExpect(jsonPath("$.data.payments[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.payments[0].paidAt", notNullValue()));

        setStatus(mockMvc, admin, orderId, "CANCELLED").andExpect(status().isConflict());
        assertThat(stockOf(curry)).isEqualTo(9);
    }

    @Test
    void adminCancelPutsStockBack() throws Exception {
        long orderId = orderOne(mockMvc, customer, curry.getId(), 4);
        setStatus(mockMvc, admin, orderId, "PREPARING").andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"reason\":\"Out of gas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelReason").value("Out of gas"));
        assertThat(stockOf(curry)).isEqualTo(10);
    }

    @Test
    void adminSearchesOrders() throws Exception {
        long orderId = orderOne(mockMvc, customer, curry.getId(), 1);
        String number = JsonPath.read(mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", customer.bearer())).andReturn().getResponse().getContentAsString(),
                "$.data.orderNumber");

        mockMvc.perform(get("/api/admin/orders").param("q", number.toLowerCase()).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(number));
        mockMvc.perform(get("/api/admin/orders").param("q", number).param("status", "DELIVERED")
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/admin/orders").param("q", number).param("to", "2020-01-01")
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/admin/orders").param("from", "2026-11-14").param("to", "2026-11-01")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_DATE_RANGE"));
        mockMvc.perform(get("/api/admin/orders").param("status", "LOST").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
    }

    // ---- Stock race (gap G10) ----

    @Test
    void lastUnitGoesToExactlyOneOfTwoBuyersAtTheSameTime() throws Exception {
        MenuItem last = MenuFixtures.item(menuItems, category, "Last Biryani", "300.00", 1);
        TestUser first = AuthTestSupport.registerCustomer(mockMvc);
        TestUser second = AuthTestSupport.registerCustomer(mockMvc);
        addToCart(mockMvc, first, last.getId(), 1).andExpect(status().isOk());
        addToCart(mockMvc, second, last.getId(), 1).andExpect(status().isOk());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MockHttpServletResponse>> results = new ArrayList<>();
            for (TestUser buyer : List.of(first, second)) {
                Callable<MockHttpServletResponse> checkout = () -> {
                    start.await();
                    return placeOrder(mockMvc, buyer, "COD").andReturn().getResponse();
                };
                results.add(pool.submit(checkout));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (Future<MockHttpServletResponse> result : results) {
                MockHttpServletResponse response = result.get(30, TimeUnit.SECONDS);
                statuses.add(response.getStatus());
                if (response.getStatus() == 409) {
                    errors.add(JsonPath.read(response.getContentAsString(), "$.error"));
                }
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            // Lost the version check while both were in flight, or arrived after the winner committed.
            assertThat(errors).singleElement().isIn("CONCURRENT_UPDATE", "OUT_OF_STOCK");
        } finally {
            pool.shutdownNow();
        }
        assertThat(stockOf(last)).isZero();
        // The loser's cart is untouched, so they can see the problem and adjust.
        long loserLines = cartItems.findByUserIdOrderByAddedAtAsc(userId(first)).size()
                + cartItems.findByUserIdOrderByAddedAtAsc(userId(second)).size();
        assertThat(loserLines).isEqualTo(1);
    }

    private int stockOf(MenuItem item) {
        return menuItems.findById(item.getId()).orElseThrow().getStockQuantity();
    }

    private Long userId(TestUser user) {
        return users.findByUsernameIgnoreCase(user.username()).orElseThrow().getId();
    }
}
