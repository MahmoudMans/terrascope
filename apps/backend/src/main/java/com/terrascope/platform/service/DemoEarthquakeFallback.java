package com.terrascope.platform.service;

import com.terrascope.platform.api.model.EarthquakeEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DemoEarthquakeFallback {

    public List<EarthquakeEvent> create() {
        Instant now = Instant.now();

        return List.of(
                event(
                        "demo-001",
                        6.2,
                        "Demo event — North Pacific",
                        now.minus(18, ChronoUnit.MINUTES),
                        145.82,
                        38.12,
                        32.0,
                        650,
                        true,
                        "yellow"
                ),
                event(
                        "demo-002",
                        5.4,
                        "Demo event — Eastern Mediterranean",
                        now.minus(51, ChronoUnit.MINUTES),
                        28.74,
                        35.47,
                        18.4,
                        470,
                        false,
                        "green"
                ),
                event(
                        "demo-003",
                        4.8,
                        "Demo event — South America",
                        now.minus(3, ChronoUnit.HOURS),
                        -71.31,
                        -22.45,
                        76.2,
                        390,
                        false,
                        "none"
                ),
                event(
                        "demo-004",
                        4.2,
                        "Demo event — Central Asia",
                        now.minus(7, ChronoUnit.HOURS),
                        72.54,
                        40.16,
                        11.7,
                        310,
                        false,
                        "none"
                ),
                event(
                        "demo-005",
                        3.7,
                        "Demo event — North Atlantic",
                        now.minus(11, ChronoUnit.HOURS),
                        -29.70,
                        37.80,
                        8.4,
                        245,
                        false,
                        "none"
                ),
                event(
                        "demo-006",
                        5.8,
                        "Demo event — Western Pacific",
                        now.minus(18, ChronoUnit.HOURS),
                        126.18,
                        8.53,
                        48.1,
                        540,
                        true,
                        "green"
                ),
                event(
                        "demo-007",
                        3.2,
                        "Demo event — Southern Europe",
                        now.minus(22, ChronoUnit.HOURS),
                        14.62,
                        38.32,
                        13.9,
                        190,
                        false,
                        "none"
                ),
                event(
                        "demo-008",
                        4.6,
                        "Demo event — Indian Ocean",
                        now.minus(28, ChronoUnit.HOURS),
                        93.40,
                        -4.72,
                        29.5,
                        355,
                        false,
                        "none"
                )
        );
    }

    private EarthquakeEvent event(
            String id,
            double magnitude,
            String place,
            Instant occurredAt,
            double longitude,
            double latitude,
            double depthKm,
            int significance,
            boolean tsunami,
            String alertLevel
    ) {
        return new EarthquakeEvent(
                id,
                magnitude,
                place,
                occurredAt,
                occurredAt,
                longitude,
                latitude,
                depthKm,
                significance,
                null,
                tsunami,
                alertLevel,
                "demo",
                ""
        );
    }
}
