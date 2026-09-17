package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateEvacuationZoneRequest;
import com.terrascope.platform.api.model.CreateShelterRequest;
import com.terrascope.platform.api.model.EvacuationOperationsBoardResponse;
import com.terrascope.platform.api.model.EvacuationZone;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentShelter;
import com.terrascope.platform.api.model.UpdateEvacuationOwnerRequest;
import com.terrascope.platform.api.model.UpdateEvacuationProgressRequest;
import com.terrascope.platform.api.model.UpdateEvacuationZoneStatusRequest;
import com.terrascope.platform.api.model.UpdateShelterOccupancyRequest;
import com.terrascope.platform.api.model.UpdateShelterOwnerRequest;
import com.terrascope.platform.api.model.UpdateShelterStatusRequest;
import com.terrascope.platform.domain.EvacuationZoneStatus;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.ShelterStatus;
import com.terrascope.platform.repository.EvacuationOperationsRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class EvacuationOperationsService {

    private final EvacuationOperationsRepository repository;
    private final IncidentResponseRepository incidentRepository;

    public EvacuationOperationsService(
            EvacuationOperationsRepository repository,
            IncidentResponseRepository incidentRepository
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
    }

    public EvacuationOperationsBoardResponse board(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        List<EvacuationZone> zones =
                repository.findZones(
                        incidentId
                );

        List<IncidentShelter> shelters =
                repository.findShelters(
                        incidentId
                );

        int activeZoneCount =
                (int) zones
                        .stream()
                        .filter(zone ->
                                zone.status()
                                        == EvacuationZoneStatus.MONITORING
                                || zone.status()
                                        == EvacuationZoneStatus.EVACUATING
                        )
                        .count();

        int clearedZoneCount =
                (int) zones
                        .stream()
                        .filter(zone ->
                                zone.status()
                                        == EvacuationZoneStatus.CLEARED
                        )
                        .count();

        long populationAtRisk =
                zones
                        .stream()
                        .filter(zone ->
                                zone.status()
                                        != EvacuationZoneStatus.CANCELLED
                        )
                        .mapToLong(
                                EvacuationZone::populationAtRisk
                        )
                        .sum();

        long evacuatedPopulation =
                zones
                        .stream()
                        .filter(zone ->
                                zone.status()
                                        != EvacuationZoneStatus.CANCELLED
                        )
                        .mapToLong(
                                EvacuationZone::evacuatedPopulation
                        )
                        .sum();

        long remainingToEvacuate =
                Math.max(
                        0,
                        populationAtRisk
                                - evacuatedPopulation
                );

        int openShelterCount =
                countShelterStatus(
                        shelters,
                        ShelterStatus.OPEN
                );

        int fullShelterCount =
                countShelterStatus(
                        shelters,
                        ShelterStatus.FULL
                );

        int closedShelterCount =
                countShelterStatus(
                        shelters,
                        ShelterStatus.CLOSED
                );

        long shelterCapacity =
                shelters
                        .stream()
                        .filter(shelter ->
                                shelter.status()
                                        != ShelterStatus.CLOSED
                        )
                        .mapToLong(
                                IncidentShelter::capacity
                        )
                        .sum();

        long shelterOccupancy =
                shelters
                        .stream()
                        .filter(shelter ->
                                shelter.status()
                                        != ShelterStatus.CLOSED
                        )
                        .mapToLong(
                                IncidentShelter::occupancy
                        )
                        .sum();

        long remainingShelterCapacity =
                Math.max(
                        0,
                        shelterCapacity
                                - shelterOccupancy
                );

        return new EvacuationOperationsBoardResponse(
                incidentId,

                zones.size(),
                activeZoneCount,
                clearedZoneCount,

                populationAtRisk,
                evacuatedPopulation,
                remainingToEvacuate,

                shelters.size(),
                openShelterCount,
                fullShelterCount,
                closedShelterCount,

                shelterCapacity,
                shelterOccupancy,
                remainingShelterCapacity,

                zones,
                shelters
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse createZone(
            UUID incidentId,
            CreateEvacuationZoneRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        if (
                request.evacuatedPopulation()
                > request.populationAtRisk()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Evacuated population cannot exceed population at risk."
            );
        }

        IncidentActionPriority priority =
                IncidentActionPriority.fromValue(
                        request.priority()
                );

        EvacuationZoneStatus status =
                deriveZoneStatus(
                        request.populationAtRisk(),
                        request.evacuatedPopulation()
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        EvacuationZone zone =
                repository.createZone(
                        UUID.randomUUID(),
                        incidentId,
                        request,
                        priority,
                        status,
                        owner
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "EVACUATION_ZONE_CREATED",
                "Evacuation zone created: '"
                        + zone.name()
                        + "' with "
                        + zone.populationAtRisk()
                        + " people at risk."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse updateZoneProgress(
            UUID incidentId,
            UUID zoneId,
            UpdateEvacuationProgressRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        EvacuationZone before =
                requireActiveZone(
                        incidentId,
                        zoneId
                );

        int evacuatedPopulation =
                request.evacuatedPopulation();

        if (
                evacuatedPopulation
                > before.populationAtRisk()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Evacuated population cannot exceed population at risk."
            );
        }

        EvacuationZoneStatus status =
                deriveZoneStatus(
                        before.populationAtRisk(),
                        evacuatedPopulation
                );

        EvacuationZone after =
                repository.updateZoneProgress(
                        incidentId,
                        zoneId,
                        evacuatedPopulation,
                        status
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "EVACUATION_PROGRESS_CHANGED",
                "Evacuation progress for '"
                        + before.name()
                        + "' changed from "
                        + before.evacuatedPopulation()
                        + " to "
                        + after.evacuatedPopulation()
                        + " of "
                        + after.populationAtRisk()
                        + ". Status is "
                        + after.status().name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse updateZoneStatus(
            UUID incidentId,
            UUID zoneId,
            UpdateEvacuationZoneStatusRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        EvacuationZone before =
                requireZone(
                        incidentId,
                        zoneId
                );

        EvacuationZoneStatus status =
                EvacuationZoneStatus.fromValue(
                        request.status()
                );

        if (
                before.status()
                == status
        ) {
            return board(
                    incidentId
            );
        }

        if (
                status
                == EvacuationZoneStatus.CLEARED
                && before.evacuatedPopulation()
                < before.populationAtRisk()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A zone cannot be CLEARED before the full at-risk population is evacuated."
            );
        }

        repository.updateZoneStatus(
                incidentId,
                zoneId,
                status
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "EVACUATION_STATUS_CHANGED",
                "Evacuation zone '"
                        + before.name()
                        + "' status changed from "
                        + before.status().name()
                        + " to "
                        + status.name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse updateZoneOwner(
            UUID incidentId,
            UUID zoneId,
            UpdateEvacuationOwnerRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        EvacuationZone before =
                requireActiveZone(
                        incidentId,
                        zoneId
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        if (
                before.owner()
                        .equals(owner)
        ) {
            return board(
                    incidentId
            );
        }

        repository.updateZoneOwner(
                incidentId,
                zoneId,
                owner
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "EVACUATION_OWNER_CHANGED",
                "Evacuation zone '"
                        + before.name()
                        + "' owner changed from '"
                        + before.owner()
                        + "' to '"
                        + owner
                        + "'."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse deleteZone(
            UUID incidentId,
            UUID zoneId
    ) {
        requireMutableIncident(
                incidentId
        );

        EvacuationZone zone =
                requireZone(
                        incidentId,
                        zoneId
                );

        int deleted =
                repository.deleteZone(
                        incidentId,
                        zoneId
                );

        if (deleted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Evacuation zone not found."
            );
        }

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "EVACUATION_ZONE_DELETED",
                "Evacuation zone deleted: '"
                        + zone.name()
                        + "'."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse createShelter(
            UUID incidentId,
            CreateShelterRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        if (
                request.occupancy()
                > request.capacity()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Shelter occupancy cannot exceed capacity."
            );
        }

        ShelterStatus status =
                request.occupancy()
                        .equals(
                                request.capacity()
                        )
                        ? ShelterStatus.FULL
                        : ShelterStatus.OPEN;

        String owner =
                normalizeOwner(
                        request.owner()
                );

        IncidentShelter shelter =
                repository.createShelter(
                        UUID.randomUUID(),
                        incidentId,
                        request,
                        status,
                        owner
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "SHELTER_CREATED",
                "Shelter created: '"
                        + shelter.name()
                        + "' with capacity "
                        + shelter.capacity()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse updateShelterOccupancy(
            UUID incidentId,
            UUID shelterId,
            UpdateShelterOccupancyRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentShelter before =
                requireOpenShelter(
                        incidentId,
                        shelterId
                );

        int occupancy =
                request.occupancy();

        if (
                occupancy
                > before.capacity()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Shelter occupancy cannot exceed capacity."
            );
        }

        ShelterStatus status =
                occupancy
                        == before.capacity()
                        ? ShelterStatus.FULL
                        : ShelterStatus.OPEN;

        IncidentShelter after =
                repository.updateShelterOccupancy(
                        incidentId,
                        shelterId,
                        occupancy,
                        status
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "SHELTER_OCCUPANCY_CHANGED",
                "Shelter '"
                        + before.name()
                        + "' occupancy changed from "
                        + before.occupancy()
                        + " to "
                        + after.occupancy()
                        + " of "
                        + after.capacity()
                        + ". Status is "
                        + after.status().name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse updateShelterStatus(
            UUID incidentId,
            UUID shelterId,
            UpdateShelterStatusRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentShelter before =
                requireShelter(
                        incidentId,
                        shelterId
                );

        ShelterStatus requested =
                ShelterStatus.fromValue(
                        request.status()
                );

        ShelterStatus status =
                requested;

        if (
                requested
                == ShelterStatus.OPEN
                && before.occupancy()
                == before.capacity()
        ) {
            status =
                    ShelterStatus.FULL;
        }

        if (
                requested
                == ShelterStatus.FULL
                && before.occupancy()
                != before.capacity()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A shelter can only be FULL when occupancy equals capacity."
            );
        }

        if (
                before.status()
                == status
        ) {
            return board(
                    incidentId
            );
        }

        repository.updateShelterStatus(
                incidentId,
                shelterId,
                status
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "SHELTER_STATUS_CHANGED",
                "Shelter '"
                        + before.name()
                        + "' status changed from "
                        + before.status().name()
                        + " to "
                        + status.name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse updateShelterOwner(
            UUID incidentId,
            UUID shelterId,
            UpdateShelterOwnerRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentShelter before =
                requireShelter(
                        incidentId,
                        shelterId
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        if (
                before.owner()
                        .equals(owner)
        ) {
            return board(
                    incidentId
            );
        }

        repository.updateShelterOwner(
                incidentId,
                shelterId,
                owner
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "SHELTER_OWNER_CHANGED",
                "Shelter '"
                        + before.name()
                        + "' owner changed from '"
                        + before.owner()
                        + "' to '"
                        + owner
                        + "'."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public EvacuationOperationsBoardResponse deleteShelter(
            UUID incidentId,
            UUID shelterId
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentShelter shelter =
                requireShelter(
                        incidentId,
                        shelterId
                );

        int deleted =
                repository.deleteShelter(
                        incidentId,
                        shelterId
                );

        if (deleted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Shelter not found."
            );
        }

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "SHELTER_DELETED",
                "Shelter deleted: '"
                        + shelter.name()
                        + "'."
        );

        return board(
                incidentId
        );
    }

    private IncidentCase requireIncident(
            UUID incidentId
    ) {
        return incidentRepository
                .findById(
                        incidentId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Incident "
                                        + incidentId
                                        + " was not found."
                        )
                );
    }

    private void requireMutableIncident(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireIncident(
                        incidentId
                );

        if (
                incident.status()
                == IncidentStatus.CLOSED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Closed incidents cannot be modified."
            );
        }
    }

    private EvacuationZone requireZone(
            UUID incidentId,
            UUID zoneId
    ) {
        return repository
                .findZoneById(
                        incidentId,
                        zoneId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Evacuation zone "
                                        + zoneId
                                        + " was not found."
                        )
                );
    }

    private EvacuationZone requireActiveZone(
            UUID incidentId,
            UUID zoneId
    ) {
        EvacuationZone zone =
                requireZone(
                        incidentId,
                        zoneId
                );

        if (
                zone.status()
                == EvacuationZoneStatus.CANCELLED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cancelled evacuation zones cannot be modified."
            );
        }

        return zone;
    }

    private IncidentShelter requireShelter(
            UUID incidentId,
            UUID shelterId
    ) {
        return repository
                .findShelterById(
                        incidentId,
                        shelterId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Shelter "
                                        + shelterId
                                        + " was not found."
                        )
                );
    }

    private IncidentShelter requireOpenShelter(
            UUID incidentId,
            UUID shelterId
    ) {
        IncidentShelter shelter =
                requireShelter(
                        incidentId,
                        shelterId
                );

        if (
                shelter.status()
                == ShelterStatus.CLOSED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Closed shelters cannot accept occupancy updates."
            );
        }

        return shelter;
    }

    private EvacuationZoneStatus deriveZoneStatus(
            int populationAtRisk,
            int evacuatedPopulation
    ) {
        if (
                evacuatedPopulation
                == populationAtRisk
        ) {
            return EvacuationZoneStatus.CLEARED;
        }

        if (
                evacuatedPopulation > 0
        ) {
            return EvacuationZoneStatus.EVACUATING;
        }

        return EvacuationZoneStatus.MONITORING;
    }

    private int countShelterStatus(
            List<IncidentShelter> shelters,
            ShelterStatus status
    ) {
        return (int) shelters
                .stream()
                .filter(shelter ->
                        shelter.status()
                                == status
                )
                .count();
    }

    private String normalizeOwner(
            String owner
    ) {
        if (
                owner == null
                || owner.isBlank()
        ) {
            return "Unassigned";
        }

        return owner.trim();
    }
}
