package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CreateDamageAssessmentRequest;
import com.terrascope.platform.api.model.DamageRecoveryBoardResponse;
import com.terrascope.platform.api.model.UpdateDamageOwnerRequest;
import com.terrascope.platform.api.model.UpdateDamageProgressRequest;
import com.terrascope.platform.api.model.UpdateDamageRecoveryEtaRequest;
import com.terrascope.platform.api.model.UpdateDamageSeverityRequest;
import com.terrascope.platform.service.DamageRecoveryService;
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
@RequestMapping("/api/v1/incidents/{incidentId}/damage")
public class DamageRecoveryController {

    private final DamageRecoveryService service;

    public DamageRecoveryController(
            DamageRecoveryService service
    ) {
        this.service = service;
    }

    @GetMapping
    public DamageRecoveryBoardResponse board(
            @PathVariable
            UUID incidentId
    ) {
        return service.board(
                incidentId
        );
    }

    @PostMapping
    public DamageRecoveryBoardResponse create(
            @PathVariable
            UUID incidentId,

            @Valid
            @RequestBody
            CreateDamageAssessmentRequest request
    ) {
        return service.create(
                incidentId,
                request
        );
    }

    @PostMapping("/{assessmentId}/verify")
    public DamageRecoveryBoardResponse verify(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assessmentId
    ) {
        return service.verify(
                incidentId,
                assessmentId
        );
    }

    @PatchMapping("/{assessmentId}/progress")
    public DamageRecoveryBoardResponse updateProgress(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assessmentId,

            @Valid
            @RequestBody
            UpdateDamageProgressRequest request
    ) {
        return service.updateProgress(
                incidentId,
                assessmentId,
                request
        );
    }

    @PatchMapping("/{assessmentId}/severity")
    public DamageRecoveryBoardResponse updateSeverity(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assessmentId,

            @Valid
            @RequestBody
            UpdateDamageSeverityRequest request
    ) {
        return service.updateSeverity(
                incidentId,
                assessmentId,
                request
        );
    }

    @PatchMapping("/{assessmentId}/owner")
    public DamageRecoveryBoardResponse updateOwner(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assessmentId,

            @Valid
            @RequestBody
            UpdateDamageOwnerRequest request
    ) {
        return service.updateOwner(
                incidentId,
                assessmentId,
                request
        );
    }

    @PatchMapping("/{assessmentId}/recovery-eta")
    public DamageRecoveryBoardResponse updateRecoveryEta(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assessmentId,

            @RequestBody
            UpdateDamageRecoveryEtaRequest request
    ) {
        return service.updateRecoveryEta(
                incidentId,
                assessmentId,
                request
        );
    }

    @DeleteMapping("/{assessmentId}")
    public DamageRecoveryBoardResponse delete(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID assessmentId
    ) {
        return service.delete(
                incidentId,
                assessmentId
        );
    }
}
