package com.biteandbyte.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.biteandbyte.restaurant.config.PricingProperties;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Pricing rules from the plan: GST 5%, ₹40 delivery, free strictly above ₹500. */
class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator(
            new PricingProperties(new BigDecimal("5"), new BigDecimal("40.00"), new BigDecimal("500.00")));

    @ParameterizedTest(name = "subtotal {0} -> tax {1}, delivery {2}, total {3}")
    @CsvSource({
            // Exactly at the threshold still pays delivery.
            "500.00, 25.00, 40.00, 565.00",
            // One paisa above is free. Tax 25.0005 rounds down.
            "500.01, 25.00, 0.00, 525.01",
            // Tax 4.995 rounds half up.
            "99.90, 5.00, 40.00, 144.90",
            "49.00, 2.45, 40.00, 91.45",
            "1000.00, 50.00, 0.00, 1050.00",
            // Empty cart: nothing to pay, not even delivery.
            "0, 0.00, 0.00, 0.00"
    })
    void calculatesTotals(String subtotal, String tax, String delivery, String total) {
        PricingCalculator.Totals totals = calculator.calculate(new BigDecimal(subtotal));

        assertThat(totals.subtotal()).isEqualByComparingTo(subtotal);
        assertThat(totals.taxAmount()).isEqualTo(new BigDecimal(tax));
        assertThat(totals.deliveryCharge()).isEqualTo(new BigDecimal(delivery));
        assertThat(totals.grandTotal()).isEqualTo(new BigDecimal(total));
        assertThat(totals.grandTotal().scale()).isEqualTo(2);
    }
}
