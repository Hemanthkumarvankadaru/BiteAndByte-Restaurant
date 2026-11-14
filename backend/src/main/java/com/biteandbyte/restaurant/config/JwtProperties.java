package com.biteandbyte.restaurant.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Token settings, bound from {@code biteandbyte.jwt.*}.
 *
 * @param secret          HMAC key for signing access tokens. At least 32 characters, which
 *                        HS256 needs. Comes from {@code BITEANDBYTE_JWT_SECRET}; only the dev
 *                        profile has a built-in value.
 * @param issuer          value of the {@code iss} claim, checked on every request
 * @param accessTokenTtl  lifetime of an access token
 * @param refreshTokenTtl lifetime of a refresh token
 */
@Validated
@ConfigurationProperties(prefix = "biteandbyte.jwt")
public record JwtProperties(
        @NotBlank(message = "BITEANDBYTE_JWT_SECRET must be set")
        @Size(min = 32, message = "BITEANDBYTE_JWT_SECRET must be at least 32 characters") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {
}
