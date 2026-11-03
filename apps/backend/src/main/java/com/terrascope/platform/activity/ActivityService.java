package com.terrascope.platform.activity;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ActivityService {

    public static final int DEFAULT_LIMIT =
            50;

    public static final int MAX_LIMIT =
            200;

    private final ActivityStore store;

    public ActivityService(
            ActivityStore store
    ) {
        this.store = store;
    }

    public ActivityPage page(
            Integer limit,
            Instant before,
            String actor
    ) {
        int size =
                limit == null
                        ? DEFAULT_LIMIT
                        : Math.max(
                                1,
                                Math.min(
                                        limit,
                                        MAX_LIMIT
                                )
                        );

        String who =
                actor == null
                        || actor.isBlank()
                        ? null
                        : actor
                                .trim()
                                .toLowerCase(Locale.ROOT);

        List<ActivityEntry> found =
                store.recent(
                        size + 1,
                        before,
                        who
                );

        if (found.size() <= size) {
            return new ActivityPage(
                    List.copyOf(found),
                    null
            );
        }

        List<ActivityEntry> entries =
                List.copyOf(
                        found.subList(
                                0,
                                size
                        )
                );

        return new ActivityPage(
                entries,
                entries
                        .get(entries.size() - 1)
                        .occurredAt()
        );
    }
}
