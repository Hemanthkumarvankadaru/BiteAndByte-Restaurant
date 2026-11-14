package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.model.MenuItem;

/**
 * Whether a quantity of a menu item can be ordered right now. Shared by the menu (to show
 * {@code orderable}), the cart and checkout, so all three agree.
 *
 * <p>An item is unavailable when it is deleted, switched off by the admin, or its category is
 * inactive. Zero stock makes it unorderable without touching the admin's {@code available}
 * switch, so restocking brings it back automatically.
 */
public final class StockCheck {

    public enum Problem {
        /** Deleted, switched off, or in an inactive category. */
        UNAVAILABLE,
        OUT_OF_STOCK,
        /** In stock, but fewer than requested. */
        INSUFFICIENT_STOCK
    }

    /** The reason this quantity cannot be ordered, or {@code null} when it can. Needs the category loaded. */
    public static Problem problem(MenuItem item, int quantity) {
        if (item.isDeleted() || !item.isAvailable() || !item.getCategory().isActive()) {
            return Problem.UNAVAILABLE;
        }
        if (item.getStockQuantity() <= 0) {
            return Problem.OUT_OF_STOCK;
        }
        if (quantity > item.getStockQuantity()) {
            return Problem.INSUFFICIENT_STOCK;
        }
        return null;
    }

    public static boolean orderable(MenuItem item) {
        return problem(item, 1) == null;
    }

    /** Throws 409 when this quantity cannot be ordered. */
    public static void require(MenuItem item, int quantity) {
        Problem problem = problem(item, quantity);
        if (problem != null) {
            throw ApiException.conflict(problem == Problem.UNAVAILABLE ? "ITEM_UNAVAILABLE" : problem.name(),
                    message(problem, item));
        }
    }

    public static String message(Problem problem, MenuItem item) {
        return switch (problem) {
            case UNAVAILABLE -> item.getName() + " is not available right now";
            case OUT_OF_STOCK -> item.getName() + " is out of stock";
            case INSUFFICIENT_STOCK -> "Only " + item.getStockQuantity() + " left of " + item.getName();
        };
    }

    private StockCheck() {
    }
}
