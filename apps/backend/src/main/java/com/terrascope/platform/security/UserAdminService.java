package com.terrascope.platform.security;

import com.terrascope.platform.api.model.UserSummary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class UserAdminService {

    private final SecuritySettings settings;
    private final UserStore store;
    private final UserDirectory directory;
    private final PasswordHasher hasher;

    public UserAdminService(
            SecuritySettings settings,
            UserStore store,
            UserDirectory directory,
            PasswordHasher hasher
    ) {
        this.settings = settings;
        this.store = store;
        this.directory = directory;
        this.hasher = hasher;
    }

    public List<UserSummary> list() {
        requireEnabled();

        return store
                .findAll()
                .stream()
                .map(UserSummary::from)
                .toList();
    }

    @Transactional
    public UserSummary create(
            String username,
            String displayName,
            String role,
            String password
    ) {
        requireEnabled();

        Role parsedRole =
                Role
                        .parse(role)
                        .orElseThrow(() ->
                                new AuthException(
                                        HttpStatus.BAD_REQUEST,
                                        "INVALID_ROLE",
                                        "Role must be VIEWER, OPERATOR or ADMIN."
                                )
                        );

        return insert(
                username,
                displayName,
                parsedRole,
                password
        );
    }

    @Transactional
    public UserSummary createInitialAdmin(
            String username,
            String password
    ) {
        requireEnabled();

        return insert(
                username,
                null,
                Role.ADMIN,
                password
        );
    }

    @Transactional
    public UserSummary update(
            AuthenticatedUser actor,
            UUID id,
            String displayName,
            String role,
            Boolean enabled
    ) {
        requireEnabled();

        UserAccount current =
                store
                        .findById(id)
                        .orElseThrow(UserAdminService::notFound);

        String nextName =
                displayName == null
                        ? current.displayName()
                        : Credentials.displayNameOrDefault(
                                displayName,
                                current.username()
                        );

        Role nextRole =
                role == null
                        ? current.role()
                        : Role
                                .parse(role)
                                .orElseThrow(() ->
                                        new AuthException(
                                                HttpStatus.BAD_REQUEST,
                                                "INVALID_ROLE",
                                                "Role must be VIEWER, OPERATOR or ADMIN."
                                        )
                                );

        boolean nextEnabled =
                enabled == null
                        ? current.enabled()
                        : enabled;

        if (
                actor.id().equals(id)
                && (
                        nextRole != current.role()
                        || nextEnabled != current.enabled()
                )
        ) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "SELF_CHANGE",
                    "You can't change your own role or disable your own account."
            );
        }

        boolean losesAdminAccess =
                current.role() == Role.ADMIN
                        && current.enabled()
                        && !(
                                nextRole == Role.ADMIN
                                && nextEnabled
                        );

        if (losesAdminAccess) {
            store.lockEnabledAdmins();

            if (store.countEnabledAdminsExcluding(id) == 0) {
                throw new AuthException(
                        HttpStatus.CONFLICT,
                        "LAST_ADMIN",
                        "At least one enabled administrator must remain."
                );
            }
        }

        boolean revokeTokens =
                nextRole != current.role()
                        || (
                                current.enabled()
                                && !nextEnabled
                        );

        UserAccount updated =
                store
                        .updateProfile(
                                id,
                                nextName,
                                nextRole,
                                nextEnabled,
                                revokeTokens
                        )
                        .orElseThrow(UserAdminService::notFound);

        directory.invalidate();

        return UserSummary.from(updated);
    }

    @Transactional
    public void resetPassword(
            UUID id,
            String newPassword
    ) {
        requireEnabled();

        UserAccount account =
                store
                        .findById(id)
                        .orElseThrow(UserAdminService::notFound);

        Credentials.requireStrongPassword(
                newPassword,
                account.username()
        );

        store
                .updatePassword(
                        id,
                        hasher.hash(newPassword)
                )
                .orElseThrow(UserAdminService::notFound);

        directory.invalidate();
    }

    private UserSummary insert(
            String username,
            String displayName,
            Role role,
            String password
    ) {
        String normalized =
                Credentials.normalizeUsername(username);

        String name =
                Credentials.displayNameOrDefault(
                        displayName,
                        normalized
                );

        Credentials.requireStrongPassword(
                password,
                normalized
        );

        if (store.findByUsername(normalized).isPresent()) {
            throw usernameTaken();
        }

        try {
            UserAccount created =
                    store.insert(
                            new UserAccount(
                                    UUID.randomUUID(),
                                    normalized,
                                    name,
                                    role,
                                    hasher.hash(password),
                                    true,
                                    0,
                                    Instant.now(),
                                    null
                            )
                    );

            return UserSummary.from(created);

        } catch (DuplicateKeyException exception) {
            throw usernameTaken();
        }
    }

    private void requireEnabled() {
        if (!settings.enabled()) {
            throw new AuthException(
                    HttpStatus.NOT_FOUND,
                    "SECURITY_DISABLED",
                    "Sign-in is not enabled on this server."
            );
        }
    }

    private static AuthException usernameTaken() {
        return new AuthException(
                HttpStatus.CONFLICT,
                "USERNAME_TAKEN",
                "That username is already in use."
        );
    }

    private static AuthException notFound() {
        return new AuthException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "That user no longer exists."
        );
    }
}
