package com.terrascope.platform.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

final class SecurityFixture {

    static final String SECRET =
            "0123456789abcdef0123456789abcdef0123";

    final SecuritySettings settings;
    final InMemoryUserStore store = new InMemoryUserStore();
    final PasswordHasher hasher = new PasswordHasher(1000);
    final UserDirectory directory = new UserDirectory(store);
    final TokenService tokens;
    final AuthService auth;
    final UserAdminService admins;

    SecurityFixture(
            boolean enabled
    ) {
        this(
                enabled,
                "",
                ""
        );
    }

    SecurityFixture(
            boolean enabled,
            String adminUsername,
            String adminPassword
    ) {
        this.settings =
                new SecuritySettings(
                        enabled,
                        SECRET,
                        "PT8H",
                        adminUsername,
                        adminPassword
                );

        this.tokens =
                new TokenService(
                        SECRET,
                        Duration.ofHours(8)
                );

        this.auth =
                new AuthService(
                        settings,
                        store,
                        directory,
                        hasher,
                        tokens
                );

        this.admins =
                new UserAdminService(
                        settings,
                        store,
                        directory,
                        hasher
                );
    }

    UserAccount addUser(
            String username,
            Role role,
            String password
    ) {
        UserAccount account =
                new UserAccount(
                        UUID.randomUUID(),
                        username,
                        username,
                        role,
                        hasher.hash(password),
                        true,
                        0,
                        Instant.now(),
                        null
                );

        store.accounts.put(
                account.id(),
                account
        );

        return account;
    }

    AuthenticatedUser actorOf(
            UserAccount account
    ) {
        return new AuthenticatedUser(
                account.id(),
                account.username(),
                account.displayName(),
                account.role()
        );
    }
}
