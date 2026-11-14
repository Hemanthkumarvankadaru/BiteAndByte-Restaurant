package com.biteandbyte.restaurant.dto.address;

import com.biteandbyte.restaurant.dto.auth.AuthDtos;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or replace a saved delivery address.
 *
 * @param defaultAddress true makes this the default and clears the flag on the others. The
 *                       first address is always the default. To change the default, mark
 *                       another address as default; sending false on the current default
 *                       leaves it unchanged.
 */
public record AddressRequest(
        @Schema(example = "Office") @Size(max = 30) String label,
        @Schema(example = "Plot 41, Hitech City Main Road") @NotBlank @Size(max = 200) String street,
        @Schema(example = "Hyderabad") @NotBlank @Size(max = 60) String city,
        @Schema(example = "Telangana") @NotBlank @Size(max = 60) String state,
        @Schema(example = "500081")
        @NotBlank @Pattern(regexp = AuthDtos.PINCODE_PATTERN, message = AuthDtos.PINCODE_MESSAGE) String pincode,
        @Schema(example = "Opposite Cyber Towers") @Size(max = 100) String landmark,
        @Schema(example = "false") Boolean defaultAddress) {
}
