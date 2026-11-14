package com.biteandbyte.restaurant.seed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biteandbyte.restaurant.config.AdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AdminPasswordGuardTest {

    private static AdminProperties admin(String password) {
        return new AdminProperties("admin", password, "admin@example.com", "9000000000", "Admin");
    }

    private static MockEnvironment env(String... activeProfiles) {
        MockEnvironment env = new MockEnvironment();
        env.setDefaultProfiles("dev");
        env.setActiveProfiles(activeProfiles);
        return env;
    }

    @Test
    void allowsDefaultPasswordOnDevProfile() {
        assertThatCode(() -> new AdminPasswordGuard(admin("admin"), env("dev")).check()).doesNotThrowAnyException();
    }

    @Test
    void allowsDefaultPasswordWhenNoProfileIsActiveBecauseDevIsTheDefault() {
        assertThatCode(() -> new AdminPasswordGuard(admin("admin"), env()).check()).doesNotThrowAnyException();
    }

    @Test
    void rejectsDefaultPasswordOnOtherProfiles() {
        assertThatThrownBy(() -> new AdminPasswordGuard(admin("admin"), env("oracle")).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BITEANDBYTE_ADMIN_PASSWORD");
    }

    @Test
    void allowsCustomPasswordOnAnyProfile() {
        assertThatCode(() -> new AdminPasswordGuard(admin("S3cure-pass!"), env("postgres")).check())
                .doesNotThrowAnyException();
    }
}
