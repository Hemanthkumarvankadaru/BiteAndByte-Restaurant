package com.biteandbyte.restaurant.repository;

/** Query result: number of live menu items in a category. */
public record CategoryItemCount(Long categoryId, Long count) {
}
