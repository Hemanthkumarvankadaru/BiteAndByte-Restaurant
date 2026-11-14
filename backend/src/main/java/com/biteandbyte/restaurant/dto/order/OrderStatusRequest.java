package com.biteandbyte.restaurant.dto.order;

import com.biteandbyte.restaurant.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin status change. Allowed moves are listed in {@code allowedNextStatuses} on the order.
 *
 * @param reason stored as the cancel reason when {@code status} is CANCELLED, ignored otherwise
 */
public record OrderStatusRequest(
        @Schema(example = "PREPARING") @NotNull OrderStatus status,
        @Schema(example = "Kitchen closed early") @Size(max = 255) String reason) {
}
