package com.terrascope.platform.service;

import com.terrascope.platform.api.model.SaveWatchlistEventRequest;
import com.terrascope.platform.api.model.UpdateWatchPriorityRequest;
import com.terrascope.platform.api.model.WatchlistEntry;
import com.terrascope.platform.api.model.WatchlistFeedResponse;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.domain.WatchPriority;
import com.terrascope.platform.repository.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private WatchlistRepository repository;
    private WatchlistService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        WatchlistRepository.class
                );

        service =
                new WatchlistService(
                        repository
                );
    }

    @Test
    void summarizesSavedEvents() {
        when(
                repository.findAll()
        ).thenReturn(
                List.of(
                        entry(
                                "earthquakes",
                                WatchPriority.CRITICAL
                        ),
                        entry(
                                "wildfires",
                                WatchPriority.HIGH
                        ),
                        entry(
                                "volcanoes",
                                WatchPriority.MEDIUM
                        )
                )
        );

        WatchlistFeedResponse result =
                service.list();

        assertThat(
                result.summary().totalCount()
        ).isEqualTo(3);

        assertThat(
                result.summary().criticalCount()
        ).isEqualTo(1);

        assertThat(
                result.summary().highCount()
        ).isEqualTo(1);

        assertThat(
                result.summary().earthquakeCount()
        ).isEqualTo(1);

        assertThat(
                result.summary().wildfireCount()
        ).isEqualTo(1);
    }

    @Test
    void savesNormalizedHazardAndPriority() {
        SaveWatchlistEventRequest request =
                request(
                        "wildfires",
                        "HIGH"
                );

        WatchlistEntry expected =
                entry(
                        "wildfires",
                        WatchPriority.HIGH
                );

        when(
                repository.upsert(
                        any(),
                        eq(
                                HazardType.WILDFIRE
                        ),
                        eq(
                                WatchPriority.HIGH
                        )
                )
        ).thenReturn(
                expected
        );

        WatchlistEntry result =
                service.save(
                        request
                );

        assertThat(result)
                .isEqualTo(expected);

        verify(repository)
                .upsert(
                        request,
                        HazardType.WILDFIRE,
                        WatchPriority.HIGH
                );
    }

    @Test
    void updatesPriority() {
        UUID id =
                UUID.randomUUID();

        WatchlistEntry expected =
                entry(
                        "severe-storms",
                        WatchPriority.CRITICAL
                );

        when(
                repository.updatePriority(
                        id,
                        WatchPriority.CRITICAL
                )
        ).thenReturn(
                Optional.of(
                        expected
                )
        );

        WatchlistEntry result =
                service.updatePriority(
                        id,
                        new UpdateWatchPriorityRequest(
                                "critical"
                        )
                );

        assertThat(
                result.priority()
        ).isEqualTo(
                WatchPriority.CRITICAL
        );
    }

    @Test
    void rejectsMissingEntryOnDelete() {
        UUID id =
                UUID.randomUUID();

        when(
                repository.deleteById(
                        id
                )
        ).thenReturn(0);

        assertThatThrownBy(() ->
                service.delete(id)
        )
                .isInstanceOf(
                        ResponseStatusException.class
                );
    }

    private SaveWatchlistEventRequest request(
            String hazardType,
            String priority
    ) {
        return new SaveWatchlistEventRequest(
                "source-1",
                hazardType,
                "Example event",
                "",
                35.0,
                -118.0,
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                ),
                "TEST",
                "",
                6.2,
                "M",
                priority,
                ""
        );
    }

    private WatchlistEntry entry(
            String hazardType,
            WatchPriority priority
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new WatchlistEntry(
                UUID.randomUUID(),
                "source-1",
                hazardType,
                "Example event",
                "",
                35.0,
                -118.0,
                now,
                "TEST",
                "",
                6.2,
                "M",
                priority,
                "",
                now,
                now
        );
    }
}
