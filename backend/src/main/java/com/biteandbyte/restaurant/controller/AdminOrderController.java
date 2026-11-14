package com.biteandbyte.restaurant.controller;

import com.biteandbyte.restaurant.dto.ApiResponse;
import com.biteandbyte.restaurant.dto.PageResponse;
import com.biteandbyte.restaurant.dto.admin.DashboardResponse;
import com.biteandbyte.restaurant.dto.admin.RevenueReportResponse;
import com.biteandbyte.restaurant.dto.order.OrderResponse;
import com.biteandbyte.restaurant.dto.order.OrderStatusRequest;
import com.biteandbyte.restaurant.dto.order.OrderSummaryResponse;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.service.AdminOrderService;
import com.biteandbyte.restaurant.service.DashboardService;
import com.biteandbyte.restaurant.service.RevenueReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Orders, dashboard and revenue for the admin. Dates are days in the store time zone (Asia/Kolkata). */
@Tag(name = "Admin: orders and reports")
@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

    static final Set<String> SORTABLE = Set.of("orderedAt", "grandTotal", "status");

    private final AdminOrderService adminOrderService;
    private final DashboardService dashboardService;
    private final RevenueReportService revenueReportService;

    public AdminOrderController(AdminOrderService adminOrderService, DashboardService dashboardService,
                                RevenueReportService revenueReportService) {
        this.adminOrderService = adminOrderService;
        this.dashboardService = dashboardService;
        this.revenueReportService = revenueReportService;
    }

    @Operation(summary = "All orders, newest first")
    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "First day ordered, inclusive", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day ordered, inclusive", example = "2026-11-14")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Order number, customer name or phone contains this")
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = Paging.DEFAULT_SIZE) int size,
            @Parameter(description = "orderedAt, grandTotal or status, then ,asc or ,desc", example = "orderedAt,desc")
            @RequestParam(required = false) String sort) {
        MenuController.checkQuery(q);
        return ApiResponse.ok(adminOrderService.list(new AdminOrderService.OrderFilter(status, from, to, q),
                Paging.of(page, size, sort, SORTABLE, OrderController.DEFAULT_SORT)));
    }

    @Operation(summary = "One order, with the customer and the statuses it can move to")
    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(adminOrderService.get(id));
    }

    @Operation(summary = "Move an order to its next status",
            description = "CONFIRMED -> PREPARING -> OUT_FOR_DELIVERY -> DELIVERED, or CANCELLED before delivery. "
                    + "Other moves answer 409 INVALID_STATUS_TRANSITION. Cancelling puts stock back. "
                    + "Delivering marks a cash-on-delivery payment as paid.")
    @PatchMapping("/orders/{id}/status")
    public ApiResponse<OrderResponse> changeStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        return ApiResponse.ok(adminOrderService.changeStatus(id, request), "Order status updated");
    }

    @Operation(summary = "Today's numbers, open orders and low stock")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.ok(dashboardService.dashboard());
    }

    @Operation(summary = "Revenue by day, week or month",
            description = "Revenue is the grand total of delivered orders, counted on the delivery day. "
                    + "The range is widened to whole weeks (Monday to Sunday) or months. "
                    + "Defaults: the last 30 days, 12 weeks or 12 months up to today.")
    @GetMapping("/reports/revenue")
    public ApiResponse<RevenueReportResponse> revenue(
            @Parameter(description = "daily, weekly or monthly", example = "daily")
            @RequestParam(defaultValue = "daily") String period,
            @Parameter(description = "First day, inclusive", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day, inclusive", example = "2026-11-14")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(revenueReportService.report(RevenueReportService.Period.parse(period), from, to));
    }
}
