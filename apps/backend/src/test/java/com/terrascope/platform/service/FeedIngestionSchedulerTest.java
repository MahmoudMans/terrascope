package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AlertDashboardResponse;
import com.terrascope.platform.api.model.AlertEvaluationResponse;
import com.terrascope.platform.api.model.AutomationStatus;
import com.terrascope.platform.api.model.EarthquakeEvent;
import com.terrascope.platform.api.model.EarthquakeFeedResponse;
import com.terrascope.platform.api.model.EvaluateAlertRulesRequest;
import com.terrascope.platform.api.model.LiveAlertUpdate;
import com.terrascope.platform.api.model.LiveFeedUpdate;
import com.terrascope.platform.api.model.NaturalHazardEvent;
import com.terrascope.platform.api.model.NaturalHazardFeedResponse;
import com.terrascope.platform.api.model.VolcanoEvent;
import com.terrascope.platform.api.model.VolcanoFeedResponse;
import com.terrascope.platform.domain.EarthquakePeriod;
import com.terrascope.platform.domain.EarthquakeSort;
import com.terrascope.platform.domain.HazardType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedIngestionSchedulerTest {

    private static final Instant NOW =
            Instant.parse("2026-10-10T12:00:00Z");

    private final FakeEarthquakes earthquakes =
            new FakeEarthquakes();

    private final FakeVolcanoes volcanoes =
            new FakeVolcanoes();

    private final FakeNaturalHazards naturalHazards =
            new FakeNaturalHazards();

    private final FakeAlerts alerts =
            new FakeAlerts();

    private final RecordingBroadcaster broadcaster =
            new RecordingBroadcaster();

    @Test
    void evaluatesLivePointsAndPublishesNewAlerts() {
        alerts.newlyTriggered = 2;
        alerts.newAlertCount = 5;

        scheduler(true, true, "PT2M").run();

        assertEquals(1, alerts.calls);
        assertEquals(3, alerts.lastRequest.points().size());

        assertEquals(2, broadcaster.published.size());
        assertEquals("alerts", broadcaster.names.get(0));
        assertEquals("feeds", broadcaster.names.get(1));

        LiveFeedUpdate feedUpdate =
                (LiveFeedUpdate) broadcaster.published.get(1);

        assertEquals(4, feedUpdate.liveFeedCount());
        assertNotNull(feedUpdate.refreshedAt());

        LiveAlertUpdate update =
                (LiveAlertUpdate) broadcaster.published.get(0);

        assertEquals(2, update.newlyTriggeredCount());
        assertEquals(5, update.newAlertCount());
        assertNotNull(update.evaluatedAt());
    }

    @Test
    void recordsTheOutcomeOfTheLastRun() {
        alerts.newlyTriggered = 1;

        FeedIngestionScheduler scheduler =
                scheduler(true, true, "PT2M");

        scheduler.run();

        AutomationStatus status =
                scheduler.status();

        assertTrue(status.lastRunSucceeded());
        assertNotNull(status.lastRunAt());
        assertEquals(3, status.lastPointCount());
        assertEquals(1, status.lastNewAlertCount());
        assertTrue(status.enabled());
        assertTrue(status.evaluatesAlerts());
    }

    @Test
    void announcesFreshFeedsEvenWhenNoAlertTriggers() {
        alerts.newlyTriggered = 0;

        scheduler(true, true, "PT2M").run();

        assertEquals(1, alerts.calls);
        assertEquals(1, broadcaster.published.size());
        assertEquals("feeds", broadcaster.names.get(0));
    }

    @Test
    void skipsEvaluationWhenNoFeedIsLive() {
        earthquakes.response = quakes(false);
        volcanoes.response = volcanoes(false);
        naturalHazards.wildfires = natural("wildfires", false);
        naturalHazards.storms = natural("severe-storms", false);

        FeedIngestionScheduler scheduler =
                scheduler(true, true, "PT2M");

        scheduler.run();

        assertEquals(0, alerts.calls);
        assertEquals(0, broadcaster.published.size());
        assertTrue(scheduler.status().lastRunSucceeded());
        assertEquals(0, scheduler.status().lastPointCount());
    }

    @Test
    void keepsGoingWhenOneFeedFails() {
        earthquakes.fail = true;

        FeedIngestionScheduler scheduler =
                scheduler(true, true, "PT2M");

        scheduler.run();

        assertEquals(1, alerts.calls);
        assertEquals(2, alerts.lastRequest.points().size());
        assertTrue(scheduler.status().lastRunSucceeded());
    }

    @Test
    void recordsAFailedEvaluationWithoutThrowing() {
        alerts.fail = true;

        FeedIngestionScheduler scheduler =
                scheduler(true, true, "PT2M");

        scheduler.run();

        AutomationStatus status =
                scheduler.status();

        assertFalse(status.lastRunSucceeded());
        assertEquals(0, status.lastNewAlertCount());
        assertEquals(0, broadcaster.published.size());
    }

    @Test
    void scheduledRunDoesNothingWhenDisabled() {
        FeedIngestionScheduler scheduler =
                scheduler(false, true, "PT2M");

        scheduler.runScheduled();

        assertEquals(0, earthquakes.calls);
        assertEquals(0, alerts.calls);
        assertFalse(scheduler.status().enabled());
    }

    @Test
    void refreshesFeedsWithoutEvaluatingWhenAlertsAreOff() {
        FeedIngestionScheduler scheduler =
                scheduler(true, false, "PT2M");

        scheduler.run();

        assertTrue(earthquakes.calls > 0);
        assertEquals(0, alerts.calls);
        assertFalse(scheduler.status().evaluatesAlerts());
    }

    @Test
    void fallsBackToTwoMinutesForABadInterval() {
        assertEquals(
                120L,
                scheduler(true, true, "garbage")
                        .status()
                        .intervalSeconds()
        );

        assertEquals(
                120L,
                scheduler(true, true, "PT0S")
                        .status()
                        .intervalSeconds()
        );

        assertEquals(
                30L,
                scheduler(true, true, "PT30S")
                        .status()
                        .intervalSeconds()
        );
    }

    @Test
    void reportsConnectedClients() {
        broadcaster.clients = 3;

        assertEquals(
                3,
                scheduler(true, true, "PT2M")
                        .status()
                        .connectedClients()
        );
    }

    private FeedIngestionScheduler scheduler(
            boolean enabled,
            boolean evaluateAlerts,
            String interval
    ) {
        return new FeedIngestionScheduler(
                earthquakes,
                volcanoes,
                naturalHazards,
                alerts,
                broadcaster,
                enabled,
                evaluateAlerts,
                interval
        );
    }

    private static final class FakeEarthquakes
            extends EarthquakeService {

        EarthquakeFeedResponse response =
                quakes(true, quake("eq-1", 5.4, 35.2, 24.9));

        boolean fail;
        int calls;

        FakeEarthquakes() {
            super(null, null);
        }

        @Override
        public EarthquakeFeedResponse query(
                EarthquakePeriod period,
                double minimumMagnitude,
                double maximumDepthKm,
                int limit,
                String search,
                EarthquakeSort sort
        ) {
            calls++;

            if (fail) {
                throw new IllegalStateException("USGS down");
            }

            return response;
        }
    }

    private static final class FakeVolcanoes
            extends VolcanoService {

        VolcanoFeedResponse response =
                volcanoes(true, volcano("vo-1", "Etna", 37.7, 15.0));

        FakeVolcanoes() {
            super(null, null);
        }

        @Override
        public VolcanoFeedResponse query(
                String requestedStatus,
                int days,
                int limit,
                String search
        ) {
            return response;
        }
    }

    private static final class FakeNaturalHazards
            extends NaturalHazardService {

        NaturalHazardFeedResponse wildfires =
                natural(
                        "wildfires",
                        true,
                        naturalEvent("wf-1", "wildfires", 40.6, -122.4, 1500.0)
                );

        NaturalHazardFeedResponse storms =
                natural("severe-storms", true);

        FakeNaturalHazards() {
            super(null, null);
        }

        @Override
        public NaturalHazardFeedResponse query(
                HazardType hazardType,
                String requestedStatus,
                int days,
                int limit,
                String search
        ) {
            return hazardType == HazardType.WILDFIRE
                    ? wildfires
                    : storms;
        }
    }

    private static final class FakeAlerts
            extends HazardAlertService {

        EvaluateAlertRulesRequest lastRequest;
        int newlyTriggered;
        int newAlertCount;
        boolean fail;
        int calls;

        FakeAlerts() {
            super(null, null);
        }

        @Override
        public AlertEvaluationResponse evaluate(
                EvaluateAlertRulesRequest request
        ) {
            calls++;

            if (fail) {
                throw new IllegalStateException("database down");
            }

            lastRequest = request;

            return new AlertEvaluationResponse(
                    1,
                    request.points().size(),
                    newlyTriggered,
                    new AlertDashboardResponse(
                            1,
                            1,
                            newAlertCount,
                            newAlertCount,
                            0,
                            List.of(),
                            List.of()
                    )
            );
        }
    }

    private static final class RecordingBroadcaster
            extends LiveEventBroadcaster {

        final List<String> names = new ArrayList<>();
        final List<Object> published = new ArrayList<>();
        int clients;

        @Override
        public void publish(
                String eventName,
                Object payload
        ) {
            names.add(eventName);
            published.add(payload);
        }

        @Override
        public int clientCount() {
            return clients;
        }
    }

    private static EarthquakeFeedResponse quakes(
            boolean live,
            EarthquakeEvent... events
    ) {
        return new EarthquakeFeedResponse(
                "USGS",
                live,
                NOW,
                "week",
                null,
                events.length,
                events.length,
                events.length,
                null,
                List.of(events)
        );
    }

    private static VolcanoFeedResponse volcanoes(
            boolean live,
            VolcanoEvent... events
    ) {
        return new VolcanoFeedResponse(
                "NASA_EONET",
                live,
                NOW,
                "open",
                3650,
                "",
                events.length,
                events.length,
                events.length,
                null,
                List.of(events)
        );
    }

    private static NaturalHazardFeedResponse natural(
            String hazardType,
            boolean live,
            NaturalHazardEvent... events
    ) {
        return new NaturalHazardFeedResponse(
                hazardType,
                "NASA_EONET",
                live,
                NOW,
                "open",
                365,
                "",
                events.length,
                events.length,
                events.length,
                null,
                List.of(events)
        );
    }

    private static EarthquakeEvent quake(
            String id,
            double magnitude,
            double latitude,
            double longitude
    ) {
        return new EarthquakeEvent(
                id,
                magnitude,
                "Somewhere",
                NOW,
                NOW,
                longitude,
                latitude,
                10.0,
                100,
                null,
                false,
                "none",
                "reviewed",
                ""
        );
    }

    private static VolcanoEvent volcano(
            String id,
            String title,
            double latitude,
            double longitude
    ) {
        return new VolcanoEvent(
                id,
                title,
                "",
                NOW,
                longitude,
                latitude,
                1,
                1,
                "open",
                "SIVolcano",
                "",
                ""
        );
    }

    private static NaturalHazardEvent naturalEvent(
            String id,
            String hazardType,
            double latitude,
            double longitude,
            Double magnitudeValue
    ) {
        return new NaturalHazardEvent(
                id,
                hazardType,
                "Event " + id,
                "",
                NOW,
                longitude,
                latitude,
                1,
                1,
                "open",
                "IRWIN",
                "",
                "",
                magnitudeValue,
                "acres",
                "Burned area"
        );
    }
}
