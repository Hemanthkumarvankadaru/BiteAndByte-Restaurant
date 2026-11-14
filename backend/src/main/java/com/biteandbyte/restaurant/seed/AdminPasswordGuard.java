package com.biteandbyte.restaurant.seed;

import com.biteandbyte.restaurant.config.AdminProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Stops the app from starting with the well-known {@code admin/admin} credentials anywhere
 * except the local {@code dev} profile. Set {@code BITEANDBYTE_ADMIN_PASSWORD} to fix the error.
 */
@Component
public class AdminPasswordGuard {

    private final AdminProperties admin;
    private final Environment environment;

    public AdminPasswordGuard(AdminProperties admin, Environment environment) {
        this.admin = admin;
        this.environment = environment;
    }

    /** Throws when the default password is used outside the dev profile. */
    public void check() {
        boolean devProfile = environment.acceptsProfiles(Profiles.of("dev"));
        if (admin.usesDefaultPassword() && !devProfile) {
            throw new IllegalStateException(
                    "The admin password is still the default 'admin'. Set BITEANDBYTE_ADMIN_PASSWORD before running "
                            + "with profiles " + String.join(",", environment.getActiveProfiles()) + ".");
        }
    }
}
