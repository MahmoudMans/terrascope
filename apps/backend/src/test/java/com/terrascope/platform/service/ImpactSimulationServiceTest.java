package com.terrascope.platform.service;

import com.terrascope.platform.api.model.ImpactSimulationRequest;
import com.terrascope.platform.api.model.ImpactSimulationResult;
import com.terrascope.platform.api.model.ImpactSimulationRunResponse;
import com.terrascope.platform.api.model.SimulationHistoryItem;
import com.terrascope.platform.api.model.SimulationHistoryResponse;
import com.terrascope.platform.api.model.SimulationScenario;
import com.terrascope.platform.domain.SimulationRiskBand;
import com.terrascope.platform.repository.ImpactSimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImpactSimulationServiceTest {

    private ImpactSimulationRepository repository;
    private ImpactModel model;
    private ImpactSimulationService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        ImpactSimulationRepository.class
                );

        model =
                new ImpactModel();

        service =
                new ImpactSimulationService(
                        repository,
                        model
                );
    }

    @Test
    void createsScenarioAndRun() {
        ImpactSimulationRequest request =
                request();

        UUID scenarioId =
                UUID.randomUUID();

        SimulationScenario scenario =
                scenario(
                        scenarioId
                );

        when(
                repository.createScenario(
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(
                scenario
        );

        when(
                repository.createRun(
                        any(),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        anyDouble(),
                        any()
                )
        ).thenAnswer(invocation ->
                new ImpactSimulationResult(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        invocation.getArgument(7),
                        invocation.getArgument(8),
                        "TERRASCOPE_IMPACT_V1",
                        Instant.parse(
                                "2026-09-17T12:00:00Z"
                        )
                )
        );

        ImpactSimulationRunResponse response =
                service.run(
                        request
                );

        assertThat(
                response.scenario()
        ).isEqualTo(
                scenario
        );

        assertThat(
                response.result()
                        .methodologyVersion()
        ).isEqualTo(
                "TERRASCOPE_IMPACT_V1"
        );
    }

    @Test
    void summarizesHistory() {
        UUID scenarioId =
                UUID.randomUUID();

        SimulationScenario scenario =
                scenario(
                        scenarioId
                );

        when(
                repository.history()
        ).thenReturn(
                List.of(
                        item(
                                scenario,
                                SimulationRiskBand.EXTREME,
                                91.0
                        ),
                        item(
                                scenario,
                                SimulationRiskBand.SEVERE,
                                76.0
                        ),
                        item(
                                scenario,
                                SimulationRiskBand.MODERATE,
                                35.0
                        )
                )
        );

        SimulationHistoryResponse response =
                service.history();

        assertThat(
                response.totalCount()
        ).isEqualTo(3);

        assertThat(
                response.extremeCount()
        ).isEqualTo(1);

        assertThat(
                response.severeCount()
        ).isEqualTo(1);

        assertThat(
                response.averageRiskScore()
        ).isEqualTo(67.33);
    }

    private ImpactSimulationRequest request() {
        return new ImpactSimulationRequest(
                "Tunis earthquake scenario",
                "earthquakes",
                36.8065,
                10.1815,
                120.0,
                7.8,
                1_500_000L,
                55.0,
                65.0
        );
    }

    private SimulationScenario scenario(
            UUID id
    ) {
        return new SimulationScenario(
                id,
                "Tunis earthquake scenario",
                "earthquakes",
                36.8065,
                10.1815,
                120.0,
                7.8,
                1_500_000L,
                55.0,
                65.0,
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                )
        );
    }

    private SimulationHistoryItem item(
            SimulationScenario scenario,
            SimulationRiskBand band,
            double score
    ) {
        return new SimulationHistoryItem(
                scenario,
                new ImpactSimulationResult(
                        UUID.randomUUID(),
                        scenario.id(),
                        100_000,
                        20_000,
                        450.0,
                        40.0,
                        45.0,
                        score,
                        band,
                        "TERRASCOPE_IMPACT_V1",
                        Instant.parse(
                                "2026-09-17T12:00:00Z"
                        )
                )
        );
    }
}
