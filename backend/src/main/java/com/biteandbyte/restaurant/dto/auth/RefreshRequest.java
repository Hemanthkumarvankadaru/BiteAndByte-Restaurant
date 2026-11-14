package com.biteandbyte.restaurant.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for refresh and logout. */
public record RefreshRequest(@NotBlank @Size(max = 100) String refreshToken) {
}
