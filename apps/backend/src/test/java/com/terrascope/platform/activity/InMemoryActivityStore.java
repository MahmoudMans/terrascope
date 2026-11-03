package com.terrascope.platform.activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class InMemoryActivityStore implements ActivityStore {

    final List<ActivityEntry> entries = new ArrayList<>();

    boolean failing;

    @Override
    public void record(
            ActivityEntry entry
    ) {
        if (failing) {
            throw new IllegalStateException("database down");
        }

        entries.add(entry);
    }

    @Override
    public List<ActivityEntry> recent(
            int limit,
            Instant before,
            String actor
    ) {
        return entries
                .stream()
                .filter(entry -> before == null || entry.occurredAt().isBefore(before))
                .filter(entry -> actor == null || entry.actorUsername().equals(actor))
                .sorted(Comparator.comparing(ActivityEntry::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public int deleteOlderThan(
            Instant cutoff
    ) {
        int before = entries.size();

        entries.removeIf(entry -> entry.occurredAt().isBefore(cutoff));

        return before - entries.size();
    }
}
