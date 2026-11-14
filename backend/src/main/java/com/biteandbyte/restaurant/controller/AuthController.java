package com.biteandbyte.restaurant.controller;

import com.biteandbyte.restaurant.dto.ApiResponse;
import com.biteandbyte.restaurant.dto.auth.AuthResponse;
import com.biteandbyte.restaurant.dto.auth.LoginRequest;
import com.biteandbyte.restaurant.dto.auth.RefreshRequest;
import com.biteandbyte.restaurant.dto.auth.RegisterRequest;
import com.biteandbyte.restaurant.dto.auth.UserResponse;
import com.biteandbyte.restaurant.security.CurrentUser;
import com.biteandbyte.restaurant.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Register, log in, refresh tokens and log out")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Create a customer account and log in")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.register(request), "Account created"));
    }

    @Operation(summary = "Log in with username or email")
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "Swap a refresh token for a new token pair. The old refresh token stops working.")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Revoke a refresh token. Works even after the access token has expired.")
    @SecurityRequirements
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.message("Logged out");
    }

    @Operation(summary = "The logged-in user")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(authService.me(currentUser.id()));
    }
}
