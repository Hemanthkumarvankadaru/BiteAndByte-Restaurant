package com.biteandbyte.restaurant.dto.menu;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create or replace a menu item.
 *
 * @param available defaults to true when left out
 * @param version   optional on update: the version the admin last saw. A mismatch means someone
 *                  else changed the item in between, and the update is rejected with 409.
 */
public record MenuItemRequest(
        @Schema(example = "3") @NotNull Long categoryId,
        @Schema(example = "Hyderabadi Chicken Dum Biryani") @NotBlank @Size(max = 100) String name,
        @Schema(example = "Chicken and basmati sealed and cooked on dum, served with raita") @Size(max = 1000) String description,
        @Schema(example = "299.00")
        @NotNull @DecimalMin(value = "0.01", message = MenuDtos.PRICE_MESSAGE)
        @Digits(integer = 8, fraction = 2, message = MenuDtos.PRICE_DIGITS_MESSAGE) BigDecimal price,
        @Schema(example = "https://placehold.co/600x400?text=Biryani")
        @Size(max = 500) @Pattern(regexp = MenuDtos.IMAGE_URL_PATTERN, message = MenuDtos.IMAGE_URL_MESSAGE) String imageUrl,
        @Schema(example = "20") @NotNull @Min(0) @Max(MenuDtos.MAX_STOCK) Integer stockQuantity,
        @Schema(example = "true") Boolean available,
        @Schema(example = "false") @NotNull Boolean veg,
        @Schema(example = "4") Long version) {
}
