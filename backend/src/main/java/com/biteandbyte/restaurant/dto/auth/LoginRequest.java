package com.biteandbyte.restaurant.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Log in with either the username or the email address. */
public record LoginRequest(
        @NotBlank @Size(max = 120) String usernameOrEmail,
        @NotBlank @Size(max = 64) String password) {
}
