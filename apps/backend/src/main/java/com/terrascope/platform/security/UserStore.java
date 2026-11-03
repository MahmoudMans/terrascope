package com.terrascope.platform.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStore {

    Optional<UserAccount> findById(
            UUID id
    );

    Optional<UserAccount> findByUsername(
            String username
    );

    List<UserAccount> findAll();

    long count();

    long countEnabledAdminsExcluding(
            UUID id
    );

    void lockEnabledAdmins();

    UserAccount insert(
            UserAccount account
    );

    Optional<UserAccount> updateProfile(
            UUID id,
            String displayName,
            Role role,
            boolean enabled,
            boolean revokeTokens
    );

    Optional<UserAccount> updatePassword(
            UUID id,
            String passwordHash
    );

    void recordLogin(
            UUID id,
            Instant at
    );
}
