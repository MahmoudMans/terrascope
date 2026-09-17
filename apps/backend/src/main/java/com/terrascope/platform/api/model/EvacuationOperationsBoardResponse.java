package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record EvacuationOperationsBoardResponse(
        UUID incidentId,

        int zoneCount,
        int activeZoneCount,
        int clearedZoneCount,

        long populationAtRisk,
        long evacuatedPopulation,
        long remainingToEvacuate,

        int shelterCount,
        int openShelterCount,
        int fullShelterCount,
        int closedShelterCount,

        long shelterCapacity,
        long shelterOccupancy,
        long remainingShelterCapacity,

        List<EvacuationZone> zones,
        List<IncidentShelter> shelters
) {
}
