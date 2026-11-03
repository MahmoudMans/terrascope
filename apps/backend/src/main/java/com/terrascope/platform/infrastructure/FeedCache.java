package com.terrascope.platform.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class FeedCache<K, V> {

    private static final int DEFAULT_MAX_ENTRIES = 64;

    private final Duration freshFor;
    private final Duration staleFor;
    private final Clock clock;
    private final int maxEntries;

    private final Map<K, Entry<V>> entries =
            new ConcurrentHashMap<>();

    private final Map<K, Object> locks =
            new ConcurrentHashMap<>();

    public FeedCache(
            Duration freshFor,
            Duration staleFor
    ) {
        this(
                freshFor,
                staleFor,
                DEFAULT_MAX_ENTRIES,
                Clock.systemUTC()
        );
    }

    FeedCache(
            Duration freshFor,
            Duration staleFor,
            int maxEntries,
            Clock clock
    ) {
        this.freshFor = freshFor;
        this.staleFor = staleFor;
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    public V get(
            K key,
            Supplier<V> loader
    ) {
        Entry<V> cached =
                entries.get(key);

        if (isFresh(cached)) {
            return cached.value();
        }

        Object lock =
                locks.computeIfAbsent(
                        key,
                        ignored -> new Object()
                );

        synchronized (lock) {
            Entry<V> current =
                    entries.get(key);

            if (isFresh(current)) {
                return current.value();
            }

            try {
                V loaded =
                        loader.get();

                entries.put(
                        key,
                        new Entry<>(
                                loaded,
                                clock.instant()
                        )
                );

                trim();

                return loaded;

            } catch (RuntimeException exception) {
                if (isUsableStale(current)) {
                    return current.value();
                }

                throw exception;
            }
        }
    }

    public void clear() {
        entries.clear();
        locks.clear();
    }

    public int size() {
        return entries.size();
    }

    private void trim() {
        if (entries.size() <= maxEntries) {
            return;
        }

        entries.entrySet()
                .removeIf(entry ->
                        !isUsableStale(entry.getValue())
                );

        while (entries.size() > maxEntries) {
            K oldestKey = null;
            Instant oldestAt = null;

            for (Map.Entry<K, Entry<V>> entry
                    : entries.entrySet()) {
                Instant loadedAt =
                        entry.getValue().loadedAt();

                if (
                        oldestAt == null
                                || loadedAt.isBefore(oldestAt)
                ) {
                    oldestKey = entry.getKey();
                    oldestAt = loadedAt;
                }
            }

            if (oldestKey == null) {
                return;
            }

            entries.remove(oldestKey);
            locks.remove(oldestKey);
        }

        locks.keySet()
                .removeIf(key ->
                        !entries.containsKey(key)
                );
    }

    private boolean isFresh(
            Entry<V> entry
    ) {
        return entry != null
                && ageOf(entry)
                .compareTo(freshFor) < 0;
    }

    private boolean isUsableStale(
            Entry<V> entry
    ) {
        return entry != null
                && ageOf(entry)
                .compareTo(staleFor) < 0;
    }

    private Duration ageOf(
            Entry<V> entry
    ) {
        return Duration.between(
                entry.loadedAt(),
                clock.instant()
        );
    }

    private record Entry<V>(
            V value,
            Instant loadedAt
    ) {
    }
}
