package com.biteandbyte.restaurant.dto.auth;

/**
 * Tokens returned by register, login and refresh.
 *
 * @param accessToken      JWT for the {@code Authorization: Bearer} header
 * @param refreshToken     opaque token for {@code /api/auth/refresh} and {@code /api/auth/logout}
 * @param expiresIn        seconds until the access token expires
 * @param refreshExpiresIn seconds until the refresh token expires
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        UserResponse user) {

    public static final String BEARER = "Bearer";
}
