package com.biteandbyte.restaurant.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Set the quantity of a dish already in the cart. To remove it, use DELETE instead of 0. */
public record CartQuantityRequest(
        @Schema(example = "3") @NotNull @Min(1) @Max(CartDtos.MAX_QUANTITY) Integer quantity) {
}
