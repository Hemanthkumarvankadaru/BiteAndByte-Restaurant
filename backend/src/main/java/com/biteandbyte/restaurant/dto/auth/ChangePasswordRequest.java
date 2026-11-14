package com.biteandbyte.restaurant.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 64) String currentPassword,
        @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_PATTERN, message = AuthDtos.PASSWORD_MESSAGE) String newPassword) {
}
