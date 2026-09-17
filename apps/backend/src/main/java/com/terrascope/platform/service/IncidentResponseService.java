package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AddIncidentNoteRequest;
import com.terrascope.platform.api.model.CreateIncidentRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentDashboardResponse;
import com.terrascope.platform.api.model.IncidentDetail;
import com.terrascope.platform.api.model.UpdateIncidentOwnerRequest;
import com.terrascope.platform.api.model.UpdateIncidentSeverityRequest;
import com.terrascope.platform.api.model.UpdateIncidentStatusRequest;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class IncidentResponseService {

    private final IncidentResponseRepository repository;

    public IncidentResponseService(
            IncidentResponseRepository repository
    ) {
        this.repository = repository;
    }

    public IncidentDashboardResponse dashboard() {
        List<IncidentCase> incidents =
                repository.findAll();

        int activeCount =
                (int) incidents
                        .stream()
                        .filter(incident ->
                                incident.status()
                                        != IncidentStatus.CLOSED
                        )
                        .count();

        int respondingCount =
                (int) incidents
                        .stream()
                        .filter(incident ->
                                incident.status()
                                        == IncidentStatus.RESPONDING
                        )
                        .count();

        int criticalCount =
                (int) incidents
                        .stream()
                        .filter(incident ->
                                incident.severity()
                                        == IncidentSeverity.CRITICAL
                                && incident.status()
                                        != IncidentStatus.CLOSED
                        )
                        .count();

        int closedCount =
                (int) incidents
                        .stream()
                        .filter(incident ->
                                incident.status()
                                        == IncidentStatus.CLOSED
                        )
                        .count();

        return new IncidentDashboardResponse(
                incidents.size(),
                activeCount,
                respondingCount,
                criticalCount,
                closedCount,
                incidents
        );
    }

    public IncidentDetail detail(
            UUID id
    ) {
        IncidentCase incident =
                requireIncident(
                        id
                );

        return new IncidentDetail(
                incident,
                repository.timeline(
                        id
                )
        );
    }

    @Transactional
    public IncidentDetail create(
            CreateIncidentRequest request
    ) {
        String hazardType =
                HazardType
                        .fromKey(
                                request.hazardType()
                        )
                        .key();

        String sourceEventId =
                request
                        .sourceEventId()
                        .trim();

        IncidentCase existing =
                repository
                        .findBySource(
                                hazardType,
                                sourceEventId
                        )
                        .orElse(null);

        if (existing != null) {
            return detail(
                    existing.id()
            );
        }

        IncidentSeverity severity =
                IncidentSeverity.fromValue(
                        request.severity()
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        IncidentCase incident =
                repository.create(
                        UUID.randomUUID(),
                        request,
                        hazardType,
                        severity,
                        owner
                );

        repository.appendTimeline(
                UUID.randomUUID(),
                incident.id(),
                "INCIDENT_OPENED",
                "Incident opened for "
                        + hazardType
                        + " event '"
                        + incident.title()
                        + "'."
        );

        return detail(
                incident.id()
        );
    }

    @Transactional
    public IncidentDetail updateStatus(
            UUID id,
            UpdateIncidentStatusRequest request
    ) {
        IncidentCase before =
                requireIncident(
                        id
                );

        IncidentStatus status =
                IncidentStatus.fromValue(
                        request.status()
                );

        if (
                before.status()
                == status
        ) {
            return detail(
                    id
            );
        }

        repository.updateStatus(
                id,
                status
        );

        repository.appendTimeline(
                UUID.randomUUID(),
                id,
                "STATUS_CHANGED",
                "Incident status changed from "
                        + before.status().name()
                        + " to "
                        + status.name()
                        + "."
        );

        return detail(
                id
        );
    }

    @Transactional
    public IncidentDetail updateSeverity(
            UUID id,
            UpdateIncidentSeverityRequest request
    ) {
        IncidentCase before =
                requireIncident(
                        id
                );

        IncidentSeverity severity =
                IncidentSeverity.fromValue(
                        request.severity()
                );

        if (
                before.severity()
                == severity
        ) {
            return detail(
                    id
            );
        }

        repository.updateSeverity(
                id,
                severity
        );

        repository.appendTimeline(
                UUID.randomUUID(),
                id,
                "SEVERITY_CHANGED",
                "Incident severity changed from "
                        + before.severity().name()
                        + " to "
                        + severity.name()
                        + "."
        );

        return detail(
                id
        );
    }

    @Transactional
    public IncidentDetail updateOwner(
            UUID id,
            UpdateIncidentOwnerRequest request
    ) {
        IncidentCase before =
                requireIncident(
                        id
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        if (
                before.owner()
                        .equals(owner)
        ) {
            return detail(
                    id
            );
        }

        repository.updateOwner(
                id,
                owner
        );

        repository.appendTimeline(
                UUID.randomUUID(),
                id,
                "OWNER_CHANGED",
                "Incident owner changed from '"
                        + before.owner()
                        + "' to '"
                        + owner
                        + "'."
        );

        return detail(
                id
        );
    }

    @Transactional
    public IncidentDetail addNote(
            UUID id,
            AddIncidentNoteRequest request
    ) {
        requireIncident(
                id
        );

        repository.appendTimeline(
                UUID.randomUUID(),
                id,
                "NOTE_ADDED",
                request
                        .message()
                        .trim()
        );

        return detail(
                id
        );
    }

    private IncidentCase requireIncident(
            UUID id
    ) {
        return repository
                .findById(
                        id
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Incident "
                                        + id
                                        + " was not found."
                        )
                );
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
