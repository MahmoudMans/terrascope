package com.terrascope.platform.service;

import com.terrascope.platform.api.model.EarthquakeEvent;
import com.terrascope.platform.api.model.EarthquakeFeedResponse;
import com.terrascope.platform.api.model.NaturalHazardEvent;
import com.terrascope.platform.api.model.NaturalHazardFeedResponse;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import com.terrascope.platform.api.model.VolcanoEvent;
import com.terrascope.platform.api.model.VolcanoFeedResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazardPointCollectorTest {

    private static final Instant NOW =
            Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void mapsEveryFeedToPoints() {
        List<RegionalHazardPoint> points =
                HazardPointCollector.collect(
                        quakes(true, quake("eq-1", 5.4, 35.2, 24.9)),
                        volcanoes(true, volcano("vo-1", "Etna", 37.7, 15.0)),
                        naturalFeed("wildfires", true, naturalEvent("wf-1", "wildfires", "Fire", 40.6, -122.4, 1500.0)),
                        naturalFeed("severe-storms", true, naturalEvent("st-1", "severe-storms", "Storm", 18.4, -62.0, null))
                );

        assertEquals(4, points.size());

        assertEquals("earthquakes", points.get(0).hazardType());
        assertEquals("eq-1", points.get(0).id());
        assertEquals(5.4, points.get(0).intensity());

        assertEquals("volcanoes", points.get(1).hazardType());
        assertNull(points.get(1).intensity());

        assertEquals("wildfires", points.get(2).hazardType());
        assertEquals(1500.0, points.get(2).intensity());

        assertEquals("severe-storms", points.get(3).hazardType());
        assertNull(points.get(3).intensity());
    }

    @Test
    void ignoresFeedsThatAreNotLive() {
        List<RegionalHazardPoint> points =
                HazardPointCollector.collect(
                        quakes(false, quake("eq-1", 5.4, 35.2, 24.9)),
                        volcanoes(false, volcano("vo-1", "Etna", 37.7, 15.0)),
                        naturalFeed("wildfires", false, naturalEvent("wf-1", "wildfires", "Fire", 40.6, -122.4, 10.0)),
                        naturalFeed("severe-storms", true, naturalEvent("st-1", "severe-storms", "Storm", 18.4, -62.0, 50.0))
                );

        assertEquals(1, points.size());
        assertEquals("st-1", points.get(0).id());
    }

    @Test
    void handlesMissingFeeds() {
        assertTrue(
                HazardPointCollector
                        .collect(null, null, null, null)
                        .isEmpty()
        );
    }

    @Test
    void dropsEventsThatWouldFailValidation() {
        List<RegionalHazardPoint> points =
                HazardPointCollector.collect(
                        quakes(
                                true,
                                quake("", 4.0, 10.0, 10.0),
                                quake("eq-bad-lat", 4.0, 91.0, 10.0),
                                quake("eq-bad-lon", 4.0, 10.0, Double.NaN),
                                quake("x".repeat(201), 4.0, 10.0, 10.0),
                                quake("eq-ok", 4.0, 10.0, 10.0)
                        ),
                        null,
                        null,
                        null
                );

        assertEquals(1, points.size());
        assertEquals("eq-ok", points.get(0).id());
    }

    @Test
    void normalisesTitles() {
        List<RegionalHazardPoint> points =
                HazardPointCollector.collect(
                        null,
                        volcanoes(
                                true,
                                volcano("vo-1", "   ", 10.0, 10.0),
                                volcano("vo-2", "y".repeat(500), 10.0, 10.0),
                                volcano("vo-3", "  Padded  ", 10.0, 10.0)
                        ),
                        null,
                        null
                );

        assertEquals("Untitled event", points.get(0).title());
        assertEquals(400, points.get(1).title().length());
        assertEquals("Padded", points.get(2).title());
    }

    @Test
    void capsTheNumberOfPoints() {
        List<EarthquakeEvent> events =
                new ArrayList<>();

        for (int index = 0; index < 2100; index++) {
            events.add(
                    quake("eq-" + index, 3.0, 10.0, 10.0)
            );
        }

        List<RegionalHazardPoint> points =
                HazardPointCollector.collect(
                        quakes(true, events.toArray(new EarthquakeEvent[0])),
                        null,
                        null,
                        null
                );

        assertEquals(HazardPointCollector.MAX_POINTS, points.size());
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

    private static NaturalHazardFeedResponse naturalFeed(
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
            String title,
            double latitude,
            double longitude,
            Double magnitudeValue
    ) {
        return new NaturalHazardEvent(
                id,
                hazardType,
                title,
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
