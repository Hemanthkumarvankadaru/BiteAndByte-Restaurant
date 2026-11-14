package com.biteandbyte.restaurant.dto.auth;

import com.biteandbyte.restaurant.model.Gender;
import com.biteandbyte.restaurant.model.Role;
import com.biteandbyte.restaurant.model.User;

/** Public view of an account. Never includes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        String phone,
        String fullName,
        Integer age,
        Gender gender,
        Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getFullName(), user.getAge(), user.getGender(), user.getRole());
    }
}
