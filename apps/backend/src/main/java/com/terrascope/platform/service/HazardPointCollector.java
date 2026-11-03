package com.terrascope.platform.service;

import com.terrascope.platform.api.model.EarthquakeEvent;
import com.terrascope.platform.api.model.EarthquakeFeedResponse;
import com.terrascope.platform.api.model.NaturalHazardEvent;
import com.terrascope.platform.api.model.NaturalHazardFeedResponse;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import com.terrascope.platform.api.model.VolcanoEvent;
import com.terrascope.platform.api.model.VolcanoFeedResponse;
import com.terrascope.platform.domain.HazardType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class HazardPointCollector {

    public static final int MAX_POINTS = 2000;

    private static final int MAX_ID_LENGTH = 200;
    private static final int MAX_TITLE_LENGTH = 400;

    private HazardPointCollector() {
    }

    public static List<RegionalHazardPoint> collect(
            EarthquakeFeedResponse earthquakes,
            VolcanoFeedResponse volcanoes,
            NaturalHazardFeedResponse wildfires,
            NaturalHazardFeedResponse storms
    ) {
        List<RegionalHazardPoint> points =
                new ArrayList<>();

        if (
                earthquakes != null
                && earthquakes.live()
                && earthquakes.events() != null
        ) {
            for (EarthquakeEvent event : earthquakes.events()) {
                add(
                        points,
                        event.id(),
                        HazardType.EARTHQUAKE,
                        event.place(),
                        event.latitude(),
                        event.longitude(),
                        event.magnitude(),
                        event.occurredAt()
                );
            }
        }

        if (
                volcanoes != null
                && volcanoes.live()
                && volcanoes.events() != null
        ) {
            for (VolcanoEvent event : volcanoes.events()) {
                add(
                        points,
                        event.id(),
                        HazardType.VOLCANO,
                        event.title(),
                        event.latitude(),
                        event.longitude(),
                        null,
                        event.observedAt()
                );
            }
        }

        addNatural(
                points,
                wildfires,
                HazardType.WILDFIRE
        );

        addNatural(
                points,
                storms,
                HazardType.SEVERE_STORM
        );

        if (points.size() > MAX_POINTS) {
            return List.copyOf(
                    points.subList(
                            0,
                            MAX_POINTS
                    )
            );
        }

        return List.copyOf(points);
    }

    private static void addNatural(
            List<RegionalHazardPoint> points,
            NaturalHazardFeedResponse feed,
            HazardType hazardType
    ) {
        if (
                feed == null
                || !feed.live()
                || feed.events() == null
        ) {
            return;
        }

        for (NaturalHazardEvent event : feed.events()) {
            add(
                    points,
                    event.id(),
                    hazardType,
                    event.title(),
                    event.latitude(),
                    event.longitude(),
                    event.magnitudeValue(),
                    event.observedAt()
            );
        }
    }

    private static void add(
            List<RegionalHazardPoint> points,
            String id,
            HazardType hazardType,
            String title,
            double latitude,
            double longitude,
            Double intensity,
            Instant observedAt
    ) {
        if (
                id == null
                || id.isBlank()
                || id.length() > MAX_ID_LENGTH
                || observedAt == null
                || !inRange(latitude, -90.0, 90.0)
                || !inRange(longitude, -180.0, 180.0)
        ) {
            return;
        }

        points.add(
                new RegionalHazardPoint(
                        id,
                        hazardType.key(),
                        titleOf(title),
                        latitude,
                        longitude,
                        finiteOrNull(intensity),
                        observedAt
                )
        );
    }

    private static boolean inRange(
            double value,
            double minimum,
            double maximum
    ) {
        return !Double.isNaN(value)
                && value >= minimum
                && value <= maximum;
    }

    private static Double finiteOrNull(
            Double value
    ) {
        if (
                value == null
                || value.isNaN()
                || value.isInfinite()
        ) {
            return null;
        }

        return value;
    }

    private static String titleOf(
            String title
    ) {
        if (
                title == null
                || title.isBlank()
        ) {
            return "Untitled event";
        }

        String trimmed =
                title.trim();

        if (trimmed.length() <= MAX_TITLE_LENGTH) {
            return trimmed;
        }

        return trimmed.substring(
                0,
                MAX_TITLE_LENGTH
        );
    }
}
