package com.biteandbyte.restaurant.dto.auth;

/**
 * Validation patterns shared by the auth and profile request records. Kept in one place so
 * the frontend and backend rules stay easy to compare.
 */
public final class AuthDtos {

    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_.]{3,50}$";
    public static final String USERNAME_MESSAGE = "must be 3 to 50 letters, digits, dots or underscores";

    public static final String PHONE_PATTERN = "^[6-9]\\d{9}$";
    public static final String PHONE_MESSAGE = "must be a valid 10-digit Indian mobile number";

    public static final String PINCODE_PATTERN = "^\\d{6}$";
    public static final String PINCODE_MESSAGE = "must be a 6-digit pincode";

    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String PASSWORD_MESSAGE = "must be 8 to 64 characters with at least one letter and one digit";

    private AuthDtos() {
    }
}
