package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CreateEvacuationZoneRequest;
import com.terrascope.platform.api.model.CreateShelterRequest;
import com.terrascope.platform.api.model.EvacuationOperationsBoardResponse;
import com.terrascope.platform.api.model.UpdateEvacuationOwnerRequest;
import com.terrascope.platform.api.model.UpdateEvacuationProgressRequest;
import com.terrascope.platform.api.model.UpdateEvacuationZoneStatusRequest;
import com.terrascope.platform.api.model.UpdateShelterOccupancyRequest;
import com.terrascope.platform.api.model.UpdateShelterOwnerRequest;
import com.terrascope.platform.api.model.UpdateShelterStatusRequest;
import com.terrascope.platform.service.EvacuationOperationsService;
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
@RequestMapping("/api/v1/incidents/{incidentId}/evacuation")
public class EvacuationOperationsController {

    private final EvacuationOperationsService service;

    public EvacuationOperationsController(
            EvacuationOperationsService service
    ) {
        this.service = service;
    }

    @GetMapping
    public EvacuationOperationsBoardResponse board(
            @PathVariable
            UUID incidentId
    ) {
        return service.board(
                incidentId
        );
    }

    @PostMapping("/zones")
    public EvacuationOperationsBoardResponse createZone(
            @PathVariable
            UUID incidentId,

            @Valid
            @RequestBody
            CreateEvacuationZoneRequest request
    ) {
        return service.createZone(
                incidentId,
                request
        );
    }

    @PatchMapping("/zones/{zoneId}/progress")
    public EvacuationOperationsBoardResponse updateZoneProgress(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID zoneId,

            @Valid
            @RequestBody
            UpdateEvacuationProgressRequest request
    ) {
        return service.updateZoneProgress(
                incidentId,
                zoneId,
                request
        );
    }

    @PatchMapping("/zones/{zoneId}/status")
    public EvacuationOperationsBoardResponse updateZoneStatus(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID zoneId,

            @Valid
            @RequestBody
            UpdateEvacuationZoneStatusRequest request
    ) {
        return service.updateZoneStatus(
                incidentId,
                zoneId,
                request
        );
    }

    @PatchMapping("/zones/{zoneId}/owner")
    public EvacuationOperationsBoardResponse updateZoneOwner(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID zoneId,

            @Valid
            @RequestBody
            UpdateEvacuationOwnerRequest request
    ) {
        return service.updateZoneOwner(
                incidentId,
                zoneId,
                request
        );
    }

    @DeleteMapping("/zones/{zoneId}")
    public EvacuationOperationsBoardResponse deleteZone(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID zoneId
    ) {
        return service.deleteZone(
                incidentId,
                zoneId
        );
    }

    @PostMapping("/shelters")
    public EvacuationOperationsBoardResponse createShelter(
            @PathVariable
            UUID incidentId,

            @Valid
            @RequestBody
            CreateShelterRequest request
    ) {
        return service.createShelter(
                incidentId,
                request
        );
    }

    @PatchMapping("/shelters/{shelterId}/occupancy")
    public EvacuationOperationsBoardResponse updateShelterOccupancy(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID shelterId,

            @Valid
            @RequestBody
            UpdateShelterOccupancyRequest request
    ) {
        return service.updateShelterOccupancy(
                incidentId,
                shelterId,
                request
        );
    }

    @PatchMapping("/shelters/{shelterId}/status")
    public EvacuationOperationsBoardResponse updateShelterStatus(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID shelterId,

            @Valid
            @RequestBody
            UpdateShelterStatusRequest request
    ) {
        return service.updateShelterStatus(
                incidentId,
                shelterId,
                request
        );
    }

    @PatchMapping("/shelters/{shelterId}/owner")
    public EvacuationOperationsBoardResponse updateShelterOwner(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID shelterId,

            @Valid
            @RequestBody
            UpdateShelterOwnerRequest request
    ) {
        return service.updateShelterOwner(
                incidentId,
                shelterId,
                request
        );
    }

    @DeleteMapping("/shelters/{shelterId}")
    public EvacuationOperationsBoardResponse deleteShelter(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID shelterId
    ) {
        return service.deleteShelter(
                incidentId,
                shelterId
        );
    }
}
