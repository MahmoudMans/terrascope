package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CreateIncidentResourceRequest;
import com.terrascope.platform.api.model.IncidentResourceBoardResponse;
import com.terrascope.platform.api.model.UpdateIncidentResourceAllocationRequest;
import com.terrascope.platform.api.model.UpdateIncidentResourceOwnerRequest;
import com.terrascope.platform.api.model.UpdateIncidentResourcePriorityRequest;
import com.terrascope.platform.service.IncidentResourceService;
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
@RequestMapping("/api/v1/incidents/{incidentId}/resources")
public class IncidentResourceController {

    private final IncidentResourceService service;

    public IncidentResourceController(
            IncidentResourceService service
    ) {
        this.service = service;
    }

    @GetMapping
    public IncidentResourceBoardResponse board(
            @PathVariable
            UUID incidentId
    ) {
        return service.board(
                incidentId
        );
    }

    @PostMapping
    public IncidentResourceBoardResponse create(
            @PathVariable
            UUID incidentId,

            @Valid
            @RequestBody
            CreateIncidentResourceRequest request
    ) {
        return service.create(
                incidentId,
                request
        );
    }

    @PatchMapping("/{resourceId}/allocation")
    public IncidentResourceBoardResponse updateAllocation(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID resourceId,

            @Valid
            @RequestBody
            UpdateIncidentResourceAllocationRequest request
    ) {
        return service.updateAllocation(
                incidentId,
                resourceId,
                request
        );
    }

    @PatchMapping("/{resourceId}/priority")
    public IncidentResourceBoardResponse updatePriority(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID resourceId,

            @Valid
            @RequestBody
            UpdateIncidentResourcePriorityRequest request
    ) {
        return service.updatePriority(
                incidentId,
                resourceId,
                request
        );
    }

    @PatchMapping("/{resourceId}/owner")
    public IncidentResourceBoardResponse updateOwner(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID resourceId,

            @Valid
            @RequestBody
            UpdateIncidentResourceOwnerRequest request
    ) {
        return service.updateOwner(
                incidentId,
                resourceId,
                request
        );
    }

    @PostMapping("/{resourceId}/cancel")
    public IncidentResourceBoardResponse cancel(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID resourceId
    ) {
        return service.cancel(
                incidentId,
                resourceId
        );
    }

    @DeleteMapping("/{resourceId}")
    public IncidentResourceBoardResponse delete(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID resourceId
    ) {
        return service.delete(
                incidentId,
                resourceId
        );
    }
}
