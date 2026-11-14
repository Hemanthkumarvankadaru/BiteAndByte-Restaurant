package com.biteandbyte.restaurant.dto.menu;

/** Validation rules shared by the menu and category requests (plan, "Validation rules"). */
public final class MenuDtos {

    public static final String IMAGE_URL_PATTERN = "^https?://\\S+$";
    public static final String IMAGE_URL_MESSAGE = "must be an http or https URL";

    public static final String PRICE_MESSAGE = "must be greater than 0";
    public static final String PRICE_DIGITS_MESSAGE = "must have at most 2 decimal places";

    public static final long MAX_STOCK = 100_000;

    private MenuDtos() {
    }
}
