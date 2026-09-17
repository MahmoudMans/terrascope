package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AlertDashboardResponse;
import com.terrascope.platform.api.model.AlertEvent;
import com.terrascope.platform.api.model.AlertRule;
import com.terrascope.platform.api.model.CreateAlertRuleRequest;
import com.terrascope.platform.domain.AlertEventStatus;
import com.terrascope.platform.repository.HazardAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HazardAlertServiceTest {

    private HazardAlertRepository repository;
    private HazardAlertService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        HazardAlertRepository.class
                );

        service =
                new HazardAlertService(
                        repository,
                        new HazardAlertEngine(
                                new RegionalIntelligenceEngine()
                        )
                );
    }

    @Test
    void normalizesAllHazardRule() {
        CreateAlertRuleRequest request =
                new CreateAlertRuleRequest(
                        "All hazards",
                        "ALL",
                        36.8,
                        10.2,
                        250.0,
                        0.0
                );

        AlertRule expected =
                rule(
                        "all",
                        true
                );

        when(
                repository.createRule(
                        any(),
                        eq(request),
                        eq("all")
                )
        ).thenReturn(
                expected
        );

        assertThat(
                service.createRule(
                        request
                )
        ).isEqualTo(
                expected
        );
    }

    @Test
    void summarizesDashboard() {
        when(
                repository.findRules()
        ).thenReturn(
                List.of(
                        rule(
                                "all",
                                true
                        ),
                        rule(
                                "earthquakes",
                                false
                        )
                )
        );

        when(
                repository.findEvents()
        ).thenReturn(
                List.of(
                        event(
                                AlertEventStatus.NEW
                        ),
                        event(
                                AlertEventStatus.ACKNOWLEDGED
                        )
                )
        );

        AlertDashboardResponse response =
                service.dashboard();

        assertThat(
                response.ruleCount()
        ).isEqualTo(2);

        assertThat(
                response.enabledRuleCount()
        ).isEqualTo(1);

        assertThat(
                response.alertCount()
        ).isEqualTo(2);

        assertThat(
                response.newAlertCount()
        ).isEqualTo(1);

        assertThat(
                response.acknowledgedAlertCount()
        ).isEqualTo(1);
    }

    private AlertRule rule(
            String hazardType,
            boolean enabled
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new AlertRule(
                UUID.randomUUID(),
                "Rule",
                hazardType,
                36.8,
                10.2,
                200.0,
                0.0,
                enabled,
                now,
                now
        );
    }

    private AlertEvent event(
            AlertEventStatus status
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new AlertEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Rule",
                "source-1",
                "earthquakes",
                "Event",
                36.8,
                10.2,
                5.0,
                now,
                10.0,
                status,
                now,
                now
        );
    }
}
