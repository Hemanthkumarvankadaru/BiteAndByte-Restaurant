package com.biteandbyte.restaurant.dto.auth;

import com.biteandbyte.restaurant.model.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Customer sign-up, including the first delivery address. */
public record RegisterRequest(
        @NotBlank @Pattern(regexp = AuthDtos.USERNAME_PATTERN, message = AuthDtos.USERNAME_MESSAGE) String username,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = AuthDtos.PHONE_PATTERN, message = AuthDtos.PHONE_MESSAGE) String phone,
        @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_PATTERN, message = AuthDtos.PASSWORD_MESSAGE) String password,
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotNull @Min(13) @Max(120) Integer age,
        @NotNull Gender gender,
        @NotNull @Valid AddressRequest address) {

    /** The first delivery address. Saved addresses after that use {@code dto.address.AddressRequest}. */
    public record AddressRequest(
            @Size(max = 30) String label,
            @NotBlank @Size(max = 200) String street,
            @NotBlank @Size(max = 60) String city,
            @NotBlank @Size(max = 60) String state,
            @NotBlank @Pattern(regexp = AuthDtos.PINCODE_PATTERN, message = AuthDtos.PINCODE_MESSAGE) String pincode,
            @Size(max = 100) String landmark) {
    }
}
