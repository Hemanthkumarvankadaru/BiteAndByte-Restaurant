package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.config.PricingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Order totals from a food subtotal, using the rules in {@code biteandbyte.pricing}:
 *
 * <pre>
 * tax      = round(subtotal × gst%, 2)            half-up
 * delivery = subtotal &gt; freeDeliveryAbove ? 0 : deliveryCharge
 * total    = subtotal + tax + delivery
 * </pre>
 *
 * Delivery is free only strictly above the threshold, so ₹500.00 pays delivery and ₹500.01 does not.
 * An empty subtotal costs nothing, so an empty cart shows all zeros.
 */
@Component
public class PricingCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Totals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal deliveryCharge, BigDecimal grandTotal) {
    }

    private final PricingProperties pricing;

    public PricingCalculator(PricingProperties pricing) {
        this.pricing = pricing;
    }

    public Totals calculate(BigDecimal subtotal) {
        BigDecimal sub = money(subtotal);
        if (sub.signum() == 0) {
            BigDecimal zero = money(BigDecimal.ZERO);
            return new Totals(zero, zero, zero, zero);
        }
        BigDecimal tax = sub.multiply(pricing.gstPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal delivery = sub.compareTo(pricing.freeDeliveryAbove()) > 0
                ? money(BigDecimal.ZERO)
                : money(pricing.deliveryCharge());
        return new Totals(sub, tax, delivery, sub.add(tax).add(delivery));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
