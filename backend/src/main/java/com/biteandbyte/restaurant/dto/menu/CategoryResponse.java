package com.biteandbyte.restaurant.dto.menu;

import com.biteandbyte.restaurant.model.Category;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A menu category.
 *
 * @param itemCount number of dishes in it, not counting deleted ones
 */
public record CategoryResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "Biryani") String name,
        @Schema(example = "Slow-cooked dum biryanis layered with fragrant basmati") String description,
        @Schema(example = "https://placehold.co/600x400?text=Biryani") String imageUrl,
        @Schema(example = "3") int displayOrder,
        @Schema(example = "true") boolean active,
        @Schema(example = "3") long itemCount) {

    public static CategoryResponse from(Category category, long itemCount) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getImageUrl(), category.getDisplayOrder(), category.isActive(), itemCount);
    }
}
