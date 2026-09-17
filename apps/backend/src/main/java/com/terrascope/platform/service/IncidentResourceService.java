package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateIncidentResourceRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentResourceBoardResponse;
import com.terrascope.platform.api.model.IncidentResourceRequest;
import com.terrascope.platform.api.model.UpdateIncidentResourceAllocationRequest;
import com.terrascope.platform.api.model.UpdateIncidentResourceOwnerRequest;
import com.terrascope.platform.api.model.UpdateIncidentResourcePriorityRequest;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentResourceCategory;
import com.terrascope.platform.domain.IncidentResourceStatus;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentResourceRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class IncidentResourceService {

    private final IncidentResourceRepository resourceRepository;
    private final IncidentResponseRepository incidentRepository;

    public IncidentResourceService(
            IncidentResourceRepository resourceRepository,
            IncidentResponseRepository incidentRepository
    ) {
        this.resourceRepository = resourceRepository;
        this.incidentRepository = incidentRepository;
    }

    public IncidentResourceBoardResponse board(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        List<IncidentResourceRequest> resources =
                resourceRepository.findAll(
                        incidentId
                );

        int requestedCount =
                count(
                        resources,
                        IncidentResourceStatus.REQUESTED
                );

        int partiallyAllocatedCount =
                count(
                        resources,
                        IncidentResourceStatus.PARTIALLY_ALLOCATED
                );

        int fulfilledCount =
                count(
                        resources,
                        IncidentResourceStatus.FULFILLED
                );

        int cancelledCount =
                count(
                        resources,
                        IncidentResourceStatus.CANCELLED
                );

        int shortageCount =
                (int) resources
                        .stream()
                        .filter(resource ->
                                resource.status()
                                        != IncidentResourceStatus.CANCELLED
                                && resource.status()
                                        != IncidentResourceStatus.FULFILLED
                                && resource.allocatedQuantity()
                                        < resource.requestedQuantity()
                        )
                        .count();

        return new IncidentResourceBoardResponse(
                incidentId,
                resources.size(),
                requestedCount,
                partiallyAllocatedCount,
                fulfilledCount,
                cancelledCount,
                shortageCount,
                resources
        );
    }

    @Transactional
    public IncidentResourceBoardResponse create(
            UUID incidentId,
            CreateIncidentResourceRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentResourceCategory category =
                IncidentResourceCategory.fromValue(
                        request.category()
                );

        IncidentActionPriority priority =
                IncidentActionPriority.fromValue(
                        request.priority()
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        IncidentResourceRequest resource =
                resourceRepository.create(
                        UUID.randomUUID(),
                        incidentId,
                        request,
                        category,
                        priority,
                        owner
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "RESOURCE_REQUESTED",
                "Resource requested: "
                        + resource.requestedQuantity()
                        + " "
                        + resource.unit()
                        + " of '"
                        + resource.name()
                        + "' ("
                        + resource.priority().name()
                        + ")."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public IncidentResourceBoardResponse updateAllocation(
            UUID incidentId,
            UUID resourceId,
            UpdateIncidentResourceAllocationRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentResourceRequest before =
                requireActiveResource(
                        incidentId,
                        resourceId
                );

        int allocatedQuantity =
                request.allocatedQuantity();

        if (
                allocatedQuantity
                > before.requestedQuantity()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Allocated quantity cannot exceed requested quantity."
            );
        }

        if (
                before.allocatedQuantity()
                == allocatedQuantity
        ) {
            return board(
                    incidentId
            );
        }

        IncidentResourceRequest after =
                resourceRepository.updateAllocation(
                        incidentId,
                        resourceId,
                        allocatedQuantity
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "RESOURCE_ALLOCATION_CHANGED",
                "Resource '"
                        + before.name()
                        + "' allocation changed from "
                        + before.allocatedQuantity()
                        + " to "
                        + after.allocatedQuantity()
                        + " "
                        + after.unit()
                        + ". Status is now "
                        + after.status().name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public IncidentResourceBoardResponse updatePriority(
            UUID incidentId,
            UUID resourceId,
            UpdateIncidentResourcePriorityRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentResourceRequest before =
                requireActiveResource(
                        incidentId,
                        resourceId
                );

        IncidentActionPriority priority =
                IncidentActionPriority.fromValue(
                        request.priority()
                );

        if (
                before.priority()
                == priority
        ) {
            return board(
                    incidentId
            );
        }

        resourceRepository.updatePriority(
                incidentId,
                resourceId,
                priority
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "RESOURCE_PRIORITY_CHANGED",
                "Resource '"
                        + before.name()
                        + "' priority changed from "
                        + before.priority().name()
                        + " to "
                        + priority.name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public IncidentResourceBoardResponse updateOwner(
            UUID incidentId,
            UUID resourceId,
            UpdateIncidentResourceOwnerRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentResourceRequest before =
                requireActiveResource(
                        incidentId,
                        resourceId
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

        resourceRepository.updateOwner(
                incidentId,
                resourceId,
                owner
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "RESOURCE_OWNER_CHANGED",
                "Resource '"
                        + before.name()
                        + "' logistics owner changed from '"
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
    public IncidentResourceBoardResponse cancel(
            UUID incidentId,
            UUID resourceId
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentResourceRequest before =
                requireResource(
                        incidentId,
                        resourceId
                );

        if (
                before.status()
                == IncidentResourceStatus.CANCELLED
        ) {
            return board(
                    incidentId
            );
        }

        resourceRepository.cancel(
                incidentId,
                resourceId
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "RESOURCE_CANCELLED",
                "Resource request cancelled: '"
                        + before.name()
                        + "'."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public IncidentResourceBoardResponse delete(
            UUID incidentId,
            UUID resourceId
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentResourceRequest resource =
                requireResource(
                        incidentId,
                        resourceId
                );

        int deleted =
                resourceRepository.delete(
                        incidentId,
                        resourceId
                );

        if (deleted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Resource "
                            + resourceId
                            + " was not found."
            );
        }

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "RESOURCE_DELETED",
                "Resource request deleted: '"
                        + resource.name()
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

    private IncidentCase requireMutableIncident(
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

        return incident;
    }

    private IncidentResourceRequest requireResource(
            UUID incidentId,
            UUID resourceId
    ) {
        return resourceRepository
                .findById(
                        incidentId,
                        resourceId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Resource "
                                        + resourceId
                                        + " was not found."
                        )
                );
    }

    private IncidentResourceRequest requireActiveResource(
            UUID incidentId,
            UUID resourceId
    ) {
        IncidentResourceRequest resource =
                requireResource(
                        incidentId,
                        resourceId
                );

        if (
                resource.status()
                == IncidentResourceStatus.CANCELLED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cancelled resource requests cannot be modified."
            );
        }

        return resource;
    }

    private int count(
            List<IncidentResourceRequest> resources,
            IncidentResourceStatus status
    ) {
        return (int) resources
                .stream()
                .filter(resource ->
                        resource.status()
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
