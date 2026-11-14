package com.biteandbyte.restaurant.repository;

import com.biteandbyte.restaurant.model.OrderStatus;

/** Query result: number of orders in a status. */
public record StatusCount(OrderStatus status, Long count) {
}
