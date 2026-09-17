package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateImprovementActionRequest;
import com.terrascope.platform.api.model.ImprovementActionBoardResponse;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentImprovementAction;
import com.terrascope.platform.api.model.UpdateImprovementDueDateRequest;
import com.terrascope.platform.api.model.UpdateImprovementOwnerRequest;
import com.terrascope.platform.api.model.UpdateImprovementPriorityRequest;
import com.terrascope.platform.api.model.UpdateImprovementStatusRequest;
import com.terrascope.platform.domain.ImprovementActionStatus;
import com.terrascope.platform.domain.ImprovementCategory;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentImprovementActionRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentImprovementActionService {

    private final IncidentImprovementActionRepository repository;
    private final IncidentResponseRepository incidentRepository;

    public IncidentImprovementActionService(
            IncidentImprovementActionRepository repository,
            IncidentResponseRepository incidentRepository
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
    }

    public ImprovementActionBoardResponse board(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        List<IncidentImprovementAction> actions =
                repository.findAll(
                        incidentId
                );

        Instant now =
                Instant.now();

        int openCount =
                countStatus(
                        actions,
                        ImprovementActionStatus.OPEN
                );

        int inProgressCount =
                countStatus(
                        actions,
                        ImprovementActionStatus.IN_PROGRESS
                );

        int completedCount =
                countStatus(
                        actions,
                        ImprovementActionStatus.COMPLETED
                );

        int dismissedCount =
                countStatus(
                        actions,
                        ImprovementActionStatus.DISMISSED
                );

        int overdueCount =
                (int) actions
                        .stream()
                        .filter(action ->
                                action.status().active()
                                && action.dueAt() != null
                                && action.dueAt()
                                        .isBefore(
                                                now
                                        )
                        )
                        .count();

        return new ImprovementActionBoardResponse(
                incidentId,

                actions.size(),
                openCount,
                inProgressCount,
                completedCount,
                dismissedCount,
                overdueCount,

                actions
        );
    }

    @Transactional
    public ImprovementActionBoardResponse create(
            UUID incidentId,
            CreateImprovementActionRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        if (
                !repository.reviewBelongsToIncident(
                        incidentId,
                        request.afterActionReviewId()
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "After-action review does not belong to this incident."
            );
        }

        ImprovementCategory category =
                ImprovementCategory.fromValue(
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

        IncidentImprovementAction action =
                repository.create(
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
                "IMPROVEMENT_ACTION_CREATED",
                "Improvement action created from after-action review: '"
                        + action.title()
                        + "' ("
                        + action.category().name()
                        + ", "
                        + action.priority().name()
                        + ")."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public ImprovementActionBoardResponse updateStatus(
            UUID incidentId,
            UUID actionId,
            UpdateImprovementStatusRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentImprovementAction before =
                requireAction(
                        incidentId,
                        actionId
                );

        ImprovementActionStatus status =
                ImprovementActionStatus.fromValue(
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

        IncidentImprovementAction after =
                repository.updateStatus(
                        incidentId,
                        actionId,
                        status
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "IMPROVEMENT_STATUS_CHANGED",
                "Improvement action '"
                        + before.title()
                        + "' status changed from "
                        + before.status().name()
                        + " to "
                        + after.status().name()
                        + "."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public ImprovementActionBoardResponse updatePriority(
            UUID incidentId,
            UUID actionId,
            UpdateImprovementPriorityRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentImprovementAction before =
                requireAction(
                        incidentId,
                        actionId
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

        repository.updatePriority(
                incidentId,
                actionId,
                priority
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "IMPROVEMENT_PRIORITY_CHANGED",
                "Improvement action '"
                        + before.title()
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
    public ImprovementActionBoardResponse updateOwner(
            UUID incidentId,
            UUID actionId,
            UpdateImprovementOwnerRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentImprovementAction before =
                requireAction(
                        incidentId,
                        actionId
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

        repository.updateOwner(
                incidentId,
                actionId,
                owner
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "IMPROVEMENT_OWNER_CHANGED",
                "Improvement action '"
                        + before.title()
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
    public ImprovementActionBoardResponse updateDueAt(
            UUID incidentId,
            UUID actionId,
            UpdateImprovementDueDateRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentImprovementAction before =
                requireAction(
                        incidentId,
                        actionId
                );

        Instant beforeDue =
                before.dueAt();

        Instant afterDue =
                request.dueAt();

        if (
                beforeDue == null
                ? afterDue == null
                : beforeDue.equals(
                        afterDue
                )
        ) {
            return board(
                    incidentId
            );
        }

        repository.updateDueAt(
                incidentId,
                actionId,
                afterDue
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "IMPROVEMENT_DUE_CHANGED",
                "Improvement action '"
                        + before.title()
                        + "' due date was updated."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public ImprovementActionBoardResponse delete(
            UUID incidentId,
            UUID actionId
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentImprovementAction action =
                requireAction(
                        incidentId,
                        actionId
                );

        int deleted =
                repository.delete(
                        incidentId,
                        actionId
                );

        if (
                deleted != 1
        ) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Improvement action "
                            + actionId
                            + " was not found."
            );
        }

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "IMPROVEMENT_ACTION_DELETED",
                "Improvement action deleted: '"
                        + action.title()
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

    private void requireClosedIncident(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireIncident(
                        incidentId
                );

        if (
                incident.status()
                != IncidentStatus.CLOSED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Improvement actions require a CLOSED incident."
            );
        }
    }

    private IncidentImprovementAction requireAction(
            UUID incidentId,
            UUID actionId
    ) {
        return repository
                .findById(
                        incidentId,
                        actionId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Improvement action "
                                        + actionId
                                        + " was not found."
                        )
                );
    }

    private int countStatus(
            List<IncidentImprovementAction> actions,
            ImprovementActionStatus status
    ) {
        return (int) actions
                .stream()
                .filter(action ->
                        action.status()
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
