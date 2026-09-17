package com.terrascope.platform.service;

import com.terrascope.platform.api.model.VolcanoEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DemoVolcanoFallback {

    public List<VolcanoEvent> create() {
        Instant now = Instant.now();

        return List.of(
                event(
                        "demo-volcano-001",
                        "Demo event — North Pacific Arc",
                        now.minus(
                                5,
                                ChronoUnit.HOURS
                        ),
                        152.4,
                        46.1,
                        4
                ),
                event(
                        "demo-volcano-002",
                        "Demo event — Central America",
                        now.minus(
                                14,
                                ChronoUnit.HOURS
                        ),
                        -90.8,
                        14.5,
                        3
                ),
                event(
                        "demo-volcano-003",
                        "Demo event — Southwest Pacific",
                        now.minus(
                                1,
                                ChronoUnit.DAYS
                        ),
                        167.8,
                        -16.3,
                        2
                ),
                event(
                        "demo-volcano-004",
                        "Demo event — Southeast Asia",
                        now.minus(
                                2,
                                ChronoUnit.DAYS
                        ),
                        110.4,
                        -7.5,
                        5
                ),
                event(
                        "demo-volcano-005",
                        "Demo event — Northern Andes",
                        now.minus(
                                3,
                                ChronoUnit.DAYS
                        ),
                        -77.3,
                        0.1,
                        2
                ),
                event(
                        "demo-volcano-006",
                        "Demo event — Mediterranean",
                        now.minus(
                                4,
                                ChronoUnit.DAYS
                        ),
                        15.0,
                        37.7,
                        3
                )
        );
    }

    private VolcanoEvent event(
            String id,
            String title,
            Instant observedAt,
            double longitude,
            double latitude,
            int observations
    ) {
        return new VolcanoEvent(
                id,
                title,
                "Local demonstration event used "
                        + "when NASA EONET cannot "
                        + "be reached.",
                observedAt,
                longitude,
                latitude,
                observations,
                1,
                "open",
                "DEMO",
                "",
                ""
        );
    }
}
