package com.terrascope.platform.service;

import com.terrascope.platform.api.model.RegionalAnalysisHistoryResponse;
import com.terrascope.platform.api.model.RegionalAnalysisRequest;
import com.terrascope.platform.api.model.RegionalAnalysisResult;
import com.terrascope.platform.domain.RegionalActivityBand;
import com.terrascope.platform.repository.RegionalIntelligenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RegionalIntelligenceService {

    private final RegionalIntelligenceEngine engine;
    private final RegionalIntelligenceRepository repository;

    public RegionalIntelligenceService(
            RegionalIntelligenceEngine engine,
            RegionalIntelligenceRepository repository
    ) {
        this.engine = engine;
        this.repository = repository;
    }

    @Transactional
    public RegionalAnalysisResult analyze(
            RegionalAnalysisRequest request
    ) {
        RegionalIntelligenceEngine.Calculation calculation =
                engine.calculate(
                        request
                );

        return repository.save(
                UUID.randomUUID(),
                request,
                calculation
        );
    }

    public RegionalAnalysisHistoryResponse history() {
        List<RegionalAnalysisResult> runs =
                repository.history();

        int criticalCount =
                (int) runs
                        .stream()
                        .filter(run ->
                                run.activityBand()
                                        == RegionalActivityBand.CRITICAL
                        )
                        .count();

        int highCount =
                (int) runs
                        .stream()
                        .filter(run ->
                                run.activityBand()
                                        == RegionalActivityBand.HIGH
                        )
                        .count();

        double averageActivityIndex =
                runs
                        .stream()
                        .mapToDouble(
                                RegionalAnalysisResult::activityIndex
                        )
                        .average()
                        .orElse(0.0);

        averageActivityIndex =
                Math.round(
                        averageActivityIndex
                                * 100.0
                )
                        / 100.0;

        return new RegionalAnalysisHistoryResponse(
                runs.size(),
                criticalCount,
                highCount,
                averageActivityIndex,
                runs
        );
    }
}
