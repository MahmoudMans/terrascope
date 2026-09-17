package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.ShelterStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentShelter(
        UUID id,
        UUID incidentId,
        String name,
        String address,
        int capacity,
        int occupancy,
        ShelterStatus status,
        String owner,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
}
