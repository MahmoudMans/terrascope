package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CreatePreparednessCorrectiveActionRequest;
import com.terrascope.platform.api.model.ResilienceValidationBoardResponse;
import com.terrascope.platform.api.model.UpdateCorrectiveActionDueDateRequest;
import com.terrascope.platform.api.model.UpdateCorrectiveActionOwnerRequest;
import com.terrascope.platform.api.model.UpdateCorrectiveActionStatusRequest;
import com.terrascope.platform.service.ResilienceValidationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/resilience-validation")
public class ResilienceValidationController {

    private final ResilienceValidationService service;

    public ResilienceValidationController(
            ResilienceValidationService service
    ) {
        this.service = service;
    }

    @GetMapping
    public ResilienceValidationBoardResponse board(
            @PathVariable
            UUID incidentId
    ) {
        return service.board(
                incidentId
        );
    }

    @PostMapping("/exercises/{exerciseId}/corrective-actions")
    public ResilienceValidationBoardResponse createCorrectiveAction(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID exerciseId,

            @Valid
            @RequestBody
            CreatePreparednessCorrectiveActionRequest request
    ) {
        return service.createCorrectiveAction(
                incidentId,
                exerciseId,
                request
        );
    }

    @PatchMapping("/corrective-actions/{correctiveActionId}/status")
    public ResilienceValidationBoardResponse updateCorrectiveStatus(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID correctiveActionId,

            @Valid
            @RequestBody
            UpdateCorrectiveActionStatusRequest request
    ) {
        return service.updateCorrectiveStatus(
                incidentId,
                correctiveActionId,
                request
        );
    }

    @PatchMapping("/corrective-actions/{correctiveActionId}/owner")
    public ResilienceValidationBoardResponse updateCorrectiveOwner(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID correctiveActionId,

            @Valid
            @RequestBody
            UpdateCorrectiveActionOwnerRequest request
    ) {
        return service.updateCorrectiveOwner(
                incidentId,
                correctiveActionId,
                request
        );
    }

    @PatchMapping("/corrective-actions/{correctiveActionId}/due-at")
    public ResilienceValidationBoardResponse updateCorrectiveDueAt(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID correctiveActionId,

            @RequestBody
            UpdateCorrectiveActionDueDateRequest request
    ) {
        return service.updateCorrectiveDueAt(
                incidentId,
                correctiveActionId,
                request
        );
    }

    @DeleteMapping("/corrective-actions/{correctiveActionId}")
    public ResilienceValidationBoardResponse deleteCorrectiveAction(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID correctiveActionId
    ) {
        return service.deleteCorrectiveAction(
                incidentId,
                correctiveActionId
        );
    }
}
