package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateDamageAssessmentRequest;
import com.terrascope.platform.api.model.DamageAssessment;
import com.terrascope.platform.api.model.DamageRecoveryBoardResponse;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.UpdateDamageProgressRequest;
import com.terrascope.platform.domain.DamageAssetCategory;
import com.terrascope.platform.domain.DamageRecoveryStatus;
import com.terrascope.platform.domain.DamageSeverity;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.DamageRecoveryRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
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

class DamageRecoveryServiceTest {

    private DamageRecoveryRepository repository;
    private IncidentResponseRepository incidentRepository;
    private DamageRecoveryService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        DamageRecoveryRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new DamageRecoveryService(
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
    void boardSummarizesDamageAndRecoveryBacklog() {
        when(
                repository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        assessment(
                                DamageSeverity.DESTROYED,
                                DamageRecoveryStatus.REPORTED,
                                10,
                                15.0,
                                0
                        ),
                        assessment(
                                DamageSeverity.MAJOR,
                                DamageRecoveryStatus.REPAIRING,
                                20,
                                10.5,
                                50
                        ),
                        assessment(
                                DamageSeverity.MODERATE,
                                DamageRecoveryStatus.RESTORED,
                                5,
                                3.0,
                                100
                        )
                )
        );

        DamageRecoveryBoardResponse board =
                service.board(
                        incidentId
                );

        assertThat(
                board.totalCount()
        ).isEqualTo(3);

        assertThat(
                board.reportedCount()
        ).isEqualTo(1);

        assertThat(
                board.repairingCount()
        ).isEqualTo(1);

        assertThat(
                board.restoredCount()
        ).isEqualTo(1);

        assertThat(
                board.criticalDamageCount()
        ).isEqualTo(2);

        assertThat(
                board.affectedUnits()
        ).isEqualTo(35);

        assertThat(
                board.totalEstimatedLossMusd()
        ).isEqualTo(28.5);

        assertThat(
                board.unresolvedEstimatedLossMusd()
        ).isEqualTo(25.5);

        assertThat(
                board.averageRepairProgressPct()
        ).isEqualTo(50.0);
    }

    @Test
    void createWritesTimelineEvidence() {
        CreateDamageAssessmentRequest request =
                new CreateDamageAssessmentRequest(
                        "BRIDGE",
                        "Bridge Alpha",
                        "Structural damage.",
                        "MAJOR",
                        1,
                        25.5,
                        "Recovery Alpha",
                        null
                );

        DamageAssessment created =
                assessment(
                        DamageSeverity.MAJOR,
                        DamageRecoveryStatus.REPORTED,
                        1,
                        25.5,
                        0
                );

        when(
                repository.create(
                        any(),
                        eq(incidentId),
                        eq(request),
                        eq(
                                DamageAssetCategory.BRIDGE
                        ),
                        eq(
                                DamageSeverity.MAJOR
                        ),
                        eq("Recovery Alpha")
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
                eq("DAMAGE_ASSESSMENT_CREATED"),
                any()
        );
    }

    @Test
    void unverifiedDamageRejectsRepairProgress() {
        DamageAssessment reported =
                assessment(
                        DamageSeverity.MAJOR,
                        DamageRecoveryStatus.REPORTED,
                        1,
                        25.5,
                        0
                );

        when(
                repository.findById(
                        incidentId,
                        reported.id()
                )
        ).thenReturn(
                Optional.of(
                        reported
                )
        );

        assertThatThrownBy(() ->
                service.updateProgress(
                        incidentId,
                        reported.id(),
                        new UpdateDamageProgressRequest(
                                50
                        )
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void fullRepairProgressAutomaticallyRestoresAssessment() {
        DamageAssessment before =
                assessment(
                        DamageSeverity.MAJOR,
                        DamageRecoveryStatus.REPAIRING,
                        1,
                        25.5,
                        60
                );

        DamageAssessment after =
                assessment(
                        DamageSeverity.MAJOR,
                        DamageRecoveryStatus.RESTORED,
                        1,
                        25.5,
                        100
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
                repository.updateProgress(
                        incidentId,
                        before.id(),
                        100,
                        DamageRecoveryStatus.RESTORED
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

        service.updateProgress(
                incidentId,
                before.id(),
                new UpdateDamageProgressRequest(
                        100
                )
        );

        verify(
                repository
        ).updateProgress(
                incidentId,
                before.id(),
                100,
                DamageRecoveryStatus.RESTORED
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq("DAMAGE_PROGRESS_CHANGED"),
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
                "earthquakes",
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

    private DamageAssessment assessment(
            DamageSeverity severity,
            DamageRecoveryStatus status,
            int affectedUnits,
            double estimatedLossMusd,
            int progress
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new DamageAssessment(
                UUID.randomUUID(),
                incidentId,
                DamageAssetCategory.BRIDGE,
                "Bridge Alpha",
                "",
                severity,
                status,
                affectedUnits,
                estimatedLossMusd,
                "Recovery Alpha",
                null,
                progress,
                now,
                now,
                status
                        == DamageRecoveryStatus.REPORTED
                        ? null
                        : now,
                status
                        == DamageRecoveryStatus.RESTORED
                        ? now
                        : null
        );
    }
}
