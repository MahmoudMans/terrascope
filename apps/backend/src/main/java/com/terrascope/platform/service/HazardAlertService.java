package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AlertDashboardResponse;
import com.terrascope.platform.api.model.AlertEvaluationResponse;
import com.terrascope.platform.api.model.AlertEvent;
import com.terrascope.platform.api.model.AlertRule;
import com.terrascope.platform.api.model.CreateAlertRuleRequest;
import com.terrascope.platform.api.model.EvaluateAlertRulesRequest;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import com.terrascope.platform.api.model.UpdateAlertStatusRequest;
import com.terrascope.platform.domain.AlertEventStatus;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.repository.HazardAlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class HazardAlertService {

    private final HazardAlertRepository repository;
    private final HazardAlertEngine engine;

    public HazardAlertService(
            HazardAlertRepository repository,
            HazardAlertEngine engine
    ) {
        this.repository = repository;
        this.engine = engine;
    }

    public AlertDashboardResponse dashboard() {
        List<AlertRule> rules =
                repository.findRules();

        List<AlertEvent> alerts =
                repository.findEvents();

        int enabledRuleCount =
                (int) rules
                        .stream()
                        .filter(
                                AlertRule::enabled
                        )
                        .count();

        int newAlertCount =
                countStatus(
                        alerts,
                        AlertEventStatus.NEW
                );

        int acknowledgedAlertCount =
                countStatus(
                        alerts,
                        AlertEventStatus.ACKNOWLEDGED
                );

        return new AlertDashboardResponse(
                rules.size(),
                enabledRuleCount,
                alerts.size(),
                newAlertCount,
                acknowledgedAlertCount,
                rules,
                alerts
        );
    }

    @Transactional
    public AlertRule createRule(
            CreateAlertRuleRequest request
    ) {
        String normalizedHazardType =
                normalizeRuleHazardType(
                        request.hazardType()
                );

        return repository.createRule(
                UUID.randomUUID(),
                request,
                normalizedHazardType
        );
    }

    @Transactional
    public AlertRule setEnabled(
            UUID id,
            boolean enabled
    ) {
        return repository
                .setRuleEnabled(
                        id,
                        enabled
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Alert rule "
                                        + id
                                        + " was not found."
                        )
                );
    }

    @Transactional
    public void deleteRule(
            UUID id
    ) {
        int deleted =
                repository.deleteRule(
                        id
                );

        if (deleted == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Alert rule "
                            + id
                            + " was not found."
            );
        }
    }

    @Transactional
    public AlertEvaluationResponse evaluate(
            EvaluateAlertRulesRequest request
    ) {
        List<AlertRule> rules =
                repository
                        .findRules()
                        .stream()
                        .filter(
                                AlertRule::enabled
                        )
                        .toList();

        int newlyTriggeredCount =
                0;

        for (AlertRule rule : rules) {
            for (
                    RegionalHazardPoint point
                    : request.points()
            ) {
                if (point == null) {
                    continue;
                }

                HazardType.fromKey(
                        point.hazardType()
                );

                HazardAlertEngine.Match match =
                        engine
                                .evaluate(
                                        rule,
                                        point
                                )
                                .orElse(null);

                if (match == null) {
                    continue;
                }

                boolean inserted =
                        repository
                                .insertAlert(
                                        UUID.randomUUID(),
                                        rule,
                                        point,
                                        match.distanceKm()
                                )
                                .isPresent();

                if (inserted) {
                    newlyTriggeredCount++;
                }
            }
        }

        return new AlertEvaluationResponse(
                rules.size(),
                request.points().size(),
                newlyTriggeredCount,
                dashboard()
        );
    }

    @Transactional
    public AlertEvent updateStatus(
            UUID id,
            UpdateAlertStatusRequest request
    ) {
        AlertEventStatus status =
                AlertEventStatus.fromValue(
                        request.status()
                );

        return repository
                .updateEventStatus(
                        id,
                        status
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Alert event "
                                        + id
                                        + " was not found."
                        )
                );
    }

    private int countStatus(
            List<AlertEvent> alerts,
            AlertEventStatus status
    ) {
        return (int) alerts
                .stream()
                .filter(alert ->
                        alert.status()
                                == status
                )
                .count();
    }

    private String normalizeRuleHazardType(
            String value
    ) {
        String normalized =
                value
                        .trim()
                        .toLowerCase();

        if (
                "all".equals(
                        normalized
                )
        ) {
            return "all";
        }

        return HazardType
                .fromKey(
                        normalized
                )
                .key();
    }
}
