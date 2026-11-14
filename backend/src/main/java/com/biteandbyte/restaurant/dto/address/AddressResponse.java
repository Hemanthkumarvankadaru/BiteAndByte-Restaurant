package com.biteandbyte.restaurant.dto.address;

import com.biteandbyte.restaurant.model.Address;
import io.swagger.v3.oas.annotations.media.Schema;

public record AddressResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "Home") String label,
        @Schema(example = "8-2-293 Road No. 12, Banjara Hills") String street,
        @Schema(example = "Hyderabad") String city,
        @Schema(example = "Telangana") String state,
        @Schema(example = "500034") String pincode,
        @Schema(example = "Near the park") String landmark,
        @Schema(example = "true") boolean defaultAddress) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getStreet(), address.getCity(),
                address.getState(), address.getPincode(), address.getLandmark(), address.isDefaultAddress());
    }
}
