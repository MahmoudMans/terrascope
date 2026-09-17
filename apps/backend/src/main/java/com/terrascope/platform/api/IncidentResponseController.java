package com.terrascope.platform.api;

import com.terrascope.platform.api.model.AddIncidentNoteRequest;
import com.terrascope.platform.api.model.CreateIncidentRequest;
import com.terrascope.platform.api.model.IncidentDashboardResponse;
import com.terrascope.platform.api.model.IncidentDetail;
import com.terrascope.platform.api.model.UpdateIncidentOwnerRequest;
import com.terrascope.platform.api.model.UpdateIncidentSeverityRequest;
import com.terrascope.platform.api.model.UpdateIncidentStatusRequest;
import com.terrascope.platform.service.IncidentResponseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentResponseController {

    private final IncidentResponseService service;

    public IncidentResponseController(
            IncidentResponseService service
    ) {
        this.service = service;
    }

    @GetMapping
    public IncidentDashboardResponse dashboard() {
        return service.dashboard();
    }

    @GetMapping("/{id}")
    public IncidentDetail detail(
            @PathVariable
            UUID id
    ) {
        return service.detail(
                id
        );
    }

    @PostMapping
    public IncidentDetail create(
            @Valid
            @RequestBody
            CreateIncidentRequest request
    ) {
        return service.create(
                request
        );
    }

    @PatchMapping("/{id}/status")
    public IncidentDetail updateStatus(
            @PathVariable
            UUID id,

            @Valid
            @RequestBody
            UpdateIncidentStatusRequest request
    ) {
        return service.updateStatus(
                id,
                request
        );
    }

    @PatchMapping("/{id}/severity")
    public IncidentDetail updateSeverity(
            @PathVariable
            UUID id,

            @Valid
            @RequestBody
            UpdateIncidentSeverityRequest request
    ) {
        return service.updateSeverity(
                id,
                request
        );
    }

    @PatchMapping("/{id}/owner")
    public IncidentDetail updateOwner(
            @PathVariable
            UUID id,

            @Valid
            @RequestBody
            UpdateIncidentOwnerRequest request
    ) {
        return service.updateOwner(
                id,
                request
        );
    }

    @PostMapping("/{id}/notes")
    public IncidentDetail addNote(
            @PathVariable
            UUID id,

            @Valid
            @RequestBody
            AddIncidentNoteRequest request
    ) {
        return service.addNote(
                id,
                request
        );
    }
}
