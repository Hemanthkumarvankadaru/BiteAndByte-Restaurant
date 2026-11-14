package com.biteandbyte.restaurant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls first-start seeding, bound from {@code biteandbyte.seed.*}.
 *
 * @param menu when true, sample categories and dishes are inserted into an empty menu
 */
@ConfigurationProperties(prefix = "biteandbyte.seed")
public record SeedProperties(boolean menu) {
}
