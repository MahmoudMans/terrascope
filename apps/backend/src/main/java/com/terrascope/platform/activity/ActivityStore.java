package com.terrascope.platform.activity;

import java.time.Instant;
import java.util.List;

public interface ActivityStore {

    void record(
            ActivityEntry entry
    );

    List<ActivityEntry> recent(
            int limit,
            Instant before,
            String actor
    );

    int deleteOlderThan(
            Instant cutoff
    );
}
