package com.terrascope.platform.service;

import com.terrascope.platform.api.model.EarthquakeEvent;
import com.terrascope.platform.api.model.EarthquakeFeedResponse;
import com.terrascope.platform.domain.EarthquakePeriod;
import com.terrascope.platform.domain.EarthquakeSort;
import com.terrascope.platform.infrastructure.usgs.DisasterSourceUnavailableException;
import com.terrascope.platform.infrastructure.usgs.UsgsEarthquakeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EarthquakeServiceTest {

    private UsgsEarthquakeClient client;
    private DemoEarthquakeFallback fallback;
    private EarthquakeService service;

    @BeforeEach
    void setUp() {
        client = mock(UsgsEarthquakeClient.class);
        fallback = mock(DemoEarthquakeFallback.class);

        service = new EarthquakeService(
                client,
                fallback
        );
    }

    @Test
    void filtersSortsAndSummarizesLiveEvents() {
        List<EarthquakeEvent> events = List.of(
                event(
                        "a",
                        4.2,
                        "Near Japan",
                        40.0,
                        Instant.parse("2026-09-17T08:00:00Z")
                ),
                event(
                        "b",
                        6.1,
                        "Near Japan",
                        25.0,
                        Instant.parse("2026-09-17T09:00:00Z")
                ),
                event(
                        "c",
                        5.2,
                        "Near Chile",
                        18.0,
                        Instant.parse("2026-09-17T10:00:00Z")
                )
        );

        when(
                client.fetch(
                        EarthquakePeriod.DAY,
                        4.0
                )
        ).thenReturn(events);

        EarthquakeFeedResponse result =
                service.query(
                        EarthquakePeriod.DAY,
                        4.0,
                        100.0,
                        20,
                        "Japan",
                        EarthquakeSort.MAGNITUDE
                );

        assertThat(result.live()).isTrue();
        assertThat(result.source()).isEqualTo("USGS");
        assertThat(result.sourceEventCount()).isEqualTo(3);
        assertThat(result.matchedEventCount()).isEqualTo(2);
        assertThat(result.returnedEventCount()).isEqualTo(2);
        assertThat(result.events().getFirst().id()).isEqualTo("b");
        assertThat(result.summary().strongestMagnitude()).isEqualTo(6.1);
        assertThat(result.summary().eventCount()).isEqualTo(2);
    }

    @Test
    void usesDemoFallbackWhenUsgsIsUnavailable() {
        when(
                client.fetch(
                        EarthquakePeriod.DAY,
                        2.5
                )
        ).thenThrow(
                new DisasterSourceUnavailableException(
                        "unavailable",
                        new RuntimeException("network")
                )
        );

        List<EarthquakeEvent> fallbackEvents =
                List.of(
                        event(
                                "demo",
                                5.4,
                                "Demo Pacific",
                                10.0,
                                Instant.parse("2026-09-17T09:00:00Z")
                        )
                );

        when(fallback.create())
                .thenReturn(fallbackEvents);

        EarthquakeFeedResponse result =
                service.query(
                        EarthquakePeriod.DAY,
                        2.5,
                        1000.0,
                        100,
                        "",
                        EarthquakeSort.TIME
                );

        assertThat(result.live()).isFalse();
        assertThat(result.source())
                .isEqualTo("DEMO_FALLBACK");
        assertThat(result.events())
                .hasSize(1);
        assertThat(result.events().getFirst().id())
                .isEqualTo("demo");
    }

    private EarthquakeEvent event(
            String id,
            double magnitude,
            String place,
            double depth,
            Instant occurredAt
    ) {
        return new EarthquakeEvent(
                id,
                magnitude,
                place,
                occurredAt,
                occurredAt,
                10.0,
                20.0,
                depth,
                magnitude >= 5.0 ? 650 : 250,
                null,
                false,
                "none",
                "reviewed",
                ""
        );
    }
}
