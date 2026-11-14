package com.biteandbyte.restaurant.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Order pricing rules, bound from {@code biteandbyte.pricing.*}. All amounts in INR.
 *
 * @param gstPercent        GST applied to the food subtotal, for example 5 for 5%
 * @param deliveryCharge    flat delivery fee
 * @param freeDeliveryAbove subtotal strictly above which delivery is free
 */
@Validated
@ConfigurationProperties(prefix = "biteandbyte.pricing")
public record PricingProperties(
        @NotNull @DecimalMin("0") BigDecimal gstPercent,
        @NotNull @DecimalMin("0") BigDecimal deliveryCharge,
        @NotNull @DecimalMin("0") BigDecimal freeDeliveryAbove) {
}
