package com.biteandbyte.restaurant.controller;

import com.biteandbyte.restaurant.dto.ApiResponse;
import com.biteandbyte.restaurant.dto.address.AddressRequest;
import com.biteandbyte.restaurant.dto.address.AddressResponse;
import com.biteandbyte.restaurant.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Addresses", description = "The customer's saved delivery addresses")
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @Operation(summary = "Your addresses, default first")
    @GetMapping
    public ApiResponse<List<AddressResponse>> list() {
        return ApiResponse.ok(addressService.list());
    }

    @Operation(summary = "Save a new address", description = "The first address becomes the default. At most 10.")
    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> create(@Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(addressService.create(request), "Address saved"));
    }

    @Operation(summary = "Replace one of your addresses")
    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> update(@PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok(addressService.update(id, request), "Address updated");
    }

    @Operation(summary = "Delete one of your addresses",
            description = "Past orders keep their copy of the address. If it was the default, the oldest remaining address becomes the default.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        addressService.delete(id);
        return ApiResponse.message("Address deleted");
    }
}
