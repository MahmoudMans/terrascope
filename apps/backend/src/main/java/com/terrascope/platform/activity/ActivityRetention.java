package com.terrascope.platform.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class ActivityRetention {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ActivityRetention.class
            );

    private final ActivityStore store;
    private final int retentionDays;

    public ActivityRetention(
            ActivityStore store,
            @Value(
                    "${terrascope.activity.retention-days:180}"
            )
            int retentionDays
    ) {
        this.store = store;
        this.retentionDays = retentionDays;
    }

    @Scheduled(
            cron = "0 15 3 * * *"
    )
    public void scheduledPrune() {
        try {
            prune(Instant.now());

        } catch (RuntimeException exception) {
            log.warn(
                    "Activity clean-up failed: {}",
                    exception.getMessage()
            );
        }
    }

    public int prune(
            Instant now
    ) {
        if (retentionDays <= 0) {
            return 0;
        }

        int removed =
                store.deleteOlderThan(
                        now.minus(
                                Duration.ofDays(retentionDays)
                        )
                );

        if (removed > 0) {
            log.info(
                    "Removed {} activity entries older than {} days",
                    removed,
                    retentionDays
            );
        }

        return removed;
    }
}
