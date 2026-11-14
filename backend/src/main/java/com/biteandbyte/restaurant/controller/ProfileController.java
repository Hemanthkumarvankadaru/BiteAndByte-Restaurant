package com.biteandbyte.restaurant.controller;

import com.biteandbyte.restaurant.dto.ApiResponse;
import com.biteandbyte.restaurant.dto.auth.ChangePasswordRequest;
import com.biteandbyte.restaurant.dto.auth.UpdateProfileRequest;
import com.biteandbyte.restaurant.dto.auth.UserResponse;
import com.biteandbyte.restaurant.security.CurrentUser;
import com.biteandbyte.restaurant.service.AuthService;
import com.biteandbyte.restaurant.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The customer's own profile. Password change is open to any logged-in user, including the admin. */
@Tag(name = "Profile")
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final AuthService authService;
    private final ProfileService profileService;
    private final CurrentUser currentUser;

    public ProfileController(AuthService authService, ProfileService profileService, CurrentUser currentUser) {
        this.authService = authService;
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Your profile (customers)")
    @GetMapping
    public ApiResponse<UserResponse> get() {
        return ApiResponse.ok(profileService.get());
    }

    @Operation(summary = "Update your profile (customers). The username cannot be changed.")
    @PutMapping
    public ApiResponse<UserResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(profileService.update(request), "Profile updated");
    }

    @Operation(summary = "Change your password. Signs you out on other devices.")
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser.id(), request);
        return ApiResponse.message("Password changed. Please log in again on your other devices.");
    }
}
