package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.dto.auth.UpdateProfileRequest;
import com.biteandbyte.restaurant.dto.auth.UserResponse;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.exception.ResourceNotFoundException;
import com.biteandbyte.restaurant.model.User;
import com.biteandbyte.restaurant.repository.UserRepository;
import com.biteandbyte.restaurant.security.CurrentUser;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The logged-in customer's own profile. Always acts on {@link CurrentUser}, never on an id from the request. */
@Service
public class ProfileService {

    private final UserRepository users;
    private final CurrentUser currentUser;

    public ProfileService(UserRepository users, CurrentUser currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public UserResponse get() {
        return UserResponse.from(me());
    }

    @Transactional
    public UserResponse update(UpdateProfileRequest request) {
        User user = me();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (users.existsByPhoneAndIdNot(request.phone(), user.getId())) {
            throw ApiException.conflict("PHONE_TAKEN", "An account with this phone number already exists");
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setAge(request.age());
        user.setGender(request.gender());
        return UserResponse.from(users.saveAndFlush(user));
    }

    private User me() {
        Long id = currentUser.id();
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
