package com.terrascope.platform.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedCacheTest {

    private final MutableClock clock =
            new MutableClock();

    private final FeedCache<String, String> cache =
            new FeedCache<>(
                    Duration.ofMinutes(2),
                    Duration.ofMinutes(10),
                    3,
                    clock
            );

    @Test
    void servesFreshValueWithoutCallingLoaderAgain() {
        AtomicInteger calls =
                new AtomicInteger();

        String first =
                cache.get(
                        "feed",
                        () -> "value-" + calls.incrementAndGet()
                );

        clock.advance(Duration.ofSeconds(90));

        String second =
                cache.get(
                        "feed",
                        () -> "value-" + calls.incrementAndGet()
                );

        assertEquals("value-1", first);
        assertEquals("value-1", second);
        assertEquals(1, calls.get());
    }

    @Test
    void reloadsOnceTheFreshWindowHasPassed() {
        AtomicInteger calls =
                new AtomicInteger();

        cache.get(
                "feed",
                () -> "value-" + calls.incrementAndGet()
        );

        clock.advance(Duration.ofMinutes(3));

        String reloaded =
                cache.get(
                        "feed",
                        () -> "value-" + calls.incrementAndGet()
                );

        assertEquals("value-2", reloaded);
        assertEquals(2, calls.get());
    }

    @Test
    void servesStaleValueWhenReloadFailsInsideStaleWindow() {
        cache.get(
                "feed",
                () -> "original"
        );

        clock.advance(Duration.ofMinutes(5));

        String served =
                cache.get(
                        "feed",
                        () -> {
                            throw new IllegalStateException("upstream down");
                        }
                );

        assertEquals("original", served);
    }

    @Test
    void rethrowsWhenStaleValueIsTooOld() {
        cache.get(
                "feed",
                () -> "original"
        );

        clock.advance(Duration.ofMinutes(11));

        assertThrows(
                IllegalStateException.class,
                () -> cache.get(
                        "feed",
                        () -> {
                            throw new IllegalStateException("upstream down");
                        }
                )
        );
    }

    @Test
    void rethrowsWhenNothingIsCachedYet() {
        assertThrows(
                IllegalStateException.class,
                () -> cache.get(
                        "feed",
                        () -> {
                            throw new IllegalStateException("upstream down");
                        }
                )
        );
    }

    @Test
    void keepsKeysIndependent() {
        cache.get("a", () -> "first");

        assertEquals(
                "second",
                cache.get("b", () -> "second")
        );

        assertEquals(
                "first",
                cache.get("a", () -> "unused")
        );
    }

    @Test
    void clearDropsEveryEntry() {
        cache.get("a", () -> "first");

        cache.clear();

        assertEquals(
                "fresh",
                cache.get("a", () -> "fresh")
        );
    }

    @Test
    void evictsTheOldestEntryWhenOverCapacity() {
        cache.get("a", () -> "A");
        clock.advance(Duration.ofSeconds(1));
        cache.get("b", () -> "B");
        clock.advance(Duration.ofSeconds(1));
        cache.get("c", () -> "C");
        clock.advance(Duration.ofSeconds(1));
        cache.get("d", () -> "D");

        assertEquals(3, cache.size());

        assertEquals(
                "reloaded",
                cache.get("a", () -> "reloaded")
        );
    }

    @Test
    void dropsEntriesPastTheStaleWindowBeforeEvictingFreshOnes() {
        cache.get("old", () -> "OLD");
        clock.advance(Duration.ofMinutes(11));
        cache.get("b", () -> "B");
        cache.get("c", () -> "C");
        cache.get("d", () -> "D");

        assertEquals(3, cache.size());

        assertEquals(
                "B",
                cache.get("b", () -> "unused")
        );
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
