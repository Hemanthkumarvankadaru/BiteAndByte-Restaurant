package com.biteandbyte.restaurant.controller;

import com.biteandbyte.restaurant.dto.ApiResponse;
import com.biteandbyte.restaurant.dto.cart.CartItemRequest;
import com.biteandbyte.restaurant.dto.cart.CartQuantityRequest;
import com.biteandbyte.restaurant.dto.cart.CartResponse;
import com.biteandbyte.restaurant.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every cart operation answers with the whole updated cart, so the client never has to refetch. */
@Tag(name = "Cart", description = "The customer's cart, stored on the server")
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "Your cart with current prices and totals",
            description = "Lines that can no longer be ordered as they are carry a problem code and message.")
    @GetMapping
    public ApiResponse<CartResponse> view() {
        return ApiResponse.ok(cartService.view());
    }

    @Operation(summary = "Add a dish", description = "Adds to the quantity if the dish is already in the cart. "
            + "409 ITEM_UNAVAILABLE, OUT_OF_STOCK or INSUFFICIENT_STOCK when it cannot be ordered.")
    @PostMapping("/items")
    public ApiResponse<CartResponse> add(@Valid @RequestBody CartItemRequest request) {
        return ApiResponse.ok(cartService.add(request));
    }

    @Operation(summary = "Change the quantity of a dish in the cart")
    @PutMapping("/items/{menuItemId}")
    public ApiResponse<CartResponse> setQuantity(@PathVariable Long menuItemId,
                                                 @Valid @RequestBody CartQuantityRequest request) {
        return ApiResponse.ok(cartService.setQuantity(menuItemId, request));
    }

    @Operation(summary = "Remove a dish from the cart")
    @DeleteMapping("/items/{menuItemId}")
    public ApiResponse<CartResponse> remove(@PathVariable Long menuItemId) {
        return ApiResponse.ok(cartService.remove(menuItemId));
    }

    @Operation(summary = "Empty the cart")
    @DeleteMapping
    public ApiResponse<CartResponse> clear() {
        return ApiResponse.ok(cartService.clear());
    }
}
