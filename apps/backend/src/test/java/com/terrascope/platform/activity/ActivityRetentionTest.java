package com.terrascope.platform.activity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityRetentionTest {

    private static final Instant NOW =
            Instant.parse("2026-10-10T12:00:00Z");

    private final InMemoryActivityStore store =
            new InMemoryActivityStore();

    private void add(
            int daysAgo
    ) {
        store.entries.add(
                new ActivityEntry(
                        UUID.randomUUID(),
                        NOW.minusSeconds(daysAgo * 86400L),
                        null,
                        "maya",
                        "Maya",
                        null,
                        "POST",
                        "/api/v1/incidents",
                        "Created incident",
                        "incident",
                        null,
                        200
                )
        );
    }

    @Test
    void removesOnlyEntriesOlderThanTheRetentionWindow() {
        add(1);
        add(179);
        add(181);
        add(400);

        int removed =
                new ActivityRetention(store, 180).prune(NOW);

        assertEquals(2, removed);
        assertEquals(2, store.entries.size());
    }

    @Test
    void keepsEverythingWhenRetentionIsSwitchedOff() {
        add(1);
        add(4000);

        assertEquals(0, new ActivityRetention(store, 0).prune(NOW));
        assertEquals(0, new ActivityRetention(store, -5).prune(NOW));
        assertEquals(2, store.entries.size());
    }

    @Test
    void survivesAFailingStoreDuringTheScheduledRun() {
        ActivityStore broken =
                new InMemoryActivityStore() {
                    @Override
                    public int deleteOlderThan(Instant cutoff) {
                        throw new IllegalStateException("database down");
                    }
                };

        new ActivityRetention(broken, 30).scheduledPrune();
    }
}
