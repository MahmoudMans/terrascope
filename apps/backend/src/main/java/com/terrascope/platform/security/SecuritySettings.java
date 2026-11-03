package com.terrascope.platform.security;

import java.time.Duration;

public final class SecuritySettings {

    public static final int MIN_SECRET_LENGTH = 32;

    private static final Duration DEFAULT_TOKEN_TTL =
            Duration.ofHours(8);

    private static final Duration MIN_TOKEN_TTL =
            Duration.ofMinutes(5);

    private static final Duration MAX_TOKEN_TTL =
            Duration.ofDays(30);

    private final boolean enabled;
    private final String tokenSecret;
    private final Duration tokenTtl;
    private final String adminUsername;
    private final String adminPassword;

    public SecuritySettings(
            boolean enabled,
            String tokenSecret,
            String tokenTtl,
            String adminUsername,
            String adminPassword
    ) {
        this.enabled = enabled;
        this.tokenSecret = valueOf(tokenSecret);
        this.tokenTtl = parseTtl(tokenTtl);
        this.adminUsername = valueOf(adminUsername);
        this.adminPassword = valueOf(adminPassword);

        if (
                enabled
                && this.tokenSecret.length() < MIN_SECRET_LENGTH
        ) {
            throw new IllegalStateException(
                    "Security is enabled but terrascope.security.token-secret "
                            + "(TERRASCOPE_SECURITY_TOKEN_SECRET) is missing or shorter than "
                            + MIN_SECRET_LENGTH
                            + " characters."
            );
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String tokenSecret() {
        return tokenSecret;
    }

    public Duration tokenTtl() {
        return tokenTtl;
    }

    public String adminUsername() {
        return adminUsername;
    }

    public String adminPassword() {
        return adminPassword;
    }

    private static String valueOf(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private static Duration parseTtl(
            String value
    ) {
        try {
            Duration parsed =
                    Duration.parse(
                            valueOf(value)
                    );

            if (parsed.compareTo(MIN_TOKEN_TTL) < 0) {
                return MIN_TOKEN_TTL;
            }

            if (parsed.compareTo(MAX_TOKEN_TTL) > 0) {
                return MAX_TOKEN_TTL;
            }

            return parsed;

        } catch (RuntimeException exception) {
            return DEFAULT_TOKEN_TTL;
        }
    }
}
