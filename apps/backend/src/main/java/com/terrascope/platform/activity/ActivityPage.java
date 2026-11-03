package com.terrascope.platform.activity;

import java.time.Instant;
import java.util.List;

public record ActivityPage(
        List<ActivityEntry> entries,
        Instant nextBefore
) {
}
