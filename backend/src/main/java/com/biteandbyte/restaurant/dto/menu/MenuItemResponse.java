package com.biteandbyte.restaurant.dto.menu;

import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.service.StockCheck;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * A dish as customers see it.
 *
 * @param available admin's on/off switch for the dish
 * @param orderable true when it can be added to the cart right now: available, in stock and
 *                  in an active category
 */
public record MenuItemResponse(
        @Schema(example = "7") Long id,
        @Schema(example = "Hyderabadi Chicken Dum Biryani") String name,
        @Schema(example = "Chicken and basmati sealed and cooked on dum, served with raita") String description,
        @Schema(example = "299.00") BigDecimal price,
        @Schema(example = "https://placehold.co/600x400?text=Biryani") String imageUrl,
        @Schema(example = "false") boolean veg,
        @Schema(example = "true") boolean available,
        @Schema(example = "20") int stockQuantity,
        @Schema(example = "true") boolean orderable,
        @Schema(example = "3") Long categoryId,
        @Schema(example = "Biryani") String categoryName) {

    public static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getName(), item.getDescription(), item.getPrice(),
                item.getImageUrl(), item.isVeg(), item.isAvailable(), item.getStockQuantity(),
                StockCheck.orderable(item), item.getCategory().getId(), item.getCategory().getName());
    }
}
