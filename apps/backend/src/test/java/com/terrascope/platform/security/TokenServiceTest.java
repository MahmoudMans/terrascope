package com.terrascope.platform.security;

import com.terrascope.platform.security.TokenService.IssuedToken;
import com.terrascope.platform.security.TokenService.TokenClaims;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123";

    private final MutableClock clock =
            new MutableClock();

    private final TokenService tokens =
            new TokenService(
                    SECRET,
                    Duration.ofHours(8),
                    clock
            );

    private final UUID userId =
            UUID.fromString("00000000-0000-4000-8000-000000000042");

    @Test
    void roundTripsTheClaims() {
        IssuedToken issued =
                tokens.issue(userId, 3);

        Optional<TokenClaims> claims =
                tokens.verify(issued.token());

        assertTrue(claims.isPresent());
        assertEquals(userId, claims.get().userId());
        assertEquals(3, claims.get().tokenVersion());
        assertEquals(issued.expiresAt(), claims.get().expiresAt());

        assertEquals(
                Instant.parse("2026-01-01T08:00:00Z"),
                issued.expiresAt()
        );
    }

    @Test
    void expiresAtTheEndOfTheLifetime() {
        String token =
                tokens.issue(userId, 0).token();

        clock.advance(Duration.ofHours(8).minusSeconds(1));

        assertTrue(tokens.verify(token).isPresent());

        clock.advance(Duration.ofSeconds(1));

        assertFalse(tokens.verify(token).isPresent());
    }

    @Test
    void rejectsATamperedPayload() {
        String token =
                tokens.issue(userId, 0).token();

        String[] parts =
                token.split("\\.");

        String other =
                tokens.issue(UUID.randomUUID(), 0).token().split("\\.")[1];

        assertFalse(
                tokens
                        .verify(parts[0] + "." + other + "." + parts[2])
                        .isPresent()
        );
    }

    @Test
    void rejectsATamperedSignature() {
        String token =
                tokens.issue(userId, 0).token();

        String flipped =
                token.substring(0, token.length() - 2)
                        + (token.endsWith("AA") ? "BB" : "AA");

        assertFalse(tokens.verify(flipped).isPresent());
    }

    @Test
    void rejectsTokensSignedWithAnotherSecret() {
        TokenService other =
                new TokenService(
                        "ffffffffffffffffffffffffffffffffffff",
                        Duration.ofHours(8),
                        clock
                );

        assertFalse(
                tokens
                        .verify(other.issue(userId, 0).token())
                        .isPresent()
        );
    }

    @Test
    void rejectsGarbage() {
        assertFalse(tokens.verify(null).isPresent());
        assertFalse(tokens.verify("").isPresent());
        assertFalse(tokens.verify("abc").isPresent());
        assertFalse(tokens.verify("v1.a.b").isPresent());
        assertFalse(tokens.verify("v2.a.b").isPresent());
        assertFalse(tokens.verify("v1..").isPresent());
        assertFalse(tokens.verify("v1.a.b.c").isPresent());
        assertFalse(tokens.verify("x".repeat(600)).isPresent());
    }

    @Test
    void issuedTokensStayWithinTheSizeLimit() {
        assertTrue(
                tokens.issue(userId, Integer.MAX_VALUE).token().length() < 512
        );
    }

    private static final class MutableClock extends Clock {

        private Instant now =
                Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
