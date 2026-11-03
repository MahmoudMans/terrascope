package com.terrascope.platform.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class LoginThrottle {

    private static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Duration lockout;

    private final Map<String, Attempts> entries =
            new HashMap<>();

    public LoginThrottle(
            int maxFailures,
            Duration window,
            Duration lockout
    ) {
        this(
                Clock.systemUTC(),
                maxFailures,
                window,
                lockout
        );
    }

    LoginThrottle(
            Clock clock,
            int maxFailures,
            Duration window,
            Duration lockout
    ) {
        this.clock = clock;
        this.maxFailures = Math.max(1, maxFailures);
        this.window = window;
        this.lockout = lockout;
    }

    public synchronized long secondsLocked(
            String key
    ) {
        Attempts attempts =
                entries.get(key);

        if (
                attempts == null
                || attempts.lockedUntil == null
        ) {
            return 0;
        }

        Instant now =
                clock.instant();

        if (!now.isBefore(attempts.lockedUntil)) {
            entries.remove(key);

            return 0;
        }

        long millis =
                Duration
                        .between(
                                now,
                                attempts.lockedUntil
                        )
                        .toMillis();

        return Math.max(
                1,
                (millis + 999) / 1000
        );
    }

    public synchronized void recordFailure(
            String key
    ) {
        Instant now =
                clock.instant();

        Attempts attempts =
                entries.get(key);

        if (
                attempts != null
                && isExpired(attempts, now)
        ) {
            entries.remove(key);

            attempts = null;
        }

        if (attempts == null) {
            if (entries.size() >= MAX_KEYS) {
                prune(now);
            }

            if (entries.size() >= MAX_KEYS) {
                return;
            }

            attempts =
                    new Attempts(now);

            entries.put(
                    key,
                    attempts
            );
        }

        attempts.failures++;

        if (attempts.failures >= maxFailures) {
            attempts.lockedUntil =
                    now.plus(lockout);
        }
    }

    public synchronized void recordSuccess(
            String key
    ) {
        entries.remove(key);
    }

    public synchronized int trackedKeys() {
        return entries.size();
    }

    private boolean isExpired(
            Attempts attempts,
            Instant now
    ) {
        if (attempts.lockedUntil != null) {
            return !now.isBefore(attempts.lockedUntil);
        }

        return now.isAfter(
                attempts.firstFailureAt.plus(window)
        );
    }

    private void prune(
            Instant now
    ) {
        entries
                .entrySet()
                .removeIf(entry ->
                        isExpired(
                                entry.getValue(),
                                now
                        )
                );
    }

    private static final class Attempts {

        private final Instant firstFailureAt;
        private int failures;
        private Instant lockedUntil;

        private Attempts(
                Instant firstFailureAt
        ) {
            this.firstFailureAt = firstFailureAt;
        }
    }
}
