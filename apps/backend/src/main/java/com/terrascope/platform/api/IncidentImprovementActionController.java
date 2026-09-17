package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CreateImprovementActionRequest;
import com.terrascope.platform.api.model.ImprovementActionBoardResponse;
import com.terrascope.platform.api.model.UpdateImprovementDueDateRequest;
import com.terrascope.platform.api.model.UpdateImprovementOwnerRequest;
import com.terrascope.platform.api.model.UpdateImprovementPriorityRequest;
import com.terrascope.platform.api.model.UpdateImprovementStatusRequest;
import com.terrascope.platform.service.IncidentImprovementActionService;
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
@RequestMapping("/api/v1/incidents/{incidentId}/improvements")
public class IncidentImprovementActionController {

    private final IncidentImprovementActionService service;

    public IncidentImprovementActionController(
            IncidentImprovementActionService service
    ) {
        this.service = service;
    }

    @GetMapping
    public ImprovementActionBoardResponse board(
            @PathVariable
            UUID incidentId
    ) {
        return service.board(
                incidentId
        );
    }

    @PostMapping
    public ImprovementActionBoardResponse create(
            @PathVariable
            UUID incidentId,

            @Valid
            @RequestBody
            CreateImprovementActionRequest request
    ) {
        return service.create(
                incidentId,
                request
        );
    }

    @PatchMapping("/{actionId}/status")
    public ImprovementActionBoardResponse updateStatus(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID actionId,

            @Valid
            @RequestBody
            UpdateImprovementStatusRequest request
    ) {
        return service.updateStatus(
                incidentId,
                actionId,
                request
        );
    }

    @PatchMapping("/{actionId}/priority")
    public ImprovementActionBoardResponse updatePriority(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID actionId,

            @Valid
            @RequestBody
            UpdateImprovementPriorityRequest request
    ) {
        return service.updatePriority(
                incidentId,
                actionId,
                request
        );
    }

    @PatchMapping("/{actionId}/owner")
    public ImprovementActionBoardResponse updateOwner(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID actionId,

            @Valid
            @RequestBody
            UpdateImprovementOwnerRequest request
    ) {
        return service.updateOwner(
                incidentId,
                actionId,
                request
        );
    }

    @PatchMapping("/{actionId}/due-at")
    public ImprovementActionBoardResponse updateDueAt(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID actionId,

            @RequestBody
            UpdateImprovementDueDateRequest request
    ) {
        return service.updateDueAt(
                incidentId,
                actionId,
                request
        );
    }

    @DeleteMapping("/{actionId}")
    public ImprovementActionBoardResponse delete(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID actionId
    ) {
        return service.delete(
                incidentId,
                actionId
        );
    }
}
