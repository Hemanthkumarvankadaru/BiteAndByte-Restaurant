package com.biteandbyte.restaurant.dto.order;

import com.biteandbyte.restaurant.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Check out the whole cart. Prices always come from the menu, never from the client.
 *
 * @param addressId one of the customer's saved addresses. Leave out to use the default address.
 * @param paymentMethod COD orders start CONFIRMED. RAZORPAY orders start PENDING until paid.
 */
public record PlaceOrderRequest(
        @Schema(example = "12") Long addressId,
        @Schema(example = "COD") @NotNull PaymentMethod paymentMethod) {
}
