package com.biteandbyte.restaurant.dto.menu;

import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.service.StockCheck;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A dish as the admin sees it, including soft-deleted state and the lock version.
 *
 * @param version send this back on update to detect edits made by someone else in between
 */
public record AdminMenuItemResponse(
        @Schema(example = "7") Long id,
        @Schema(example = "Hyderabadi Chicken Dum Biryani") String name,
        String description,
        @Schema(example = "299.00") BigDecimal price,
        String imageUrl,
        boolean veg,
        boolean available,
        @Schema(example = "20") int stockQuantity,
        boolean orderable,
        boolean deleted,
        @Schema(example = "3") Long categoryId,
        @Schema(example = "Biryani") String categoryName,
        @Schema(example = "4") long version,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminMenuItemResponse from(MenuItem item) {
        return new AdminMenuItemResponse(item.getId(), item.getName(), item.getDescription(), item.getPrice(),
                item.getImageUrl(), item.isVeg(), item.isAvailable(), item.getStockQuantity(),
                StockCheck.orderable(item), item.isDeleted(), item.getCategory().getId(),
                item.getCategory().getName(), item.getVersion(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
