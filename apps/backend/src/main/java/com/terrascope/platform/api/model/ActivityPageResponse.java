package com.terrascope.platform.api.model;

import com.terrascope.platform.activity.ActivityPage;

import java.time.Instant;
import java.util.List;

public record ActivityPageResponse(
        List<ActivityEntryView> entries,
        Instant nextBefore
) {

    public static ActivityPageResponse from(
            ActivityPage page
    ) {
        return new ActivityPageResponse(
                page
                        .entries()
                        .stream()
                        .map(ActivityEntryView::from)
                        .toList(),
                page.nextBefore()
        );
    }
}
