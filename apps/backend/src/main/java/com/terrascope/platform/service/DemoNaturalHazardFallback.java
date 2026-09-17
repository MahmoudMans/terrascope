package com.terrascope.platform.service;

import com.terrascope.platform.api.model.NaturalHazardEvent;
import com.terrascope.platform.domain.HazardType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DemoNaturalHazardFallback {

    public List<NaturalHazardEvent> create(
            HazardType hazardType
    ) {
        return switch (hazardType) {
            case WILDFIRE ->
                    wildfireEvents();

            case SEVERE_STORM ->
                    stormEvents();

            default ->
                    throw new IllegalArgumentException(
                            "No generic fallback for "
                                    + hazardType
                    );
        };
    }

    private List<NaturalHazardEvent> wildfireEvents() {
        Instant now =
                Instant.now();

        return List.of(
                event(
                        "demo-fire-001",
                        HazardType.WILDFIRE,
                        "Demo wildfire — Western North America",
                        now.minus(
                                3,
                                ChronoUnit.HOURS
                        ),
                        -119.4,
                        37.1,
                        4,
                        null,
                        "",
                        ""
                ),
                event(
                        "demo-fire-002",
                        HazardType.WILDFIRE,
                        "Demo wildfire — Mediterranean",
                        now.minus(
                                8,
                                ChronoUnit.HOURS
                        ),
                        23.7,
                        38.2,
                        3,
                        null,
                        "",
                        ""
                ),
                event(
                        "demo-fire-003",
                        HazardType.WILDFIRE,
                        "Demo wildfire — Southern Hemisphere",
                        now.minus(
                                1,
                                ChronoUnit.DAYS
                        ),
                        147.1,
                        -36.4,
                        2,
                        null,
                        "",
                        ""
                )
        );
    }

    private List<NaturalHazardEvent> stormEvents() {
        Instant now =
                Instant.now();

        return List.of(
                event(
                        "demo-storm-001",
                        HazardType.SEVERE_STORM,
                        "Demo severe storm — North Atlantic",
                        now.minus(
                                5,
                                ChronoUnit.HOURS
                        ),
                        -48.7,
                        28.1,
                        6,
                        65.0,
                        "kts",
                        "Maximum sustained winds"
                ),
                event(
                        "demo-storm-002",
                        HazardType.SEVERE_STORM,
                        "Demo severe storm — Western Pacific",
                        now.minus(
                                12,
                                ChronoUnit.HOURS
                        ),
                        132.5,
                        17.8,
                        5,
                        80.0,
                        "kts",
                        "Maximum sustained winds"
                ),
                event(
                        "demo-storm-003",
                        HazardType.SEVERE_STORM,
                        "Demo severe storm — Indian Ocean",
                        now.minus(
                                1,
                                ChronoUnit.DAYS
                        ),
                        79.1,
                        -15.7,
                        4,
                        55.0,
                        "kts",
                        "Maximum sustained winds"
                )
        );
    }

    private NaturalHazardEvent event(
            String id,
            HazardType hazardType,
            String title,
            Instant observedAt,
            double longitude,
            double latitude,
            int observationCount,
            Double magnitudeValue,
            String magnitudeUnit,
            String magnitudeDescription
    ) {
        return new NaturalHazardEvent(
                id,
                hazardType.key(),
                title,
                "Local demonstration event used when "
                        + "NASA EONET cannot be reached.",
                observedAt,
                longitude,
                latitude,
                observationCount,
                1,
                "open",
                "DEMO",
                "",
                "",
                magnitudeValue,
                magnitudeUnit,
                magnitudeDescription
        );
    }
}
