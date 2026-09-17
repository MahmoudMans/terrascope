package com.terrascope.platform.service;

import com.terrascope.platform.api.model.VolcanoEvent;
import com.terrascope.platform.api.model.VolcanoFeedResponse;
import com.terrascope.platform.infrastructure.eonet.EonetSourceUnavailableException;
import com.terrascope.platform.infrastructure.eonet.NasaEonetClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VolcanoServiceTest {

    private NasaEonetClient client;
    private DemoVolcanoFallback fallback;
    private VolcanoService service;

    @BeforeEach
    void setUp() {
        client =
                mock(
                        NasaEonetClient.class
                );

        fallback =
                mock(
                        DemoVolcanoFallback.class
                );

        service =
                new VolcanoService(
                        client,
                        fallback
                );
    }

    @Test
    void filtersAndSummarizesEonetEvents() {
        List<VolcanoEvent> events =
                List.of(
                        event(
                                "v1",
                                "Pacific Alpha",
                                Instant.parse(
                                        "2026-09-17T09:00:00Z"
                                ),
                                3
                        ),
                        event(
                                "v2",
                                "Andes Beta",
                                Instant.parse(
                                        "2026-09-17T10:00:00Z"
                                ),
                                2
                        )
                );

        when(
                client.fetchVolcanoEvents(
                        "open",
                        3650
                )
        ).thenReturn(
                events
        );

        VolcanoFeedResponse result =
                service.query(
                        "open",
                        3650,
                        20,
                        "Pacific"
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
                result.sourceEventCount()
        ).isEqualTo(2);

        assertThat(
                result.matchedEventCount()
        ).isEqualTo(1);

        assertThat(
                result.events()
        ).hasSize(1);

        assertThat(
                result.events().getFirst().id()
        ).isEqualTo(
                "v1"
        );

        assertThat(
                result.summary().totalObservationCount()
        ).isEqualTo(3);
    }

    @Test
    void fallsBackWhenEonetIsUnavailable() {
        when(
                client.fetchVolcanoEvents(
                        "open",
                        3650
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
                fallback.create()
        ).thenReturn(
                List.of(
                        event(
                                "demo",
                                "Demo volcano",
                                Instant.parse(
                                        "2026-09-17T10:00:00Z"
                                ),
                                1
                        )
                )
        );

        VolcanoFeedResponse result =
                service.query(
                        "open",
                        3650,
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

        assertThat(
                result.returnedEventCount()
        ).isEqualTo(1);
    }

    private VolcanoEvent event(
            String id,
            String title,
            Instant observedAt,
            int observations
    ) {
        return new VolcanoEvent(
                id,
                title,
                "",
                observedAt,
                10.0,
                20.0,
                observations,
                1,
                "open",
                "TEST",
                "",
                ""
        );
    }
}
