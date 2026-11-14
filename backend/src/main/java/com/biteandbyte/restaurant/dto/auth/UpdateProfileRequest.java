package com.biteandbyte.restaurant.dto.auth;

import com.biteandbyte.restaurant.model.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Replace the customer's profile. The username cannot be changed. */
public record UpdateProfileRequest(
        @Schema(example = "Priya Sharma") @NotBlank @Size(min = 2, max = 100) String fullName,
        @Schema(example = "priya@example.com") @NotBlank @Email @Size(max = 120) String email,
        @Schema(example = "9876543210")
        @NotBlank @Pattern(regexp = AuthDtos.PHONE_PATTERN, message = AuthDtos.PHONE_MESSAGE) String phone,
        @Schema(example = "29") @NotNull @Min(13) @Max(120) Integer age,
        @Schema(example = "FEMALE") @NotNull Gender gender) {
}
