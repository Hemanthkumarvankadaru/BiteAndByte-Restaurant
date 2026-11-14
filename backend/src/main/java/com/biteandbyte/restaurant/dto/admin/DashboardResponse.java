package com.biteandbyte.restaurant.dto.admin;

import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Admin dashboard figures. "Today" is the current day in {@code timeZone}.
 *
 * @param todayOrders     orders placed today, in any status
 * @param todayRevenue    grand total of orders delivered today that count as revenue
 * @param todayDelivered  number of those orders
 * @param pendingOrders   orders not yet finished: PENDING, CONFIRMED, PREPARING or OUT_FOR_DELIVERY
 * @param ordersByStatus  all-time count per status, including zeros
 * @param lowStockItems   live dishes with stock at or below {@code lowStockThreshold}, lowest first
 */
public record DashboardResponse(
        @Schema(example = "2026-11-14") LocalDate date,
        @Schema(example = "Asia/Kolkata") String timeZone,
        @Schema(example = "18") long todayOrders,
        @Schema(example = "8420.50") BigDecimal todayRevenue,
        @Schema(example = "12") long todayDelivered,
        @Schema(example = "5") long pendingOrders,
        Map<OrderStatus, Long> ordersByStatus,
        @Schema(example = "5") int lowStockThreshold,
        List<LowStockItem> lowStockItems) {

    public record LowStockItem(
            @Schema(example = "7") Long id,
            @Schema(example = "Mutton Biryani") String name,
            @Schema(example = "Biryani") String categoryName,
            @Schema(example = "2") int stockQuantity,
            @Schema(example = "true") boolean available) {

        public static LowStockItem from(MenuItem item) {
            return new LowStockItem(item.getId(), item.getName(), item.getCategory().getName(),
                    item.getStockQuantity(), item.isAvailable());
        }
    }
}
