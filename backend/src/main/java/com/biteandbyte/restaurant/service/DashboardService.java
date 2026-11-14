package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.config.StoreProperties;
import com.biteandbyte.restaurant.dto.admin.DashboardResponse;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.repository.CustomerOrderRepository;
import com.biteandbyte.restaurant.repository.MenuItemRepository;
import com.biteandbyte.restaurant.repository.RevenueRow;
import com.biteandbyte.restaurant.repository.StatusCount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Headline numbers for the admin dashboard. "Today" starts at midnight in the store time zone. */
@Service
public class DashboardService {

    /** Statuses of orders the kitchen still has to finish. */
    static final Set<OrderStatus> OPEN_STATUSES =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY);

    private final CustomerOrderRepository orders;
    private final MenuItemRepository menuItems;
    private final StoreProperties store;
    private final Clock clock;

    public DashboardService(CustomerOrderRepository orders, MenuItemRepository menuItems, StoreProperties store,
                            Clock clock) {
        this.orders = orders;
        this.menuItems = menuItems;
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        ZoneId zone = store.timeZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();

        long todayOrders = orders.countByOrderedAtGreaterThanEqualAndOrderedAtLessThan(start, end);
        List<RevenueRow> delivered = orders.findRevenueRows(start, end);
        BigDecimal todayRevenue = delivered.stream()
                .map(RevenueRow::grandTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            byStatus.put(status, 0L);
        }
        for (StatusCount count : orders.countByStatusGrouped()) {
            byStatus.put(count.status(), count.count());
        }
        long pending = OPEN_STATUSES.stream().mapToLong(byStatus::get).sum();

        List<DashboardResponse.LowStockItem> lowStock = menuItems
                .findByDeletedFalseAndStockQuantityLessThanEqualOrderByStockQuantityAsc(store.lowStockThreshold())
                .stream()
                .map(DashboardResponse.LowStockItem::from)
                .toList();

        return new DashboardResponse(today, zone.getId(), todayOrders, todayRevenue, delivered.size(), pending,
                byStatus, store.lowStockThreshold(), lowStock);
    }
}
