package com.biteandbyte.restaurant.controller;

import com.biteandbyte.restaurant.dto.ApiResponse;
import com.biteandbyte.restaurant.dto.PageResponse;
import com.biteandbyte.restaurant.dto.order.CancelOrderRequest;
import com.biteandbyte.restaurant.dto.order.OrderResponse;
import com.biteandbyte.restaurant.dto.order.OrderSummaryResponse;
import com.biteandbyte.restaurant.dto.order.PlaceOrderRequest;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders", description = "Checkout and the customer's own orders")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    static final Set<String> SORTABLE = Set.of("orderedAt", "grandTotal");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "orderedAt", "id");

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Place an order from the cart",
            description = "Prices come from the menu, not the client. Stock is taken and the cart is emptied in "
                    + "the same transaction. COD orders start CONFIRMED; RAZORPAY orders start PENDING until paid. "
                    + "400 CART_EMPTY or ADDRESS_REQUIRED, 409 ITEM_UNAVAILABLE, OUT_OF_STOCK, INSUFFICIENT_STOCK "
                    + "or CONCURRENT_UPDATE (someone bought the last unit first: try again).")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> place(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse order = orderService.place(request);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id()))
                .body(ApiResponse.ok(order, "Order placed"));
    }

    @Operation(summary = "Your orders, newest first")
    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = Paging.DEFAULT_SIZE) int size,
            @Parameter(description = "orderedAt or grandTotal, then ,asc or ,desc", example = "orderedAt,desc")
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(orderService.list(status, Paging.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    @Operation(summary = "One of your orders")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(orderService.get(id));
    }

    @Operation(summary = "Cancel one of your orders",
            description = "Allowed while the order is PENDING or CONFIRMED. Stock goes back to the menu. The body is optional.")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return ApiResponse.ok(orderService.cancel(id, request == null ? null : request.reason()), "Order cancelled");
    }
}
