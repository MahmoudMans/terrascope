package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CommandPulseSnapshot;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.CommandPulseMetrics;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentCommandPulseRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentCommandPulseServiceTest {

    private IncidentCommandPulseRepository repository;
    private IncidentResponseRepository incidentRepository;
    private IncidentCommandPulseService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        IncidentCommandPulseRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new IncidentCommandPulseService(
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
                        incident(
                                IncidentStatus.RESPONDING
                        )
                )
        );
    }

    @Test
    void emptyIncidentIsStable() {
        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                metrics(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                )
        );

        IncidentCommandPulseResponse pulse =
                service.pulse(
                        incidentId
                );

        assertThat(
                pulse.score()
        ).isZero();

        assertThat(
                pulse.band()
        ).isEqualTo(
                CommandPressureBand.STABLE
        );

        assertThat(
                pulse.blockers()
        ).isEmpty();
    }

    @Test
    void aggregatesCrossFunctionalPressureDeterministically() {
        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                metrics(
                        1,
                        1,
                        1,
                        1,
                        10000,
                        90,
                        1,
                        1,
                        5000,
                        25.5,
                        1,
                        0
                )
        );

        IncidentCommandPulseResponse pulse =
                service.pulse(
                        incidentId
                );

        assertThat(
                pulse.score()
        ).isEqualTo(49);

        assertThat(
                pulse.band()
        ).isEqualTo(
                CommandPressureBand.ELEVATED
        );

        assertThat(
                pulse.blockers()
        ).hasSize(7);

        assertThat(
                pulse.blockers()
                        .getFirst()
                        .weight()
        ).isEqualTo(15);
    }

    @Test
    void snapshotPersistsPulseAndWritesTimeline() {
        CommandPulseMetrics metrics =
                metrics(
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                );

        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                metrics
        );

        when(
                repository.saveSnapshot(
                        any(),
                        any()
                )
        ).thenAnswer(invocation -> {
            IncidentCommandPulseResponse pulse =
                    invocation.getArgument(
                            1
                    );

            return new CommandPulseSnapshot(
                    UUID.randomUUID(),
                    incidentId,
                    pulse.score(),
                    pulse.band(),
                    pulse.blockers().size(),

                    pulse.openActionCount(),
                    pulse.overdueActionCount(),
                    pulse.blockedActionCount(),

                    pulse.resourceShortageCount(),

                    pulse.evacuationRemaining(),
                    pulse.shelterRemainingCapacity(),

                    pulse.lifelineDisruptedCount(),
                    pulse.criticalLifelineDisruptionCount(),
                    pulse.lifelineAffectedPopulation(),

                    pulse.unresolvedDamageLossMusd(),
                    pulse.criticalDamageCount(),
                    pulse.averageRepairProgressPct(),

                    Instant.now()
            );
        });

        CommandPulseSnapshot snapshot =
                service.createSnapshot(
                        incidentId
                );

        assertThat(
                snapshot.score()
        ).isEqualTo(4);

        assertThat(
                snapshot.band()
        ).isEqualTo(
                CommandPressureBand.STABLE
        );

        verify(
                repository
        ).saveSnapshot(
                any(),
                any()
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq("COMMAND_SNAPSHOT_CREATED"),
                any()
        );
    }

    @Test
    void closedIncidentRejectsCommandSnapshot() {
        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        incident(
                                IncidentStatus.CLOSED
                        )
                )
        );

        assertThatThrownBy(() ->
                service.createSnapshot(
                        incidentId
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    private IncidentCase incident(
            IncidentStatus status
    ) {
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
                status,
                "Command",
                0,
                now,
                now,
                status == IncidentStatus.CLOSED
                        ? now
                        : null
        );
    }

    private CommandPulseMetrics metrics(
            int openActions,
            int overdueActions,
            int blockedActions,
            int resourceShortages,
            long evacuationRemaining,
            long shelterRemainingCapacity,
            int lifelineDisrupted,
            int criticalLifelineDisrupted,
            long lifelineAffectedPopulation,
            double unresolvedDamageLoss,
            int criticalDamage,
            double averageRepairProgress
    ) {
        return new CommandPulseMetrics(
                openActions,
                overdueActions,
                blockedActions,
                resourceShortages,
                evacuationRemaining,
                shelterRemainingCapacity,
                lifelineDisrupted,
                criticalLifelineDisrupted,
                lifelineAffectedPopulation,
                unresolvedDamageLoss,
                criticalDamage,
                averageRepairProgress
        );
    }
}
