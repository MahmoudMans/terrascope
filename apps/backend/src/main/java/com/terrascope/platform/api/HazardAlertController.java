package com.terrascope.platform.api;

import com.terrascope.platform.api.model.AlertDashboardResponse;
import com.terrascope.platform.api.model.AlertEvaluationResponse;
import com.terrascope.platform.api.model.AlertEvent;
import com.terrascope.platform.api.model.AlertRule;
import com.terrascope.platform.api.model.CreateAlertRuleRequest;
import com.terrascope.platform.api.model.EvaluateAlertRulesRequest;
import com.terrascope.platform.api.model.UpdateAlertStatusRequest;
import com.terrascope.platform.service.HazardAlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class HazardAlertController {

    private final HazardAlertService service;

    public HazardAlertController(
            HazardAlertService service
    ) {
        this.service = service;
    }

    @GetMapping
    public AlertDashboardResponse dashboard() {
        return service.dashboard();
    }

    @PostMapping("/rules")
    public AlertRule createRule(
            @Valid
            @RequestBody
            CreateAlertRuleRequest request
    ) {
        return service.createRule(
                request
        );
    }

    @PatchMapping("/rules/{id}/enabled")
    public AlertRule setRuleEnabled(
            @PathVariable
            UUID id,

            @RequestParam
            boolean enabled
    ) {
        return service.setEnabled(
                id,
                enabled
        );
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(
            @PathVariable
            UUID id
    ) {
        service.deleteRule(
                id
        );
    }

    @PostMapping("/evaluate")
    public AlertEvaluationResponse evaluate(
            @Valid
            @RequestBody
            EvaluateAlertRulesRequest request
    ) {
        return service.evaluate(
                request
        );
    }

    @PatchMapping("/events/{id}/status")
    public AlertEvent updateStatus(
            @PathVariable
            UUID id,

            @Valid
            @RequestBody
            UpdateAlertStatusRequest request
    ) {
        return service.updateStatus(
                id,
                request
        );
    }
}
