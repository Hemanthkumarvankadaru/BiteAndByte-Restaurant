package com.biteandbyte.restaurant.dto.menu;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Set the stock count of a menu item. Zero stock makes the item unorderable; restocking makes
 * it orderable again unless the admin has switched it off.
 *
 * @param version optional: the version the admin last saw, to reject a stale update with 409
 */
public record StockUpdateRequest(
        @Schema(example = "25") @NotNull @Min(0) @Max(MenuDtos.MAX_STOCK) Integer stockQuantity,
        @Schema(example = "4") Long version) {
}
