package com.biteandbyte.restaurant.service;

import static com.biteandbyte.restaurant.model.OrderStatus.CANCELLED;
import static com.biteandbyte.restaurant.model.OrderStatus.CONFIRMED;
import static com.biteandbyte.restaurant.model.OrderStatus.DELIVERED;
import static com.biteandbyte.restaurant.model.OrderStatus.OUT_FOR_DELIVERY;
import static com.biteandbyte.restaurant.model.OrderStatus.PENDING;
import static com.biteandbyte.restaurant.model.OrderStatus.PREPARING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.service.OrderStatusPolicy.Actor;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OrderStatusPolicyTest {

    /** Every allowed move, as "FROM>TO:ACTOR". Anything not listed must be refused. */
    private static final Set<String> ALLOWED = Set.of(
            "PENDING>CONFIRMED:SYSTEM",
            "PENDING>CANCELLED:SYSTEM",
            "PENDING>CANCELLED:CUSTOMER",
            "CONFIRMED>PREPARING:ADMIN",
            "CONFIRMED>CANCELLED:CUSTOMER",
            "CONFIRMED>CANCELLED:ADMIN",
            "PREPARING>OUT_FOR_DELIVERY:ADMIN",
            "PREPARING>CANCELLED:ADMIN",
            "OUT_FOR_DELIVERY>DELIVERED:ADMIN",
            "OUT_FOR_DELIVERY>CANCELLED:ADMIN");

    @Test
    void allowsExactlyTheDocumentedMoves() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                for (Actor actor : Actor.values()) {
                    String move = from + ">" + to + ":" + actor;
                    assertThat(OrderStatusPolicy.allows(from, to, actor)).as(move).isEqualTo(ALLOWED.contains(move));
                }
            }
        }
    }

    @Test
    void finishedOrdersCannotMove() {
        for (Actor actor : Actor.values()) {
            assertThat(OrderStatusPolicy.nextStatuses(DELIVERED, actor)).isEmpty();
            assertThat(OrderStatusPolicy.nextStatuses(CANCELLED, actor)).isEmpty();
        }
    }

    @Test
    void listsNextStatusesPerActor() {
        assertThat(OrderStatusPolicy.nextStatuses(CONFIRMED, Actor.ADMIN)).containsExactly(PREPARING, CANCELLED);
        assertThat(OrderStatusPolicy.nextStatuses(CONFIRMED, Actor.CUSTOMER)).containsExactly(CANCELLED);
        assertThat(OrderStatusPolicy.nextStatuses(PREPARING, Actor.CUSTOMER)).isEmpty();
        assertThat(OrderStatusPolicy.nextStatuses(PENDING, Actor.ADMIN)).isEmpty();
        assertThat(OrderStatusPolicy.nextStatuses(OUT_FOR_DELIVERY, Actor.ADMIN)).containsExactly(DELIVERED, CANCELLED);
    }

    @Test
    void checkRejectsInvalidMovesWithConflict() {
        assertThatNoException().isThrownBy(() -> OrderStatusPolicy.check(CONFIRMED, PREPARING, Actor.ADMIN));

        assertThatThrownBy(() -> OrderStatusPolicy.check(DELIVERED, PENDING, Actor.ADMIN))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("INVALID_STATUS_TRANSITION");
                    assertThat(e.getMessage()).isEqualTo("An order cannot move from delivered to pending");
                });
        assertThatThrownBy(() -> OrderStatusPolicy.check(PREPARING, CANCELLED, Actor.CUSTOMER))
                .hasMessage("This order can no longer be cancelled because it is preparing");
        assertThatThrownBy(() -> OrderStatusPolicy.check(CANCELLED, CANCELLED, Actor.ADMIN))
                .hasMessage("The order is already cancelled");
    }
}
