package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CreateIncidentActionRequest;
import com.terrascope.platform.api.model.IncidentActionBoardResponse;
import com.terrascope.platform.api.model.UpdateIncidentActionDueDateRequest;
import com.terrascope.platform.api.model.UpdateIncidentActionOwnerRequest;
import com.terrascope.platform.api.model.UpdateIncidentActionPriorityRequest;
import com.terrascope.platform.api.model.UpdateIncidentActionStatusRequest;
import com.terrascope.platform.service.IncidentActionService;
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
@RequestMapping("/api/v1/incidents/{incidentId}/actions")
public class IncidentActionController {

    private final IncidentActionService service;

    public IncidentActionController(
            IncidentActionService service
    ) {
        this.service = service;
    }

    @GetMapping
    public IncidentActionBoardResponse board(
            @PathVariable
            UUID incidentId
    ) {
        return service.board(
                incidentId
        );
    }

    @PostMapping
    public IncidentActionBoardResponse create(
            @PathVariable
            UUID incidentId,

            @Valid
            @RequestBody
            CreateIncidentActionRequest request
    ) {
        return service.create(
                incidentId,
                request
        );
    }

    @PatchMapping("/{actionId}/status")
    public IncidentActionBoardResponse updateStatus(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID actionId,

            @Valid
            @RequestBody
            UpdateIncidentActionStatusRequest request
    ) {
        return service.updateStatus(
                incidentId,
                actionId,
                request
        );
    }

    @PatchMapping("/{actionId}/priority")
    public IncidentActionBoardResponse updatePriority(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID actionId,

            @Valid
            @RequestBody
            UpdateIncidentActionPriorityRequest request
    ) {
        return service.updatePriority(
                incidentId,
                actionId,
                request
        );
    }

    @PatchMapping("/{actionId}/owner")
    public IncidentActionBoardResponse updateOwner(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID actionId,

            @Valid
            @RequestBody
            UpdateIncidentActionOwnerRequest request
    ) {
        return service.updateOwner(
                incidentId,
                actionId,
                request
        );
    }

    @PatchMapping("/{actionId}/due-at")
    public IncidentActionBoardResponse updateDueAt(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID actionId,

            @RequestBody
            UpdateIncidentActionDueDateRequest request
    ) {
        return service.updateDueAt(
                incidentId,
                actionId,
                request
        );
    }

    @DeleteMapping("/{actionId}")
    public IncidentActionBoardResponse delete(
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
