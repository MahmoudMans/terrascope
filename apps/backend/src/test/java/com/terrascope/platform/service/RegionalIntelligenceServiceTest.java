package com.terrascope.platform.service;

import com.terrascope.platform.api.model.RegionalAnalysisHistoryResponse;
import com.terrascope.platform.api.model.RegionalAnalysisRequest;
import com.terrascope.platform.api.model.RegionalAnalysisResult;
import com.terrascope.platform.api.model.RegionalHazardBreakdown;
import com.terrascope.platform.domain.RegionalActivityBand;
import com.terrascope.platform.repository.RegionalIntelligenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionalIntelligenceServiceTest {

    private RegionalIntelligenceRepository repository;
    private RegionalIntelligenceService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        RegionalIntelligenceRepository.class
                );

        service =
                new RegionalIntelligenceService(
                        new RegionalIntelligenceEngine(),
                        repository
                );
    }

    @Test
    void persistsCalculatedAnalysis() {
        RegionalAnalysisRequest request =
                new RegionalAnalysisRequest(
                        "Test region",
                        36.8,
                        10.2,
                        500.0,
                        List.of()
                );

        RegionalAnalysisResult expected =
                result(
                        RegionalActivityBand.QUIET,
                        0.0
                );

        when(
                repository.save(
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(
                expected
        );

        RegionalAnalysisResult actual =
                service.analyze(
                        request
                );

        assertThat(actual)
                .isEqualTo(expected);
    }

    @Test
    void summarizesHistory() {
        when(
                repository.history()
        ).thenReturn(
                List.of(
                        result(
                                RegionalActivityBand.CRITICAL,
                                91.0
                        ),
                        result(
                                RegionalActivityBand.HIGH,
                                73.0
                        ),
                        result(
                                RegionalActivityBand.GUARDED,
                                26.0
                        )
                )
        );

        RegionalAnalysisHistoryResponse response =
                service.history();

        assertThat(
                response.totalCount()
        ).isEqualTo(3);

        assertThat(
                response.criticalCount()
        ).isEqualTo(1);

        assertThat(
                response.highCount()
        ).isEqualTo(1);

        assertThat(
                response.averageActivityIndex()
        ).isEqualTo(63.33);
    }

    private RegionalAnalysisResult result(
            RegionalActivityBand band,
            double index
    ) {
        return new RegionalAnalysisResult(
                UUID.randomUUID(),
                "Test region",
                36.8,
                10.2,
                500.0,
                10,
                3,
                new RegionalHazardBreakdown(
                        1,
                        0,
                        1,
                        1
                ),
                null,
                80.0,
                index,
                band,
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                )
        );
    }
}
