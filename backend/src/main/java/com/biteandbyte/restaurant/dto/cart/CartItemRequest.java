package com.biteandbyte.restaurant.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Add a dish to the cart. If it is already there, the quantities are added together. */
public record CartItemRequest(
        @Schema(example = "7") @NotNull Long menuItemId,
        @Schema(example = "2") @NotNull @Min(1) @Max(CartDtos.MAX_QUANTITY) Integer quantity) {
}
