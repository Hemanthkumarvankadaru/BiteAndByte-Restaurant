package com.biteandbyte.restaurant.security;

import com.biteandbyte.restaurant.config.JwtProperties;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.model.RefreshToken;
import com.biteandbyte.restaurant.model.User;
import com.biteandbyte.restaurant.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Long-lived refresh tokens. The client gets a random opaque string; the database keeps only
 * its SHA-256 hash. Each refresh rotates the token. Using a revoked token again revokes every
 * token of that user, because it means an old token was copied.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    /** A new refresh token. {@code value} is shown to the client once and never stored. */
    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    private final RefreshTokenRepository tokens;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, JwtProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken create(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(value));
        token.setExpiresAt(clock.instant().plus(properties.refreshTokenTtl()));
        tokens.save(token);
        return new IssuedRefreshToken(value, token.getExpiresAt());
    }

    /**
     * Uses up a refresh token and returns its owner. The caller then issues a new pair.
     * The reuse check commits its revocations even though it ends in an exception.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public User consume(String value) {
        Instant now = clock.instant();
        RefreshToken token = tokens.findByTokenHash(hash(value)).orElseThrow(RefreshTokenService::invalid);
        if (token.getRevokedAt() != null) {
            log.warn("Revoked refresh token reused for user {}. Revoking all of that user's tokens.",
                    token.getUser().getId());
            tokens.revokeAllForUser(token.getUser().getId(), now);
            throw invalid();
        }
        if (!token.isUsable(now)) {
            throw invalid();
        }
        token.setRevokedAt(now);
        return token.getUser();
    }

    /** Revokes one token. Unknown or already revoked tokens are ignored, so logout is idempotent. */
    @Transactional
    public void revoke(String value) {
        tokens.findByTokenHash(hash(value))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(clock.instant()));
    }

    @Transactional
    public void revokeAll(Long userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    public long refreshTokenTtlSeconds() {
        return properties.refreshTokenTtl().toSeconds();
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Your session has expired. Please log in again.");
    }
}
