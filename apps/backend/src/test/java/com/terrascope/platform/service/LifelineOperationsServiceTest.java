package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateLifelineAssetRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.LifelineAsset;
import com.terrascope.platform.api.model.LifelineOperationsBoardResponse;
import com.terrascope.platform.api.model.UpdateLifelineImpactRequest;
import com.terrascope.platform.api.model.UpdateLifelineStatusRequest;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.LifelineAssetCategory;
import com.terrascope.platform.domain.LifelineAssetStatus;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.LifelineOperationsRepository;
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

class LifelineOperationsServiceTest {

    private LifelineOperationsRepository repository;
    private IncidentResponseRepository incidentRepository;
    private LifelineOperationsService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        LifelineOperationsRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new LifelineOperationsService(
                        repository,
                        incidentRepository
                );

        incidentId =
                UUID.randomUUID();

        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        incident()
                )
        );
    }

    @Test
    void boardSummarizesDisruptions() {
        when(
                repository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        asset(
                                LifelineAssetStatus.OUTAGE,
                                IncidentActionPriority.CRITICAL,
                                5000,
                                Instant.parse(
                                        "2030-01-01T00:00:00Z"
                                )
                        ),
                        asset(
                                LifelineAssetStatus.DEGRADED,
                                IncidentActionPriority.HIGH,
                                2000,
                                null
                        ),
                        asset(
                                LifelineAssetStatus.OPERATIONAL,
                                IncidentActionPriority.MEDIUM,
                                0,
                                null
                        ),
                        asset(
                                LifelineAssetStatus.RESTORED,
                                IncidentActionPriority.HIGH,
                                0,
                                null
                        )
                )
        );

        LifelineOperationsBoardResponse board =
                service.board(
                        incidentId
                );

        assertThat(
                board.totalCount()
        ).isEqualTo(4);

        assertThat(
                board.disruptedCount()
        ).isEqualTo(2);

        assertThat(
                board.criticalDisruptionCount()
        ).isEqualTo(1);

        assertThat(
                board.affectedPopulation()
        ).isEqualTo(7000);

        assertThat(
                board.etaTrackedCount()
        ).isEqualTo(1);
    }

    @Test
    void createWritesTimelineEvidence() {
        CreateLifelineAssetRequest request =
                new CreateLifelineAssetRequest(
                        "POWER",
                        "Grid Alpha",
                        "Primary electrical grid.",
                        "CRITICAL",
                        "OUTAGE",
                        5000,
                        "Utility Alpha",
                        null
                );

        LifelineAsset created =
                asset(
                        LifelineAssetStatus.OUTAGE,
                        IncidentActionPriority.CRITICAL,
                        5000,
                        null
                );

        when(
                repository.create(
                        any(),
                        eq(incidentId),
                        eq(request),
                        eq(
                                LifelineAssetCategory.POWER
                        ),
                        eq(
                                IncidentActionPriority.CRITICAL
                        ),
                        eq(
                                LifelineAssetStatus.OUTAGE
                        ),
                        eq("Utility Alpha")
                )
        ).thenReturn(
                created
        );

        when(
                repository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        created
                )
        );

        service.create(
                incidentId,
                request
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq("LIFELINE_ASSET_CREATED"),
                any()
        );
    }

    @Test
    void operationalAssetRejectsImpactUpdate() {
        LifelineAsset operational =
                asset(
                        LifelineAssetStatus.OPERATIONAL,
                        IncidentActionPriority.HIGH,
                        0,
                        null
                );

        when(
                repository.findById(
                        incidentId,
                        operational.id()
                )
        ).thenReturn(
                Optional.of(
                        operational
                )
        );

        assertThatThrownBy(() ->
                service.updateImpact(
                        incidentId,
                        operational.id(),
                        new UpdateLifelineImpactRequest(
                                100
                        )
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void restoringAssetWritesTimelineEvidence() {
        LifelineAsset before =
                asset(
                        LifelineAssetStatus.OUTAGE,
                        IncidentActionPriority.CRITICAL,
                        5000,
                        null
                );

        LifelineAsset after =
                new LifelineAsset(
                        before.id(),
                        before.incidentId(),
                        before.category(),
                        before.name(),
                        before.description(),
                        before.priority(),
                        LifelineAssetStatus.RESTORED,
                        0,
                        before.owner(),
                        null,
                        before.createdAt(),
                        before.updatedAt(),
                        before.updatedAt()
                );

        when(
                repository.findById(
                        incidentId,
                        before.id()
                )
        ).thenReturn(
                Optional.of(
                        before
                )
        );

        when(
                repository.updateStatus(
                        incidentId,
                        before.id(),
                        LifelineAssetStatus.RESTORED
                )
        ).thenReturn(
                after
        );

        when(
                repository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        after
                )
        );

        service.updateStatus(
                incidentId,
                before.id(),
                new UpdateLifelineStatusRequest(
                        "RESTORED"
                )
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq("LIFELINE_STATUS_CHANGED"),
                any()
        );
    }

    private IncidentCase incident() {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentCase(
                incidentId,
                null,
                "source-1",
                "severe-storms",
                "Test incident",
                "",
                36.8,
                10.2,
                IncidentSeverity.SEVERE,
                IncidentStatus.RESPONDING,
                "Command",
                0,
                now,
                now,
                null
        );
    }

    private LifelineAsset asset(
            LifelineAssetStatus status,
            IncidentActionPriority priority,
            int affectedPopulation,
            Instant restorationEta
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new LifelineAsset(
                UUID.randomUUID(),
                incidentId,
                LifelineAssetCategory.POWER,
                "Grid Alpha",
                "",
                priority,
                status,
                affectedPopulation,
                "Utility Alpha",
                restorationEta,
                now,
                now,
                status
                        == LifelineAssetStatus.RESTORED
                        ? now
                        : null
        );
    }
}
