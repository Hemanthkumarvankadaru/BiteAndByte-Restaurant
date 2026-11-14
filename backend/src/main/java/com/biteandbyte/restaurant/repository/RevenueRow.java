package com.biteandbyte.restaurant.repository;

import java.math.BigDecimal;
import java.time.Instant;

/** Query result: one order that counts as revenue, with when it was delivered and its total. */
public record RevenueRow(Instant deliveredAt, BigDecimal grandTotal) {
}
