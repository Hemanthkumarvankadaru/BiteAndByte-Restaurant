package com.biteandbyte.restaurant.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Optional reason for a customer cancellation. The whole body may be left out. */
public record CancelOrderRequest(
        @Schema(example = "Ordered by mistake") @Size(max = 255) String reason) {
}
