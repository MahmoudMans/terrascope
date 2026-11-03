package com.terrascope.platform.security;

import com.terrascope.platform.infrastructure.FeedCache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class UserDirectory {

    private final UserStore store;

    private final FeedCache<UUID, Optional<UserAccount>> cache =
            new FeedCache<>(
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(30)
            );

    public UserDirectory(
            UserStore store
    ) {
        this.store = store;
    }

    public Optional<UserAccount> find(
            UUID id
    ) {
        return cache.get(
                id,
                () -> store.findById(id)
        );
    }

    public void invalidate() {
        cache.clear();
    }
}
