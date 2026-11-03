package com.terrascope.platform.security;

import com.terrascope.platform.api.model.LoginResponse;
import com.terrascope.platform.api.model.UserSummary;
import com.terrascope.platform.security.TokenService.IssuedToken;
import com.terrascope.platform.security.TokenService.TokenClaims;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public class AuthService {

    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_LOGIN_PASSWORD_LENGTH = 256;

    private final SecuritySettings settings;
    private final UserStore store;
    private final UserDirectory directory;
    private final PasswordHasher hasher;
    private final TokenService tokens;
    private final LoginThrottle userThrottle;
    private final LoginThrottle clientThrottle;

    public AuthService(
            SecuritySettings settings,
            UserStore store,
            UserDirectory directory,
            PasswordHasher hasher,
            TokenService tokens
    ) {
        this(
                settings,
                store,
                directory,
                hasher,
                tokens,
                new LoginThrottle(
                        5,
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(15)
                ),
                new LoginThrottle(
                        60,
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(15)
                )
        );
    }

    public AuthService(
            SecuritySettings settings,
            UserStore store,
            UserDirectory directory,
            PasswordHasher hasher,
            TokenService tokens,
            LoginThrottle userThrottle,
            LoginThrottle clientThrottle
    ) {
        this.settings = settings;
        this.store = store;
        this.directory = directory;
        this.hasher = hasher;
        this.tokens = tokens;
        this.userThrottle = userThrottle;
        this.clientThrottle = clientThrottle;
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public void requireEnabled() {
        if (!settings.enabled()) {
            throw new AuthException(
                    HttpStatus.NOT_FOUND,
                    "SECURITY_DISABLED",
                    "Sign-in is not enabled on this server."
            );
        }
    }

    public LoginResponse login(
            String username,
            String password,
            String clientAddress
    ) {
        requireEnabled();

        String userKey =
                "user:" + keyOf(username);

        String clientKey =
                "client:" + keyOf(clientAddress);

        long lockedSeconds =
                Math.max(
                        userThrottle.secondsLocked(userKey),
                        clientThrottle.secondsLocked(clientKey)
                );

        if (lockedSeconds > 0) {
            throw tooManyAttempts(lockedSeconds);
        }

        Optional<UserAccount> account =
                lookup(username);

        String attempt =
                password == null
                        ? ""
                        : password;

        boolean passwordMatches;

        if (
                attempt.isEmpty()
                || attempt.length() > MAX_LOGIN_PASSWORD_LENGTH
        ) {
            passwordMatches = false;

        } else if (account.isPresent()) {
            passwordMatches =
                    hasher.verify(
                            attempt,
                            account.get().passwordHash()
                    );

        } else {
            hasher.verify(
                    attempt,
                    hasher.dummyHash()
            );

            passwordMatches = false;
        }

        if (
                !passwordMatches
                || account.isEmpty()
                || !account.get().enabled()
        ) {
            userThrottle.recordFailure(userKey);
            clientThrottle.recordFailure(clientKey);

            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Incorrect username or password."
            );
        }

        userThrottle.recordSuccess(userKey);

        store.recordLogin(
                account.get().id(),
                Instant.now()
        );

        return respond(account.get());
    }

    public Optional<AuthenticatedUser> authenticate(
            String token
    ) {
        if (!settings.enabled()) {
            return Optional.empty();
        }

        Optional<TokenClaims> claims =
                tokens.verify(token);

        if (claims.isEmpty()) {
            return Optional.empty();
        }

        Optional<UserAccount> account =
                directory.find(
                        claims.get().userId()
                );

        if (
                account.isEmpty()
                || !account.get().enabled()
                || account.get().tokenVersion()
                != claims.get().tokenVersion()
        ) {
            return Optional.empty();
        }

        UserAccount user =
                account.get();

        return Optional.of(
                new AuthenticatedUser(
                        user.id(),
                        user.username(),
                        user.displayName(),
                        user.role()
                )
        );
    }

    public UserSummary currentUser(
            AuthenticatedUser user
    ) {
        requireEnabled();

        return store
                .findById(user.id())
                .filter(UserAccount::enabled)
                .map(UserSummary::from)
                .orElseThrow(AuthService::signInRequired);
    }

    public LoginResponse changePassword(
            AuthenticatedUser user,
            String currentPassword,
            String newPassword
    ) {
        requireEnabled();

        String key =
                "password:" + user.id();

        long lockedSeconds =
                userThrottle.secondsLocked(key);

        if (lockedSeconds > 0) {
            throw tooManyAttempts(lockedSeconds);
        }

        UserAccount account =
                store
                        .findById(user.id())
                        .filter(UserAccount::enabled)
                        .orElseThrow(AuthService::signInRequired);

        if (
                !hasher.verify(
                        currentPassword,
                        account.passwordHash()
                )
        ) {
            userThrottle.recordFailure(key);

            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "WRONG_PASSWORD",
                    "The current password is incorrect."
            );
        }

        userThrottle.recordSuccess(key);

        Credentials.requireStrongPassword(
                newPassword,
                account.username()
        );

        if (hasher.verify(newPassword, account.passwordHash())) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "WEAK_PASSWORD",
                    "Choose a password that differs from the current one."
            );
        }

        UserAccount updated =
                store
                        .updatePassword(
                                account.id(),
                                hasher.hash(newPassword)
                        )
                        .orElseThrow(AuthService::signInRequired);

        directory.invalidate();

        return respond(updated);
    }

    private LoginResponse respond(
            UserAccount account
    ) {
        IssuedToken issued =
                tokens.issue(
                        account.id(),
                        account.tokenVersion()
                );

        return new LoginResponse(
                issued.token(),
                issued.expiresAt(),
                UserSummary.from(account)
        );
    }

    private Optional<UserAccount> lookup(
            String username
    ) {
        try {
            return store.findByUsername(
                    Credentials.normalizeUsername(username)
            );

        } catch (AuthException exception) {
            return Optional.empty();
        }
    }

    private static String keyOf(
            String value
    ) {
        String normalized =
                value == null
                        ? ""
                        : value
                                .trim()
                                .toLowerCase(Locale.ROOT);

        return normalized.length() > MAX_KEY_LENGTH
                ? normalized.substring(0, MAX_KEY_LENGTH)
                : normalized;
    }

    private static AuthException tooManyAttempts(
            long seconds
    ) {
        long minutes =
                Math.max(1, (seconds + 59) / 60);

        return new AuthException(
                HttpStatus.TOO_MANY_REQUESTS,
                "TOO_MANY_ATTEMPTS",
                "Too many failed attempts. Try again in "
                        + minutes
                        + (minutes == 1 ? " minute." : " minutes.")
        );
    }

    private static AuthException signInRequired() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Sign in to continue."
        );
    }
}
