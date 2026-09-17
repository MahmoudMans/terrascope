package com.terrascope.platform.service;

import com.terrascope.platform.api.model.RegionalAnalysisRequest;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionalIntelligenceEngineTest {

    private final RegionalIntelligenceEngine engine =
            new RegionalIntelligenceEngine();

    @Test
    void excludesEventsOutsideRadius() {
        RegionalIntelligenceEngine.Calculation result =
                engine.calculate(
                        request(
                                100.0,
                                List.of(
                                        point(
                                                "near",
                                                "earthquakes",
                                                36.9,
                                                10.2,
                                                6.0
                                        ),
                                        point(
                                                "far",
                                                "wildfires",
                                                48.8,
                                                2.3,
                                                null
                                        )
                                )
                        )
                );

        assertThat(
                result.sourcePointCount()
        ).isEqualTo(2);

        assertThat(
                result.matchedEventCount()
        ).isEqualTo(1);

        assertThat(
                result.breakdown()
                        .earthquakeCount()
        ).isEqualTo(1);

        assertThat(
                result.breakdown()
                        .wildfireCount()
        ).isZero();
    }

    @Test
    void identifiesNearestEvent() {
        RegionalIntelligenceEngine.Calculation result =
                engine.calculate(
                        request(
                                500.0,
                                List.of(
                                        point(
                                                "closer",
                                                "wildfires",
                                                36.82,
                                                10.19,
                                                null
                                        ),
                                        point(
                                                "further",
                                                "severe-storms",
                                                37.5,
                                                10.9,
                                                80.0
                                        )
                                )
                        )
                );

        assertThat(
                result.nearestEvent()
                        .id()
        ).isEqualTo(
                "closer"
        );

        assertThat(
                result.nearestEventDistanceKm()
        ).isLessThan(
                10.0
        );
    }

    @Test
    void strongerNearbyActivityRaisesIndex() {
        RegionalIntelligenceEngine.Calculation low =
                engine.calculate(
                        request(
                                300.0,
                                List.of(
                                        point(
                                                "eq",
                                                "earthquakes",
                                                36.9,
                                                10.2,
                                                2.5
                                        )
                                )
                        )
                );

        RegionalIntelligenceEngine.Calculation high =
                engine.calculate(
                        request(
                                300.0,
                                List.of(
                                        point(
                                                "eq",
                                                "earthquakes",
                                                36.9,
                                                10.2,
                                                7.5
                                        ),
                                        point(
                                                "storm",
                                                "severe-storms",
                                                36.95,
                                                10.25,
                                                100.0
                                        )
                                )
                        )
                );

        assertThat(
                high.activityIndex()
        ).isGreaterThan(
                low.activityIndex()
        );
    }

    @Test
    void countsAllHazardFamilies() {
        RegionalIntelligenceEngine.Calculation result =
                engine.calculate(
                        request(
                                1000.0,
                                List.of(
                                        point(
                                                "eq",
                                                "earthquakes",
                                                36.9,
                                                10.2,
                                                5.0
                                        ),
                                        point(
                                                "volcano",
                                                "volcanoes",
                                                37.2,
                                                10.1,
                                                null
                                        ),
                                        point(
                                                "fire",
                                                "wildfires",
                                                36.7,
                                                9.9,
                                                null
                                        ),
                                        point(
                                                "storm",
                                                "severe-storms",
                                                36.5,
                                                10.4,
                                                80.0
                                        )
                                )
                        )
                );

        assertThat(
                result.breakdown()
                        .earthquakeCount()
        ).isEqualTo(1);

        assertThat(
                result.breakdown()
                        .volcanoCount()
        ).isEqualTo(1);

        assertThat(
                result.breakdown()
                        .wildfireCount()
        ).isEqualTo(1);

        assertThat(
                result.breakdown()
                        .severeStormCount()
        ).isEqualTo(1);
    }

    private RegionalAnalysisRequest request(
            double radiusKm,
            List<RegionalHazardPoint> points
    ) {
        return new RegionalAnalysisRequest(
                "Test region",
                36.8065,
                10.1815,
                radiusKm,
                points
        );
    }

    private RegionalHazardPoint point(
            String id,
            String hazardType,
            double latitude,
            double longitude,
            Double intensity
    ) {
        return new RegionalHazardPoint(
                id,
                hazardType,
                id,
                latitude,
                longitude,
                intensity,
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                )
        );
    }
}
