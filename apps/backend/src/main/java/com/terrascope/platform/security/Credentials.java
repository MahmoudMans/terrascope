package com.terrascope.platform.security;

import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Credentials {

    public static final int MIN_PASSWORD_LENGTH = 12;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private static final Pattern USERNAME =
            Pattern.compile(
                    "^[a-z0-9][a-z0-9._-]{2,63}$"
            );

    private static final int MAX_DISPLAY_NAME_LENGTH = 120;

    private Credentials() {
    }

    public static String normalizeUsername(
            String value
    ) {
        String normalized =
                value == null
                        ? ""
                        : value
                                .trim()
                                .toLowerCase(Locale.ROOT);

        if (!USERNAME.matcher(normalized).matches()) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_USERNAME",
                    "Usernames are 3 to 64 characters: lowercase letters, digits, dots, dashes or underscores."
            );
        }

        return normalized;
    }

    public static String displayNameOrDefault(
            String value,
            String fallback
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            return fallback;
        }

        String trimmed =
                value.trim();

        if (
                trimmed.length() > MAX_DISPLAY_NAME_LENGTH
                || trimmed.chars().anyMatch(Character::isISOControl)
        ) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DISPLAY_NAME",
                    "Display names are up to 120 characters with no control characters."
            );
        }

        return trimmed;
    }

    public static String requireStrongPassword(
            String password,
            String username
    ) {
        if (
                password == null
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH
        ) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "WEAK_PASSWORD",
                    "Passwords must be between "
                            + MIN_PASSWORD_LENGTH
                            + " and "
                            + MAX_PASSWORD_LENGTH
                            + " characters."
            );
        }

        if (
                username != null
                && password.equalsIgnoreCase(username)
        ) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "WEAK_PASSWORD",
                    "The password must differ from the username."
            );
        }

        return password;
    }
}
