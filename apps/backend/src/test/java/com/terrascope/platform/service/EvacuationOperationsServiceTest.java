package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateEvacuationZoneRequest;
import com.terrascope.platform.api.model.CreateShelterRequest;
import com.terrascope.platform.api.model.EvacuationOperationsBoardResponse;
import com.terrascope.platform.api.model.EvacuationZone;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentShelter;
import com.terrascope.platform.api.model.UpdateEvacuationProgressRequest;
import com.terrascope.platform.api.model.UpdateShelterOccupancyRequest;
import com.terrascope.platform.domain.EvacuationZoneStatus;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.ShelterStatus;
import com.terrascope.platform.repository.EvacuationOperationsRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvacuationOperationsServiceTest {

    private EvacuationOperationsRepository repository;
    private IncidentResponseRepository incidentRepository;
    private EvacuationOperationsService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        EvacuationOperationsRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new EvacuationOperationsService(
                        repository,
                        incidentRepository
                );

        incidentId =
                UUID.randomUUID();

        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        incident()
                )
        );
    }

    @Test
    void boardCalculatesEvacuationAndCapacityTotals() {
        when(
                repository.findZones(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        zone(
                                EvacuationZoneStatus.EVACUATING,
                                1000,
                                400
                        ),
                        zone(
                                EvacuationZoneStatus.CLEARED,
                                500,
                                500
                        )
                )
        );

        when(
                repository.findShelters(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        shelter(
                                ShelterStatus.OPEN,
                                500,
                                200
                        ),
                        shelter(
                                ShelterStatus.FULL,
                                100,
                                100
                        )
                )
        );

        EvacuationOperationsBoardResponse board =
                service.board(
                        incidentId
                );

        assertThat(
                board.populationAtRisk()
        ).isEqualTo(1500);

        assertThat(
                board.evacuatedPopulation()
        ).isEqualTo(900);

        assertThat(
                board.remainingToEvacuate()
        ).isEqualTo(600);

        assertThat(
                board.shelterCapacity()
        ).isEqualTo(600);

        assertThat(
                board.shelterOccupancy()
        ).isEqualTo(300);

        assertThat(
                board.remainingShelterCapacity()
        ).isEqualTo(300);
    }

    @Test
    void evacuationProgressCannotExceedPopulationAtRisk() {
        EvacuationZone zone =
                zone(
                        EvacuationZoneStatus.EVACUATING,
                        100,
                        50
                );

        when(
                repository.findZoneById(
                        incidentId,
                        zone.id()
                )
        ).thenReturn(
                Optional.of(
                        zone
                )
        );

        assertThatThrownBy(() ->
                service.updateZoneProgress(
                        incidentId,
                        zone.id(),
                        new UpdateEvacuationProgressRequest(
                                101
                        )
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void fullEvacuationAutomaticallyClearsZone() {
        EvacuationZone before =
                zone(
                        EvacuationZoneStatus.EVACUATING,
                        100,
                        80
                );

        EvacuationZone after =
                zone(
                        EvacuationZoneStatus.CLEARED,
                        100,
                        100
                );

        when(
                repository.findZoneById(
                        incidentId,
                        before.id()
                )
        ).thenReturn(
                Optional.of(
                        before
                )
        );

        when(
                repository.updateZoneProgress(
                        incidentId,
                        before.id(),
                        100,
                        EvacuationZoneStatus.CLEARED
                )
        ).thenReturn(
                after
        );

        when(
                repository.findZones(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        after
                )
        );

        when(
                repository.findShelters(
                        incidentId
                )
        ).thenReturn(
                List.of()
        );

        service.updateZoneProgress(
                incidentId,
                before.id(),
                new UpdateEvacuationProgressRequest(
                        100
                )
        );

        verify(
                repository
        ).updateZoneProgress(
                incidentId,
                before.id(),
                100,
                EvacuationZoneStatus.CLEARED
        );
    }

    @Test
    void shelterAtCapacityAutomaticallyBecomesFull() {
        IncidentShelter before =
                shelter(
                        ShelterStatus.OPEN,
                        100,
                        80
                );

        IncidentShelter after =
                shelter(
                        ShelterStatus.FULL,
                        100,
                        100
                );

        when(
                repository.findShelterById(
                        incidentId,
                        before.id()
                )
        ).thenReturn(
                Optional.of(
                        before
                )
        );

        when(
                repository.updateShelterOccupancy(
                        incidentId,
                        before.id(),
                        100,
                        ShelterStatus.FULL
                )
        ).thenReturn(
                after
        );

        when(
                repository.findZones(
                        incidentId
                )
        ).thenReturn(
                List.of()
        );

        when(
                repository.findShelters(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        after
                )
        );

        service.updateShelterOccupancy(
                incidentId,
                before.id(),
                new UpdateShelterOccupancyRequest(
                        100
                )
        );

        verify(
                repository
        ).updateShelterOccupancy(
                incidentId,
                before.id(),
                100,
                ShelterStatus.FULL
        );
    }

    private IncidentCase incident() {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentCase(
                incidentId,
                null,
                "source-1",
                "wildfires",
                "Test incident",
                "",
                36.8,
                10.2,
                IncidentSeverity.HIGH,
                IncidentStatus.RESPONDING,
                "Command",
                0,
                now,
                now,
                null
        );
    }

    private EvacuationZone zone(
            EvacuationZoneStatus status,
            int population,
            int evacuated
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new EvacuationZone(
                UUID.randomUUID(),
                incidentId,
                "Zone Alpha",
                "",
                IncidentActionPriority.HIGH,
                status,
                population,
                evacuated,
                "Evac Alpha",
                now,
                now,
                status
                        == EvacuationZoneStatus.CLEARED
                        ? now
                        : null,
                status
                        == EvacuationZoneStatus.CANCELLED
                        ? now
                        : null
        );
    }

    private IncidentShelter shelter(
            ShelterStatus status,
            int capacity,
            int occupancy
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentShelter(
                UUID.randomUUID(),
                incidentId,
                "Shelter Alpha",
                "",
                capacity,
                occupancy,
                status,
                "Shelter Ops",
                now,
                now,
                status
                        == ShelterStatus.CLOSED
                        ? now
                        : null
        );
    }
}
