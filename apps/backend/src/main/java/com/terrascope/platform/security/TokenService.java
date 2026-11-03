package com.terrascope.platform.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public final class TokenService {

    private static final String VERSION = "v1";
    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_TOKEN_LENGTH = 512;

    private final byte[] key;
    private final Duration ttl;
    private final Clock clock;

    public TokenService(
            String secret,
            Duration ttl
    ) {
        this(
                secret,
                ttl,
                Clock.systemUTC()
        );
    }

    TokenService(
            String secret,
            Duration ttl,
            Clock clock
    ) {
        this.key =
                secret.getBytes(
                        StandardCharsets.UTF_8
                );

        this.ttl = ttl;
        this.clock = clock;
    }

    public IssuedToken issue(
            UUID userId,
            int tokenVersion
    ) {
        Instant issuedAt =
                clock
                        .instant()
                        .truncatedTo(ChronoUnit.SECONDS);

        Instant expiresAt =
                issuedAt.plus(ttl);

        String payload =
                userId
                        + ":"
                        + tokenVersion
                        + ":"
                        + issuedAt.getEpochSecond()
                        + ":"
                        + expiresAt.getEpochSecond();

        String signedPart =
                VERSION
                        + "."
                        + encode(
                                payload.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );

        return new IssuedToken(
                signedPart
                        + "."
                        + encode(sign(signedPart)),
                expiresAt
        );
    }

    public Optional<TokenClaims> verify(
            String token
    ) {
        if (
                token == null
                || token.isEmpty()
                || token.length() > MAX_TOKEN_LENGTH
        ) {
            return Optional.empty();
        }

        String[] parts =
                token.split(
                        "\\.",
                        -1
                );

        if (
                parts.length != 3
                || !VERSION.equals(parts[0])
        ) {
            return Optional.empty();
        }

        try {
            String signedPart =
                    parts[0] + "." + parts[1];

            byte[] provided =
                    Base64
                            .getUrlDecoder()
                            .decode(parts[2]);

            if (
                    !MessageDigest.isEqual(
                            sign(signedPart),
                            provided
                    )
            ) {
                return Optional.empty();
            }

            String payload =
                    new String(
                            Base64
                                    .getUrlDecoder()
                                    .decode(parts[1]),
                            StandardCharsets.UTF_8
                    );

            String[] fields =
                    payload.split(
                            ":",
                            -1
                    );

            if (fields.length != 4) {
                return Optional.empty();
            }

            TokenClaims claims =
                    new TokenClaims(
                            UUID.fromString(fields[0]),
                            Integer.parseInt(fields[1]),
                            Instant.ofEpochSecond(
                                    Long.parseLong(fields[2])
                            ),
                            Instant.ofEpochSecond(
                                    Long.parseLong(fields[3])
                            )
                    );

            if (!clock.instant().isBefore(claims.expiresAt())) {
                return Optional.empty();
            }

            return Optional.of(claims);

        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private byte[] sign(
            String value
    ) {
        try {
            Mac mac =
                    Mac.getInstance(MAC_ALGORITHM);

            mac.init(
                    new SecretKeySpec(
                            key,
                            MAC_ALGORITHM
                    )
            );

            return mac.doFinal(
                    value.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "HMAC-SHA256 is not available on this JVM.",
                    exception
            );
        }
    }

    private static String encode(
            byte[] value
    ) {
        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }

    public record IssuedToken(
            String token,
            Instant expiresAt
    ) {
    }

    public record TokenClaims(
            UUID userId,
            int tokenVersion,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
