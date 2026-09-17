package com.terrascope.platform.service;

import com.terrascope.platform.api.model.NaturalHazardEvent;
import com.terrascope.platform.api.model.NaturalHazardFeedResponse;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.infrastructure.eonet.EonetSourceUnavailableException;
import com.terrascope.platform.infrastructure.eonet.NasaEonetClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NaturalHazardServiceTest {

    private NasaEonetClient client;
    private DemoNaturalHazardFallback fallback;
    private NaturalHazardService service;

    @BeforeEach
    void setUp() {
        client =
                mock(
                        NasaEonetClient.class
                );

        fallback =
                mock(
                        DemoNaturalHazardFallback.class
                );

        service =
                new NaturalHazardService(
                        client,
                        fallback
                );
    }

    @Test
    void filtersAndSummarizesWildfires() {
        List<NaturalHazardEvent> events =
                List.of(
                        event(
                                "fire-a",
                                HazardType.WILDFIRE,
                                "California wildfire",
                                Instant.parse(
                                        "2026-09-17T10:00:00Z"
                                ),
                                null
                        ),
                        event(
                                "fire-b",
                                HazardType.WILDFIRE,
                                "Mediterranean wildfire",
                                Instant.parse(
                                        "2026-09-17T11:00:00Z"
                                ),
                                null
                        )
                );

        when(
                client.fetchNaturalHazardEvents(
                        HazardType.WILDFIRE,
                        "open",
                        90
                )
        ).thenReturn(
                events
        );

        NaturalHazardFeedResponse result =
                service.query(
                        HazardType.WILDFIRE,
                        "open",
                        90,
                        20,
                        "California"
                );

        assertThat(
                result.live()
        ).isTrue();

        assertThat(
                result.source()
        ).isEqualTo(
                "NASA_EONET"
        );

        assertThat(
                result.hazardType()
        ).isEqualTo(
                "wildfires"
        );

        assertThat(
                result.matchedEventCount()
        ).isEqualTo(1);

        assertThat(
                result.events()
        ).hasSize(1);

        assertThat(
                result.events().getFirst().id()
        ).isEqualTo(
                "fire-a"
        );
    }

    @Test
    void summarizesStormMagnitudeEvents() {
        List<NaturalHazardEvent> events =
                List.of(
                        event(
                                "storm-a",
                                HazardType.SEVERE_STORM,
                                "Storm Alpha",
                                Instant.parse(
                                        "2026-09-17T09:00:00Z"
                                ),
                                80.0
                        ),
                        event(
                                "storm-b",
                                HazardType.SEVERE_STORM,
                                "Storm Beta",
                                Instant.parse(
                                        "2026-09-17T12:00:00Z"
                                ),
                                null
                        )
                );

        when(
                client.fetchNaturalHazardEvents(
                        HazardType.SEVERE_STORM,
                        "open",
                        90
                )
        ).thenReturn(
                events
        );

        NaturalHazardFeedResponse result =
                service.query(
                        HazardType.SEVERE_STORM,
                        "open",
                        90,
                        20,
                        ""
                );

        assertThat(
                result.summary().eventCount()
        ).isEqualTo(2);

        assertThat(
                result.summary().magnitudeEventCount()
        ).isEqualTo(1);

        assertThat(
                result.events().getFirst().id()
        ).isEqualTo(
                "storm-b"
        );
    }

    @Test
    void usesFallbackWhenEonetFails() {
        when(
                client.fetchNaturalHazardEvents(
                        HazardType.WILDFIRE,
                        "open",
                        90
                )
        ).thenThrow(
                new EonetSourceUnavailableException(
                        "offline",
                        new RuntimeException(
                                "network"
                        )
                )
        );

        when(
                fallback.create(
                        HazardType.WILDFIRE
                )
        ).thenReturn(
                List.of(
                        event(
                                "demo",
                                HazardType.WILDFIRE,
                                "Demo wildfire",
                                Instant.parse(
                                        "2026-09-17T12:00:00Z"
                                ),
                                null
                        )
                )
        );

        NaturalHazardFeedResponse result =
                service.query(
                        HazardType.WILDFIRE,
                        "open",
                        90,
                        10,
                        ""
                );

        assertThat(
                result.live()
        ).isFalse();

        assertThat(
                result.source()
        ).isEqualTo(
                "DEMO_FALLBACK"
        );
    }

    @Test
    void rejectsUnsupportedGenericType() {
        assertThatThrownBy(() ->
                service.query(
                        HazardType.EARTHQUAKE,
                        "open",
                        90,
                        10,
                        ""
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    private NaturalHazardEvent event(
            String id,
            HazardType hazardType,
            String title,
            Instant observedAt,
            Double magnitude
    ) {
        return new NaturalHazardEvent(
                id,
                hazardType.key(),
                title,
                "",
                observedAt,
                10.0,
                20.0,
                2,
                1,
                "open",
                "TEST",
                "",
                "",
                magnitude,
                magnitude == null
                        ? ""
                        : "kts",
                magnitude == null
                        ? ""
                        : "Maximum sustained winds"
        );
    }
}
