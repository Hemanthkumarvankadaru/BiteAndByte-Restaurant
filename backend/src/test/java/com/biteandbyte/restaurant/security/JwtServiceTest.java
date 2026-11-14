package com.biteandbyte.restaurant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biteandbyte.restaurant.config.JwtProperties;
import com.biteandbyte.restaurant.model.Role;
import com.biteandbyte.restaurant.model.User;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

    private static final JwtProperties PROPS = new JwtProperties(
            "test-secret-that-is-at-least-32-characters-long", "biteandbyte-restaurant",
            Duration.ofMinutes(15), Duration.ofDays(7));

    private final JwtConfig config = new JwtConfig();

    private JwtService serviceAt(Instant now) {
        return new JwtService(config.jwtEncoder(config.jwtSigningKey(PROPS)), PROPS, Clock.fixed(now, ZoneOffset.UTC));
    }

    private JwtDecoder decoder(JwtProperties props) {
        return config.jwtDecoder(config.jwtSigningKey(props), props);
    }

    private static User user(long id, Role role) throws Exception {
        User user = new User();
        user.setUsername("ravi");
        user.setRole(role);
        Field idField = com.biteandbyte.restaurant.model.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
        return user;
    }

    @Test
    void issuedTokenCarriesUserIdUsernameAndRole() throws Exception {
        JwtService.AccessToken token = serviceAt(Instant.now()).issueAccessToken(user(42, Role.CUSTOMER));

        Jwt jwt = decoder(PROPS).decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("username")).isEqualTo("ravi");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("biteandbyte-restaurant");
        assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        JwtService.AccessToken token = serviceAt(Instant.now()).issueAccessToken(user(1, Role.ADMIN));
        JwtProperties otherSecret = new JwtProperties("a-completely-different-secret-of-32-chars", "biteandbyte-restaurant",
                Duration.ofMinutes(15), Duration.ofDays(7));

        assertThatThrownBy(() -> decoder(otherSecret).decode(token.value())).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        JwtService.AccessToken token = serviceAt(Instant.now().minus(Duration.ofHours(1)))
                .issueAccessToken(user(1, Role.CUSTOMER));

        assertThatThrownBy(() -> decoder(PROPS).decode(token.value())).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        JwtService.AccessToken token = serviceAt(Instant.now()).issueAccessToken(user(1, Role.CUSTOMER));
        JwtProperties otherIssuer = new JwtProperties(PROPS.secret(), "someone-else", PROPS.accessTokenTtl(),
                PROPS.refreshTokenTtl());

        assertThatThrownBy(() -> decoder(otherIssuer).decode(token.value())).isInstanceOf(JwtException.class);
    }
}
