package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.dto.auth.AuthResponse;
import com.biteandbyte.restaurant.dto.auth.ChangePasswordRequest;
import com.biteandbyte.restaurant.dto.auth.LoginRequest;
import com.biteandbyte.restaurant.dto.auth.RegisterRequest;
import com.biteandbyte.restaurant.dto.auth.UserResponse;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.exception.ResourceNotFoundException;
import com.biteandbyte.restaurant.model.Address;
import com.biteandbyte.restaurant.model.Role;
import com.biteandbyte.restaurant.model.User;
import com.biteandbyte.restaurant.repository.AddressRepository;
import com.biteandbyte.restaurant.repository.UserRepository;
import com.biteandbyte.restaurant.security.JwtService;
import com.biteandbyte.restaurant.security.RefreshTokenService;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login, token refresh, logout and password change. */
@Service
public class AuthService {

    private final UserRepository users;
    private final AddressRepository addresses;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    /**
     * Hash checked when the login name is unknown, so a wrong username takes as long as a
     * wrong password and response time does not reveal which accounts exist.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, AddressRepository addresses, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokens) {
        this.users = users;
        this.addresses = addresses;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalise(request.username());
        String email = normalise(request.email());
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "This username is already taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (users.existsByPhone(request.phone())) {
            throw ApiException.conflict("PHONE_TAKEN", "An account with this phone number already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setAge(request.age());
        user.setGender(request.gender());
        user.setRole(Role.CUSTOMER);
        users.save(user);

        RegisterRequest.AddressRequest a = request.address();
        Address address = new Address();
        address.setUser(user);
        address.setLabel(a.label() == null || a.label().isBlank() ? "Home" : a.label().trim());
        address.setStreet(a.street().trim());
        address.setCity(a.city().trim());
        address.setState(a.state().trim());
        address.setPincode(a.pincode());
        address.setLandmark(a.landmark() == null ? null : a.landmark().trim());
        address.setDefaultAddress(true);
        addresses.save(address);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String login = normalise(request.usernameOrEmail());
        Optional<User> found = login.contains("@")
                ? users.findByEmailIgnoreCase(login)
                : users.findByUsernameIgnoreCase(login);

        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }
        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        requireActive(user);
        return issueTokens(user);
    }

    /** Rotates the refresh token. Revocations from reuse detection must commit, so no rollback on ApiException. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokens.consume(refreshToken);
        requireActive(user);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    /** Changes the password and signs the user out everywhere else by revoking refresh tokens. */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw ApiException.badRequest("SAME_PASSWORD", "New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAll(userId);
    }

    private AuthResponse issueTokens(User user) {
        JwtService.AccessToken access = jwtService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.create(user);
        return new AuthResponse(access.value(), refresh.value(), AuthResponse.BEARER,
                jwtService.accessTokenTtlSeconds(), refreshTokens.refreshTokenTtlSeconds(), UserResponse.from(user));
    }

    private User findUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static void requireActive(User user) {
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled");
        }
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Username or password is incorrect");
    }

    private static String normalise(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
