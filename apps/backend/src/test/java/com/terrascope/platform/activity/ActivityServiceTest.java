package com.terrascope.platform.activity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActivityServiceTest {

    private static final Instant START =
            Instant.parse("2026-10-10T12:00:00Z");

    private final InMemoryActivityStore store =
            new InMemoryActivityStore();

    private final ActivityService service =
            new ActivityService(store);

    private void add(
            String actor,
            int minutesAgo
    ) {
        store.entries.add(
                new ActivityEntry(
                        UUID.randomUUID(),
                        START.minusSeconds(minutesAgo * 60L),
                        null,
                        actor,
                        actor,
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
    void returnsTheNewestEntriesFirst() {
        add("maya", 30);
        add("omar", 5);
        add("maya", 15);

        ActivityPage page =
                service.page(null, null, null);

        assertEquals(3, page.entries().size());
        assertEquals(START.minusSeconds(300), page.entries().get(0).occurredAt());
        assertEquals(START.minusSeconds(1800), page.entries().get(2).occurredAt());
        assertNull(page.nextBefore());
    }

    @Test
    void offersAnOlderPageOnlyWhenThereIsMore() {
        for (int index = 1; index <= 5; index++) {
            add("maya", index);
        }

        ActivityPage first =
                service.page(2, null, null);

        assertEquals(2, first.entries().size());
        assertEquals(START.minusSeconds(120), first.nextBefore());

        ActivityPage second =
                service.page(2, first.nextBefore(), null);

        assertEquals(2, second.entries().size());
        assertEquals(START.minusSeconds(240), second.nextBefore());

        ActivityPage third =
                service.page(2, second.nextBefore(), null);

        assertEquals(1, third.entries().size());
        assertNull(third.nextBefore());

        ActivityPage exact =
                service.page(5, null, null);

        assertEquals(5, exact.entries().size());
        assertNull(exact.nextBefore());
    }

    @Test
    void keepsThePageSizeWithinSensibleBounds() {
        for (int index = 1; index <= 300; index++) {
            add("maya", index);
        }

        assertEquals(50, service.page(null, null, null).entries().size());
        assertEquals(1, service.page(0, null, null).entries().size());
        assertEquals(1, service.page(-7, null, null).entries().size());
        assertEquals(200, service.page(5000, null, null).entries().size());
    }

    @Test
    void filtersByPersonIgnoringCaseAndSpacing() {
        add("maya", 10);
        add("omar", 9);
        add("maya", 8);

        assertEquals(2, service.page(null, null, "  MAYA ").entries().size());
        assertEquals(1, service.page(null, null, "omar").entries().size());
        assertEquals(0, service.page(null, null, "nobody").entries().size());
        assertEquals(3, service.page(null, null, "   ").entries().size());
        assertEquals(3, service.page(null, null, "").entries().size());
    }
}
