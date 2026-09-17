package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AlertRule;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HazardAlertEngineTest {

    private final HazardAlertEngine engine =
            new HazardAlertEngine(
                    new RegionalIntelligenceEngine()
            );

    @Test
    void triggersMatchingNearbyEvent() {
        assertThat(
                engine.evaluate(
                        rule(
                                "earthquakes",
                                100.0,
                                4.0,
                                true
                        ),
                        point(
                                "earthquakes",
                                36.81,
                                10.19,
                                5.5
                        )
                )
        ).isPresent();
    }

    @Test
    void rejectsOutsideRadius() {
        assertThat(
                engine.evaluate(
                        rule(
                                "earthquakes",
                                10.0,
                                0.0,
                                true
                        ),
                        point(
                                "earthquakes",
                                38.0,
                                12.0,
                                6.0
                        )
                )
        ).isEmpty();
    }

    @Test
    void rejectsBelowIntensityThreshold() {
        assertThat(
                engine.evaluate(
                        rule(
                                "earthquakes",
                                500.0,
                                6.0,
                                true
                        ),
                        point(
                                "earthquakes",
                                36.9,
                                10.3,
                                4.5
                        )
                )
        ).isEmpty();
    }

    @Test
    void allHazardsRuleMatchesHazardWithoutIntensity() {
        assertThat(
                engine.evaluate(
                        rule(
                                "all",
                                500.0,
                                0.0,
                                true
                        ),
                        point(
                                "wildfires",
                                36.9,
                                10.3,
                                null
                        )
                )
        ).isPresent();
    }

    private AlertRule rule(
            String hazardType,
            double radiusKm,
            double minimumIntensity,
            boolean enabled
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new AlertRule(
                UUID.randomUUID(),
                "Test rule",
                hazardType,
                36.8065,
                10.1815,
                radiusKm,
                minimumIntensity,
                enabled,
                now,
                now
        );
    }

    private RegionalHazardPoint point(
            String hazardType,
            double latitude,
            double longitude,
            Double intensity
    ) {
        return new RegionalHazardPoint(
                "source-1",
                hazardType,
                "Test event",
                latitude,
                longitude,
                intensity,
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                )
        );
    }
}
