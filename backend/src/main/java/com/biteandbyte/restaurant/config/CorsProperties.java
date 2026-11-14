package com.biteandbyte.restaurant.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Browser origins allowed to call the API, bound from {@code biteandbyte.cors.allowed-origins}. */
@Validated
@ConfigurationProperties(prefix = "biteandbyte.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
