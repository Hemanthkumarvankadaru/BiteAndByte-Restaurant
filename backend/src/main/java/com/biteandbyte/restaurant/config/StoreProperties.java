package com.biteandbyte.restaurant.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Restaurant-wide settings, bound from {@code biteandbyte.store.*}.
 *
 * @param timeZone          business time zone. Order numbers, "today" on the dashboard and
 *                          report days are cut at midnight in this zone. Timestamps are still
 *                          stored in UTC.
 * @param lowStockThreshold menu items at or below this stock show as low stock on the dashboard
 */
@Validated
@ConfigurationProperties(prefix = "biteandbyte.store")
public record StoreProperties(
        @NotNull ZoneId timeZone,
        @Min(0) int lowStockThreshold) {
}
