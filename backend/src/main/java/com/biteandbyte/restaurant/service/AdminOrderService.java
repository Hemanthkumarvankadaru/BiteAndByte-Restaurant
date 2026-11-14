package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.config.StoreProperties;
import com.biteandbyte.restaurant.dto.PageResponse;
import com.biteandbyte.restaurant.dto.order.OrderResponse;
import com.biteandbyte.restaurant.dto.order.OrderStatusRequest;
import com.biteandbyte.restaurant.dto.order.OrderSummaryResponse;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.exception.ResourceNotFoundException;
import com.biteandbyte.restaurant.model.CustomerOrder;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.repository.CustomerOrderRepository;
import com.biteandbyte.restaurant.service.OrderStatusPolicy.Actor;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Order management for the admin: list, view and move orders through the status flow. */
@Service
public class AdminOrderService {

    /**
     * Admin order list filters.
     *
     * @param from first day, inclusive, in the store time zone
     * @param to   last day, inclusive, in the store time zone
     * @param q    matches the order number, customer name or phone
     */
    public record OrderFilter(OrderStatus status, LocalDate from, LocalDate to, String q) {
    }

    private final CustomerOrderRepository orders;
    private final OrderLifecycle lifecycle;
    private final StoreProperties store;

    public AdminOrderService(CustomerOrderRepository orders, OrderLifecycle lifecycle, StoreProperties store) {
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.store = store;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(OrderFilter filter, Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "'from' must not be after 'to'");
        }
        return PageResponse.from(orders.findAll(specification(filter, store.timeZone()), pageable),
                OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return lifecycle.toResponse(find(id), Actor.ADMIN);
    }

    @Transactional
    public OrderResponse changeStatus(Long id, OrderStatusRequest request) {
        CustomerOrder order = find(id);
        lifecycle.transition(order, request.status(), Actor.ADMIN, request.reason());
        orders.flush();
        return lifecycle.toResponse(order, Actor.ADMIN);
    }

    private CustomerOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    static Specification<CustomerOrder> specification(OrderFilter filter, ZoneId zone) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderedAt"), filter.from().atStartOfDay(zone).toInstant()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("orderedAt"), filter.to().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String pattern = MenuService.likePattern(filter.q());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("deliveryName")), pattern, '\\'),
                        cb.like(root.get("deliveryPhone"), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
