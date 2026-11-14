package com.biteandbyte.restaurant.dto.cart;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * The customer's cart, priced with current menu prices.
 *
 * @param itemCount   total units across all lines
 * @param canCheckout true when the cart is not empty and every line can be ordered as is
 */
public record CartResponse(
        List<Line> items,
        @Schema(example = "3") int itemCount,
        @Schema(example = "598.00") BigDecimal subtotal,
        @Schema(example = "29.90") BigDecimal taxAmount,
        @Schema(example = "0.00") BigDecimal deliveryCharge,
        @Schema(example = "627.90") BigDecimal grandTotal,
        @Schema(example = "true") boolean canCheckout) {

    /**
     * One cart line.
     *
     * @param problem        null when the line can be ordered, otherwise UNAVAILABLE,
     *                       OUT_OF_STOCK or INSUFFICIENT_STOCK
     * @param problemMessage text to show next to the line
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(
            @Schema(example = "7") Long menuItemId,
            @Schema(example = "Hyderabadi Chicken Dum Biryani") String name,
            String imageUrl,
            boolean veg,
            @Schema(example = "299.00") BigDecimal unitPrice,
            @Schema(example = "2") int quantity,
            @Schema(example = "598.00") BigDecimal lineTotal,
            @Schema(example = "20") int stockQuantity,
            String problem,
            String problemMessage) {
    }
}
