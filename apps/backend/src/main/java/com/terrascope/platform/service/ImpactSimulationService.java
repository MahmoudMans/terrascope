package com.terrascope.platform.service;

import com.terrascope.platform.api.model.ImpactSimulationRequest;
import com.terrascope.platform.api.model.ImpactSimulationResult;
import com.terrascope.platform.api.model.ImpactSimulationRunResponse;
import com.terrascope.platform.api.model.SimulationHistoryItem;
import com.terrascope.platform.api.model.SimulationHistoryResponse;
import com.terrascope.platform.api.model.SimulationScenario;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.domain.SimulationRiskBand;
import com.terrascope.platform.repository.ImpactSimulationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ImpactSimulationService {

    private final ImpactSimulationRepository repository;
    private final ImpactModel impactModel;

    public ImpactSimulationService(
            ImpactSimulationRepository repository,
            ImpactModel impactModel
    ) {
        this.repository = repository;
        this.impactModel = impactModel;
    }

    @Transactional
    public ImpactSimulationRunResponse run(
            ImpactSimulationRequest request
    ) {
        HazardType hazardType =
                HazardType.fromKey(
                        request.hazardType()
                );

        UUID scenarioId =
                UUID.randomUUID();

        SimulationScenario scenario =
                repository.createScenario(
                        scenarioId,
                        request,
                        hazardType.key()
                );

        ImpactModel.ModelResult modelResult =
                impactModel.calculate(
                        request,
                        hazardType
                );

        ImpactSimulationResult result =
                repository.createRun(
                        UUID.randomUUID(),
                        scenarioId,
                        modelResult.affectedPopulation(),
                        modelResult.displacedPopulation(),
                        modelResult.estimatedEconomicLossMusd(),
                        modelResult.infrastructureDisruptionPct(),
                        modelResult.responsePressurePct(),
                        modelResult.compositeRiskScore(),
                        modelResult.riskBand()
                );

        return new ImpactSimulationRunResponse(
                scenario,
                result
        );
    }

    public SimulationHistoryResponse history() {
        List<SimulationHistoryItem> runs =
                repository.history();

        int extremeCount =
                (int) runs
                        .stream()
                        .filter(item ->
                                item.result().riskBand()
                                        == SimulationRiskBand.EXTREME
                        )
                        .count();

        int severeCount =
                (int) runs
                        .stream()
                        .filter(item ->
                                item.result().riskBand()
                                        == SimulationRiskBand.SEVERE
                        )
                        .count();

        double averageRiskScore =
                runs
                        .stream()
                        .mapToDouble(item ->
                                item
                                        .result()
                                        .compositeRiskScore()
                        )
                        .average()
                        .orElse(0.0);

        averageRiskScore =
                Math.round(
                        averageRiskScore
                                * 100.0
                )
                        / 100.0;

        return new SimulationHistoryResponse(
                runs.size(),
                extremeCount,
                severeCount,
                averageRiskScore,
                runs
        );
    }
}
