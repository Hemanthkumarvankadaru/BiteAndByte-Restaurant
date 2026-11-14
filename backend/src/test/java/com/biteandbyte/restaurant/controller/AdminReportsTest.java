package com.biteandbyte.restaurant.controller;

import static com.biteandbyte.restaurant.support.ShopTestSupport.decimal;
import static com.biteandbyte.restaurant.support.ShopTestSupport.deliver;
import static com.biteandbyte.restaurant.support.ShopTestSupport.orderOne;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteandbyte.restaurant.model.Category;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.repository.CategoryRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import com.biteandbyte.restaurant.support.AuthTestSupport;
import com.biteandbyte.restaurant.support.AuthTestSupport.TestUser;
import com.biteandbyte.restaurant.support.MenuFixtures;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Dashboard and revenue report against real orders. Other tests also deliver orders today in
 * the shared database, so these tests compare before and after instead of absolute totals.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReportsTest {

    /** ₹200 dish: 200 + 5% GST 10 + delivery 40. */
    private static final BigDecimal ORDER_TOTAL = new BigDecimal("250.00");

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categories;
    @Autowired private MenuItemRepository menuItems;

    private TestUser admin;
    private TestUser customer;
    private MenuItem dish;

    @BeforeEach
    void setUp() throws Exception {
        admin = AuthTestSupport.loginAdmin(mockMvc);
        customer = AuthTestSupport.registerCustomer(mockMvc);
        Category category = MenuFixtures.category(categories);
        dish = MenuFixtures.item(menuItems, category, "Report Dish " + AuthTestSupport.uniqueSuffix(), "200.00", 6);
    }

    @Test
    void dashboardCountsTodaysOrdersRevenueAndLowStock() throws Exception {
        String before = dashboard();

        long delivered = orderOne(mockMvc, customer, dish.getId(), 1);
        deliver(mockMvc, admin, delivered);
        orderOne(mockMvc, customer, dish.getId(), 1);   // stays CONFIRMED: placed today, not revenue

        String after = dashboard();
        assertThat(JsonPath.<String>read(after, "$.data.date"))
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
        assertThat(JsonPath.<String>read(after, "$.data.timeZone")).isEqualTo("Asia/Kolkata");
        assertThat(count(after, "$.data.todayOrders")).isEqualTo(count(before, "$.data.todayOrders") + 2);
        assertThat(count(after, "$.data.todayDelivered")).isEqualTo(count(before, "$.data.todayDelivered") + 1);
        assertThat(decimal(after, "$.data.todayRevenue"))
                .isEqualByComparingTo(decimal(before, "$.data.todayRevenue").add(ORDER_TOTAL));
        assertThat(count(after, "$.data.pendingOrders")).isEqualTo(count(before, "$.data.pendingOrders") + 1);
        assertThat(count(after, "$.data.ordersByStatus.DELIVERED"))
                .isEqualTo(count(before, "$.data.ordersByStatus.DELIVERED") + 1);

        // 6 in stock, 2 ordered: 4 left, at or below the threshold of 5.
        List<Integer> stock = JsonPath.read(after, "$.data.lowStockItems[?(@.name == '%s')].stockQuantity"
                .formatted(dish.getName()));
        assertThat(stock).containsExactly(4);
    }

    @Test
    void revenueReportCountsOnlyDeliveredOrdersOnTheirDay() throws Exception {
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        String before = report("daily", today, today);

        long delivered = orderOne(mockMvc, customer, dish.getId(), 1);
        deliver(mockMvc, admin, delivered);
        long cancelled = orderOne(mockMvc, customer, dish.getId(), 1);
        mockMvc.perform(post("/api/orders/" + cancelled + "/cancel").header("Authorization", customer.bearer()))
                .andExpect(status().isOk());

        String after = report("daily", today, today);
        assertThat(JsonPath.<List<?>>read(after, "$.data.buckets")).hasSize(1);
        assertThat(JsonPath.<String>read(after, "$.data.buckets[0].label")).isEqualTo(today);
        assertThat(count(after, "$.data.totalOrders")).isEqualTo(count(before, "$.data.totalOrders") + 1);
        assertThat(decimal(after, "$.data.totalRevenue"))
                .isEqualByComparingTo(decimal(before, "$.data.totalRevenue").add(ORDER_TOTAL));
        assertThat(decimal(after, "$.data.buckets[0].revenue")).isEqualByComparingTo(decimal(after, "$.data.totalRevenue"));
    }

    @Test
    void reportWidensRangesToWholeWeeksAndMonths() throws Exception {
        mockMvc.perform(get("/api/admin/reports/revenue").header("Authorization", admin.bearer())
                        .param("period", "weekly").param("from", "2026-11-11").param("to", "2026-11-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("weekly"))
                .andExpect(jsonPath("$.data.from").value("2026-11-09"))
                .andExpect(jsonPath("$.data.to").value("2026-11-15"))
                .andExpect(jsonPath("$.data.buckets.length()").value(1))
                .andExpect(jsonPath("$.data.buckets[0].label").value("2026-W46"));

        mockMvc.perform(get("/api/admin/reports/revenue").header("Authorization", admin.bearer())
                        .param("period", "MONTHLY").param("from", "2026-10-15").param("to", "2026-11-14"))
                .andExpect(jsonPath("$.data.from").value("2026-10-01"))
                .andExpect(jsonPath("$.data.to").value("2026-11-30"))
                .andExpect(jsonPath("$.data.buckets[*].label").value(org.hamcrest.Matchers.contains("2026-10", "2026-11")));

        // Defaults: the last 30 days.
        mockMvc.perform(get("/api/admin/reports/revenue").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.period").value("daily"))
                .andExpect(jsonPath("$.data.buckets.length()").value(30));
    }

    @Test
    void reportRejectsBadParameters() throws Exception {
        mockMvc.perform(get("/api/admin/reports/revenue").header("Authorization", admin.bearer()).param("period", "yearly"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/admin/reports/revenue").header("Authorization", admin.bearer())
                        .param("from", "2026-11-14").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_DATE_RANGE"));
        mockMvc.perform(get("/api/admin/reports/revenue").header("Authorization", admin.bearer())
                        .param("from", "2024-01-01").param("to", "2026-11-14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DATE_RANGE_TOO_LONG"));
        mockMvc.perform(get("/api/admin/reports/revenue").header("Authorization", admin.bearer()).param("from", "14-11-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    private String dashboard() throws Exception {
        return mockMvc.perform(get("/api/admin/dashboard").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String report(String period, String from, String to) throws Exception {
        return mockMvc.perform(get("/api/admin/reports/revenue").header("Authorization", admin.bearer())
                        .param("period", period).param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static long count(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }
}
