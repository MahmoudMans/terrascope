package com.terrascope.platform.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class InMemoryUserStore implements UserStore {

    final Map<UUID, UserAccount> accounts =
            new LinkedHashMap<>();

    int lockCalls;
    int findByIdCalls;

    @Override
    public Optional<UserAccount> findById(
            UUID id
    ) {
        findByIdCalls++;

        return Optional.ofNullable(
                accounts.get(id)
        );
    }

    @Override
    public Optional<UserAccount> findByUsername(
            String username
    ) {
        return accounts
                .values()
                .stream()
                .filter(account ->
                        account.username().equals(username)
                )
                .findFirst();
    }

    @Override
    public List<UserAccount> findAll() {
        List<UserAccount> all =
                new ArrayList<>(accounts.values());

        all.sort(
                Comparator.comparing(UserAccount::username)
        );

        return all;
    }

    @Override
    public long count() {
        return accounts.size();
    }

    @Override
    public long countEnabledAdminsExcluding(
            UUID id
    ) {
        return accounts
                .values()
                .stream()
                .filter(account ->
                        account.role() == Role.ADMIN
                                && account.enabled()
                                && !account.id().equals(id)
                )
                .count();
    }

    @Override
    public void lockEnabledAdmins() {
        lockCalls++;
    }

    @Override
    public UserAccount insert(
            UserAccount account
    ) {
        accounts.put(
                account.id(),
                account
        );

        return account;
    }

    @Override
    public Optional<UserAccount> updateProfile(
            UUID id,
            String displayName,
            Role role,
            boolean enabled,
            boolean revokeTokens
    ) {
        UserAccount current =
                accounts.get(id);

        if (current == null) {
            return Optional.empty();
        }

        UserAccount updated =
                new UserAccount(
                        current.id(),
                        current.username(),
                        displayName,
                        role,
                        current.passwordHash(),
                        enabled,
                        current.tokenVersion()
                                + (revokeTokens ? 1 : 0),
                        current.createdAt(),
                        current.lastLoginAt()
                );

        accounts.put(id, updated);

        return Optional.of(updated);
    }

    @Override
    public Optional<UserAccount> updatePassword(
            UUID id,
            String passwordHash
    ) {
        UserAccount current =
                accounts.get(id);

        if (current == null) {
            return Optional.empty();
        }

        UserAccount updated =
                new UserAccount(
                        current.id(),
                        current.username(),
                        current.displayName(),
                        current.role(),
                        passwordHash,
                        current.enabled(),
                        current.tokenVersion() + 1,
                        current.createdAt(),
                        current.lastLoginAt()
                );

        accounts.put(id, updated);

        return Optional.of(updated);
    }

    @Override
    public void recordLogin(
            UUID id,
            Instant at
    ) {
        UserAccount current =
                accounts.get(id);

        if (current == null) {
            return;
        }

        accounts.put(
                id,
                new UserAccount(
                        current.id(),
                        current.username(),
                        current.displayName(),
                        current.role(),
                        current.passwordHash(),
                        current.enabled(),
                        current.tokenVersion(),
                        current.createdAt(),
                        at
                )
        );
    }
}
