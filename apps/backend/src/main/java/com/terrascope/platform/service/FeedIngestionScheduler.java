package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AlertEvaluationResponse;
import com.terrascope.platform.api.model.AutomationStatus;
import com.terrascope.platform.api.model.EarthquakeFeedResponse;
import com.terrascope.platform.api.model.EvaluateAlertRulesRequest;
import com.terrascope.platform.api.model.LiveAlertUpdate;
import com.terrascope.platform.api.model.LiveFeedUpdate;
import com.terrascope.platform.api.model.NaturalHazardFeedResponse;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import com.terrascope.platform.api.model.VolcanoFeedResponse;
import com.terrascope.platform.domain.EarthquakePeriod;
import com.terrascope.platform.domain.EarthquakeSort;
import com.terrascope.platform.domain.HazardType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Service
public class FeedIngestionScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    FeedIngestionScheduler.class
            );

    private static final Duration DEFAULT_INTERVAL =
            Duration.ofMinutes(2);

    private final EarthquakeService earthquakeService;
    private final VolcanoService volcanoService;
    private final NaturalHazardService naturalHazardService;
    private final HazardAlertService alertService;
    private final LiveEventBroadcaster broadcaster;
    private final boolean enabled;
    private final boolean evaluateAlerts;
    private final long intervalSeconds;

    private volatile AutomationStatus lastStatus;

    public FeedIngestionScheduler(
            EarthquakeService earthquakeService,
            VolcanoService volcanoService,
            NaturalHazardService naturalHazardService,
            HazardAlertService alertService,
            LiveEventBroadcaster broadcaster,
            @Value(
                    "${terrascope.automation.ingestion.enabled:true}"
            )
            boolean enabled,
            @Value(
                    "${terrascope.automation.ingestion.evaluate-alerts:true}"
            )
            boolean evaluateAlerts,
            @Value(
                    "${terrascope.automation.ingestion.interval:PT2M}"
            )
            String interval
    ) {
        this.earthquakeService = earthquakeService;
        this.volcanoService = volcanoService;
        this.naturalHazardService = naturalHazardService;
        this.alertService = alertService;
        this.broadcaster = broadcaster;
        this.enabled = enabled;
        this.evaluateAlerts = evaluateAlerts;
        this.intervalSeconds =
                parseInterval(interval)
                        .toSeconds();

        this.lastStatus =
                new AutomationStatus(
                        enabled,
                        evaluateAlerts,
                        intervalSeconds,
                        null,
                        true,
                        0,
                        0,
                        0
                );
    }

    public AutomationStatus status() {
        AutomationStatus current =
                lastStatus;

        return new AutomationStatus(
                current.enabled(),
                current.evaluatesAlerts(),
                current.intervalSeconds(),
                current.lastRunAt(),
                current.lastRunSucceeded(),
                current.lastPointCount(),
                current.lastNewAlertCount(),
                broadcaster.clientCount()
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${terrascope.automation.ingestion.interval:PT2M}",
            initialDelayString =
                    "${terrascope.automation.ingestion.initial-delay:PT20S}"
    )
    public void runScheduled() {
        if (!enabled) {
            return;
        }

        run();
    }

    public void run() {
        Instant startedAt =
                Instant.now();

        try {
            EarthquakeFeedResponse earthquakes =
                    safely(
                            "earthquakes",
                            () -> earthquakeService.query(
                                    EarthquakePeriod.WEEK,
                                    2.5,
                                    1000.0,
                                    500,
                                    "",
                                    EarthquakeSort.TIME
                            )
                    );

            VolcanoFeedResponse volcanoes =
                    safely(
                            "volcanoes",
                            () -> volcanoService.query(
                                    "open",
                                    3650,
                                    500,
                                    ""
                            )
                    );

            NaturalHazardFeedResponse wildfires =
                    safely(
                            "wildfires",
                            () -> naturalHazardService.query(
                                    HazardType.WILDFIRE,
                                    "open",
                                    365,
                                    500,
                                    ""
                            )
                    );

            NaturalHazardFeedResponse storms =
                    safely(
                            "severe storms",
                            () -> naturalHazardService.query(
                                    HazardType.SEVERE_STORM,
                                    "open",
                                    365,
                                    500,
                                    ""
                            )
                    );

            warmDashboardFeeds();

            int pointCount = 0;
            int newlyTriggered = 0;

            if (evaluateAlerts) {
                List<RegionalHazardPoint> points =
                        HazardPointCollector.collect(
                                earthquakes,
                                volcanoes,
                                wildfires,
                                storms
                        );

                pointCount = points.size();

                if (!points.isEmpty()) {
                    AlertEvaluationResponse response =
                            alertService.evaluate(
                                    new EvaluateAlertRulesRequest(
                                            points
                                    )
                            );

                    newlyTriggered =
                            response.newlyTriggeredCount();

                    if (newlyTriggered > 0) {
                        broadcaster.publish(
                                "alerts",
                                new LiveAlertUpdate(
                                        newlyTriggered,
                                        response
                                                .dashboard()
                                                .newAlertCount(),
                                        Instant.now()
                                )
                        );
                    }
                }
            }

            recordRun(
                    startedAt,
                    true,
                    pointCount,
                    newlyTriggered
            );

            int liveFeedCount =
                    countLiveFeeds(
                            earthquakes,
                            volcanoes,
                            wildfires,
                            storms
                    );

            if (liveFeedCount > 0) {
                broadcaster.publish(
                        "feeds",
                        new LiveFeedUpdate(
                                liveFeedCount,
                                Instant.now()
                        )
                );
            }

            log.info(
                    "Feed ingestion finished: {} points checked, {} new alerts.",
                    pointCount,
                    newlyTriggered
            );

        } catch (RuntimeException exception) {
            recordRun(
                    startedAt,
                    false,
                    0,
                    0
            );

            log.warn(
                    "Feed ingestion failed: {}",
                    exception.toString()
            );
        }
    }

    private static int countLiveFeeds(
            EarthquakeFeedResponse earthquakes,
            VolcanoFeedResponse volcanoes,
            NaturalHazardFeedResponse wildfires,
            NaturalHazardFeedResponse storms
    ) {
        int count = 0;

        if (earthquakes != null && earthquakes.live()) {
            count++;
        }

        if (volcanoes != null && volcanoes.live()) {
            count++;
        }

        if (wildfires != null && wildfires.live()) {
            count++;
        }

        if (storms != null && storms.live()) {
            count++;
        }

        return count;
    }

    private void warmDashboardFeeds() {
        safely(
                "dashboard earthquakes",
                () -> earthquakeService.query(
                        EarthquakePeriod.DAY,
                        2.5,
                        700.0,
                        300,
                        "",
                        EarthquakeSort.TIME
                )
        );

        safely(
                "dashboard wildfires",
                () -> naturalHazardService.query(
                        HazardType.WILDFIRE,
                        "open",
                        90,
                        120,
                        ""
                )
        );

        safely(
                "dashboard severe storms",
                () -> naturalHazardService.query(
                        HazardType.SEVERE_STORM,
                        "open",
                        90,
                        120,
                        ""
                )
        );
    }

    private void recordRun(
            Instant startedAt,
            boolean succeeded,
            int pointCount,
            int newlyTriggered
    ) {
        lastStatus =
                new AutomationStatus(
                        enabled,
                        evaluateAlerts,
                        intervalSeconds,
                        startedAt,
                        succeeded,
                        pointCount,
                        newlyTriggered,
                        0
                );
    }

    private <T> T safely(
            String label,
            Supplier<T> action
    ) {
        try {
            return action.get();

        } catch (RuntimeException exception) {
            log.warn(
                    "Scheduled refresh of {} failed: {}",
                    label,
                    exception.toString()
            );

            return null;
        }
    }

    private static Duration parseInterval(
            String value
    ) {
        try {
            Duration parsed =
                    Duration.parse(
                            value.trim()
                    );

            if (parsed.isNegative() || parsed.isZero()) {
                return DEFAULT_INTERVAL;
            }

            return parsed;

        } catch (RuntimeException exception) {
            return DEFAULT_INTERVAL;
        }
    }
}
