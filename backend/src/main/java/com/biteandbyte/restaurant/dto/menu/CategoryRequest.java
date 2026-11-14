package com.biteandbyte.restaurant.dto.menu;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or replace a category.
 *
 * @param displayOrder position on the menu, lowest first. Defaults to 0.
 * @param active       inactive categories and their dishes are hidden from customers. Defaults to true.
 */
public record CategoryRequest(
        @Schema(example = "Chaat") @NotBlank @Size(max = 60) String name,
        @Schema(example = "Tangy street-food favourites") @Size(max = 500) String description,
        @Schema(example = "https://placehold.co/600x400?text=Chaat")
        @Size(max = 500) @Pattern(regexp = MenuDtos.IMAGE_URL_PATTERN, message = MenuDtos.IMAGE_URL_MESSAGE) String imageUrl,
        @Schema(example = "9") @Min(0) @Max(10_000) Integer displayOrder,
        @Schema(example = "true") Boolean active) {
}
