package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.dto.order.OrderResponse;
import com.biteandbyte.restaurant.model.CustomerOrder;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.model.OrderItem;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.model.Payment;
import com.biteandbyte.restaurant.model.PaymentMethod;
import com.biteandbyte.restaurant.model.PaymentStatus;
import com.biteandbyte.restaurant.repository.PaymentRepository;
import com.biteandbyte.restaurant.service.OrderStatusPolicy.Actor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Applies status changes and their side effects. Every change, by customer, admin or the
 * payment code, goes through {@link #transition}, so the rules are enforced in one place.
 *
 * <ul>
 *   <li>CANCELLED: stock goes back to the menu, pending payments become FAILED.</li>
 *   <li>DELIVERED: delivery time is set and a pending cash-on-delivery payment becomes SUCCESS.</li>
 * </ul>
 *
 * Callers must run inside a transaction; changes are saved by dirty checking.
 */
@Component
public class OrderLifecycle {

    private final PaymentRepository payments;
    private final Clock clock;

    public OrderLifecycle(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    /**
     * @param reason only used for CANCELLED. A default is filled in when blank.
     * @throws com.biteandbyte.restaurant.exception.ApiException 409 if the policy forbids the change
     */
    public void transition(CustomerOrder order, OrderStatus target, Actor actor, String reason) {
        OrderStatusPolicy.check(order.getStatus(), target, actor);
        Instant now = clock.instant();
        if (target == OrderStatus.CANCELLED) {
            cancel(order, actor, reason, now);
        } else if (target == OrderStatus.DELIVERED) {
            deliver(order, now);
        }
        order.setStatus(target);
    }

    /** Full detail for this viewer. The admin view also shows who placed the order. */
    public OrderResponse toResponse(CustomerOrder order, Actor viewer) {
        return OrderResponse.from(order, payments.findByOrderIdOrderByIdAsc(order.getId()),
                OrderStatusPolicy.nextStatuses(order.getStatus(), viewer), viewer == Actor.ADMIN);
    }

    private void cancel(CustomerOrder order, Actor actor, String reason, Instant now) {
        order.setCancelledAt(now);
        order.setCancelReason(reason == null || reason.isBlank() ? defaultReason(actor) : reason.trim());
        for (OrderItem line : order.getItems()) {
            MenuItem item = line.getMenuItem();
            item.setStockQuantity(item.getStockQuantity() + line.getQuantity());
        }
        // A paid online order also needs a refund. Phase 4 adds that when it adds Razorpay.
        for (Payment payment : payments.findByOrderIdOrderByIdAsc(order.getId())) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.FAILED);
            }
        }
    }

    private void deliver(CustomerOrder order, Instant now) {
        order.setDeliveredAt(now);
        for (Payment payment : payments.findByOrderIdOrderByIdAsc(order.getId())) {
            if (payment.getMethod() == PaymentMethod.COD && payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(now);
            }
        }
    }

    private static String defaultReason(Actor actor) {
        return switch (actor) {
            case CUSTOMER -> "Cancelled by customer";
            case ADMIN -> "Cancelled by the restaurant";
            case SYSTEM -> "Cancelled automatically";
        };
    }
}
