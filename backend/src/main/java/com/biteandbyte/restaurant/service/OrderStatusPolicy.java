package com.biteandbyte.restaurant.service;

import static com.biteandbyte.restaurant.model.OrderStatus.CANCELLED;
import static com.biteandbyte.restaurant.model.OrderStatus.CONFIRMED;
import static com.biteandbyte.restaurant.model.OrderStatus.DELIVERED;
import static com.biteandbyte.restaurant.model.OrderStatus.OUT_FOR_DELIVERY;
import static com.biteandbyte.restaurant.model.OrderStatus.PENDING;
import static com.biteandbyte.restaurant.model.OrderStatus.PREPARING;

import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.model.OrderStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The order state machine (plan, "Order status flow"). Every status change goes through
 * {@link #check}, so invalid jumps such as DELIVERED back to PENDING are impossible.
 *
 * <pre>
 * PENDING          -> CONFIRMED         system (payment verified, Phase 4)
 * PENDING          -> CANCELLED         system (payment failed or timed out), customer
 * CONFIRMED        -> PREPARING         admin
 * CONFIRMED        -> CANCELLED         customer, admin
 * PREPARING        -> OUT_FOR_DELIVERY  admin
 * PREPARING        -> CANCELLED         admin
 * OUT_FOR_DELIVERY -> DELIVERED         admin
 * OUT_FOR_DELIVERY -> CANCELLED         admin
 * DELIVERED, CANCELLED                  final
 * </pre>
 */
public final class OrderStatusPolicy {

    /** Who is asking for the change. */
    public enum Actor { CUSTOMER, ADMIN, SYSTEM }

    private static final Map<OrderStatus, Map<OrderStatus, Set<Actor>>> RULES = new EnumMap<>(OrderStatus.class);

    static {
        allow(PENDING, CONFIRMED, Actor.SYSTEM);
        allow(PENDING, CANCELLED, Actor.SYSTEM, Actor.CUSTOMER);
        allow(CONFIRMED, PREPARING, Actor.ADMIN);
        allow(CONFIRMED, CANCELLED, Actor.CUSTOMER, Actor.ADMIN);
        allow(PREPARING, OUT_FOR_DELIVERY, Actor.ADMIN);
        allow(PREPARING, CANCELLED, Actor.ADMIN);
        allow(OUT_FOR_DELIVERY, DELIVERED, Actor.ADMIN);
        allow(OUT_FOR_DELIVERY, CANCELLED, Actor.ADMIN);
    }

    private static void allow(OrderStatus from, OrderStatus to, Actor... actors) {
        RULES.computeIfAbsent(from, k -> new EnumMap<>(OrderStatus.class))
                .put(to, EnumSet.copyOf(List.of(actors)));
    }

    public static boolean allows(OrderStatus from, OrderStatus to, Actor actor) {
        return RULES.getOrDefault(from, Map.of()).getOrDefault(to, Set.of()).contains(actor);
    }

    /** Statuses this actor may move the order to next, in the enum's natural order. */
    public static List<OrderStatus> nextStatuses(OrderStatus from, Actor actor) {
        return RULES.getOrDefault(from, Map.of()).entrySet().stream()
                .filter(e -> e.getValue().contains(actor))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Throws 409 {@code INVALID_STATUS_TRANSITION} unless the change is allowed. */
    public static void check(OrderStatus from, OrderStatus to, Actor actor) {
        if (allows(from, to, actor)) {
            return;
        }
        String message;
        if (from == to) {
            message = "The order is already " + label(from);
        } else if (actor == Actor.CUSTOMER && to == CANCELLED) {
            message = "This order can no longer be cancelled because it is " + label(from);
        } else {
            message = "An order cannot move from " + label(from) + " to " + label(to);
        }
        throw ApiException.conflict("INVALID_STATUS_TRANSITION", message);
    }

    private static String label(OrderStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private OrderStatusPolicy() {
    }
}
