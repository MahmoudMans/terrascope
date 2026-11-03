package com.terrascope.platform.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    private final MutableClock clock =
            new MutableClock();

    private final LoginThrottle throttle =
            new LoginThrottle(
                    clock,
                    3,
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(15)
            );

    @Test
    void locksAfterTheConfiguredNumberOfFailures() {
        throttle.recordFailure("alice");
        throttle.recordFailure("alice");

        assertEquals(0, throttle.secondsLocked("alice"));

        throttle.recordFailure("alice");

        assertEquals(900, throttle.secondsLocked("alice"));
    }

    @Test
    void reportsTheRemainingLockTime() {
        for (int index = 0; index < 3; index++) {
            throttle.recordFailure("alice");
        }

        clock.advance(Duration.ofMinutes(10));

        assertEquals(300, throttle.secondsLocked("alice"));
    }

    @Test
    void unlocksOnceTheLockoutHasPassed() {
        for (int index = 0; index < 3; index++) {
            throttle.recordFailure("alice");
        }

        clock.advance(Duration.ofMinutes(15));

        assertEquals(0, throttle.secondsLocked("alice"));
        assertEquals(0, throttle.trackedKeys());
    }

    @Test
    void keepsKeysIndependent() {
        for (int index = 0; index < 3; index++) {
            throttle.recordFailure("alice");
        }

        assertEquals(0, throttle.secondsLocked("bob"));
    }

    @Test
    void aSuccessClearsTheCounter() {
        throttle.recordFailure("alice");
        throttle.recordFailure("alice");
        throttle.recordSuccess("alice");
        throttle.recordFailure("alice");
        throttle.recordFailure("alice");

        assertEquals(0, throttle.secondsLocked("alice"));
    }

    @Test
    void forgetsOldFailuresOutsideTheWindow() {
        throttle.recordFailure("alice");
        throttle.recordFailure("alice");

        clock.advance(Duration.ofMinutes(11));

        throttle.recordFailure("alice");

        assertEquals(0, throttle.secondsLocked("alice"));
    }

    @Test
    void startsAFreshWindowAfterALockoutEnds() {
        for (int index = 0; index < 3; index++) {
            throttle.recordFailure("alice");
        }

        clock.advance(Duration.ofMinutes(16));

        throttle.recordFailure("alice");

        assertEquals(0, throttle.secondsLocked("alice"));
        assertEquals(1, throttle.trackedKeys());
    }

    @Test
    void staysBoundedUnderManyDistinctKeys() {
        for (int index = 0; index < 12_000; index++) {
            throttle.recordFailure("key-" + index);
        }

        assertTrue(throttle.trackedKeys() <= 10_000);
    }

    private static final class MutableClock extends Clock {

        private Instant now =
                Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
