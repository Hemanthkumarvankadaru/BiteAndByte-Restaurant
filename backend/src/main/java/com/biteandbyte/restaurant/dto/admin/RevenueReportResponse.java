package com.biteandbyte.restaurant.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Revenue grouped by day, week (Monday to Sunday) or month, in the store time zone. Revenue is
 * the grand total of DELIVERED orders, counted on the day they were delivered. Online payments
 * must have succeeded; cash on delivery counts once delivered.
 *
 * <p>{@code from} and {@code to} are widened to whole weeks or months, so every bucket is complete.
 * Buckets with no sales are included with zeros, so a chart has no gaps.
 */
public record RevenueReportResponse(
        @Schema(example = "daily") String period,
        @Schema(example = "2026-10-16") LocalDate from,
        @Schema(example = "2026-11-14") LocalDate to,
        @Schema(example = "Asia/Kolkata") String timeZone,
        @Schema(example = "214") long totalOrders,
        @Schema(example = "96512.40") BigDecimal totalRevenue,
        List<Bucket> buckets) {

    public record Bucket(
            @Schema(example = "2026-11-14") LocalDate start,
            @Schema(example = "2026-11-14") LocalDate end,
            @Schema(example = "2026-11-14") String label,
            @Schema(example = "12") long orders,
            @Schema(example = "8420.50") BigDecimal revenue) {
    }
}
