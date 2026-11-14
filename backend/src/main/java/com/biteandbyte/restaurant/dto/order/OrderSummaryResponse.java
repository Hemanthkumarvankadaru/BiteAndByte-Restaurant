package com.biteandbyte.restaurant.dto.order;

import com.biteandbyte.restaurant.model.CustomerOrder;
import com.biteandbyte.restaurant.model.OrderItem;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row in an order list.
 *
 * @param itemCount total units in the order
 */
public record OrderSummaryResponse(
        @Schema(example = "41") Long id,
        @Schema(example = "BB-20261114-7KQ2M") String orderNumber,
        @Schema(example = "CONFIRMED") OrderStatus status,
        @Schema(example = "COD") PaymentMethod paymentMethod,
        @Schema(example = "627.90") BigDecimal grandTotal,
        @Schema(example = "3") int itemCount,
        @Schema(example = "Priya Sharma") String deliveryName,
        @Schema(example = "Hyderabad") String deliveryCity,
        Instant orderedAt) {

    public static OrderSummaryResponse from(CustomerOrder order) {
        int units = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        return new OrderSummaryResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
                order.getPaymentMethod(), order.getGrandTotal(), units, order.getDeliveryName(),
                order.getDeliveryCity(), order.getOrderedAt());
    }
}
