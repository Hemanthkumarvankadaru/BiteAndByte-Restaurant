package com.biteandbyte.restaurant.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The admin account created on first start, bound from {@code biteandbyte.admin.*}.
 * The password comes from {@code BITEANDBYTE_ADMIN_PASSWORD} and defaults to "admin" for local dev only.
 */
@Validated
@ConfigurationProperties(prefix = "biteandbyte.admin")
public record AdminProperties(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String email,
        @NotBlank String phone,
        @NotBlank String fullName) {

    public static final String INSECURE_DEFAULT_PASSWORD = "admin";

    public boolean usesDefaultPassword() {
        return INSECURE_DEFAULT_PASSWORD.equals(password);
    }
}
