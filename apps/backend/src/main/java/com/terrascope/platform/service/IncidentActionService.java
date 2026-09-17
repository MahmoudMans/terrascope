package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateIncidentActionRequest;
import com.terrascope.platform.api.model.IncidentActionBoardResponse;
import com.terrascope.platform.api.model.IncidentActionItem;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.UpdateIncidentActionDueDateRequest;
import com.terrascope.platform.api.model.UpdateIncidentActionOwnerRequest;
import com.terrascope.platform.api.model.UpdateIncidentActionPriorityRequest;
import com.terrascope.platform.api.model.UpdateIncidentActionStatusRequest;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentActionStatus;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentActionRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentActionService {

    private final IncidentActionRepository actionRepository;
    private final IncidentResponseRepository incidentRepository;

    public IncidentActionService(
            IncidentActionRepository actionRepository,
            IncidentResponseRepository incidentRepository
    ) {
        this.actionRepository = actionRepository;
        this.incidentRepository = incidentRepository;
    }

    public IncidentActionBoardResponse board(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        List<IncidentActionItem> actions =
                actionRepository.findAll(
                        incidentId
                );

        Instant now =
                Instant.now();

        int todoCount =
                count(
                        actions,
                        IncidentActionStatus.TODO
                );

        int inProgressCount =
                count(
                        actions,
                        IncidentActionStatus.IN_PROGRESS
                );

        int blockedCount =
                count(
                        actions,
                        IncidentActionStatus.BLOCKED
                );

        int doneCount =
                count(
                        actions,
                        IncidentActionStatus.DONE
                );

        int overdueCount =
                (int) actions
                        .stream()
                        .filter(action ->
                                action.status()
                                        != IncidentActionStatus.DONE
                                && action.dueAt()
                                        != null
                                && action
                                        .dueAt()
                                        .isBefore(
                                                now
                                        )
                        )
                        .count();

        return new IncidentActionBoardResponse(
                incidentId,
                actions.size(),
                todoCount,
                inProgressCount,
                blockedCount,
                doneCount,
                overdueCount,
                actions
        );
    }

    @Transactional
    public IncidentActionBoardResponse create(
            UUID incidentId,
            CreateIncidentActionRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentActionPriority priority =
                IncidentActionPriority.fromValue(
                        request.priority()
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        IncidentActionItem action =
                actionRepository.create(
                        UUID.randomUUID(),
                        incidentId,
                        request,
                        priority,
                        owner
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "ACTION_CREATED",
                "Response action created: '"
                        + action.title()
                        + "' with "
                        + action.priority().name()
                        + " priority."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public IncidentActionBoardResponse updateStatus(
            UUID incidentId,
            UUID actionId,
            UpdateIncidentActionStatusRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentActionItem before =
                requireAction(
                        incidentId,
                        actionId
                );

        IncidentActionStatus status =
                IncidentActionStatus.fromValue(
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

        actionRepository.updateStatus(
                incidentId,
                actionId,
                status
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "ACTION_STATUS_CHANGED",
                "Action '"
                        + before.title()
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
    public IncidentActionBoardResponse updatePriority(
            UUID incidentId,
            UUID actionId,
            UpdateIncidentActionPriorityRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentActionItem before =
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

        actionRepository.updatePriority(
                incidentId,
                actionId,
                priority
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "ACTION_PRIORITY_CHANGED",
                "Action '"
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
    public IncidentActionBoardResponse updateOwner(
            UUID incidentId,
            UUID actionId,
            UpdateIncidentActionOwnerRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentActionItem before =
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

        actionRepository.updateOwner(
                incidentId,
                actionId,
                owner
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "ACTION_OWNER_CHANGED",
                "Action '"
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
    public IncidentActionBoardResponse updateDueAt(
            UUID incidentId,
            UUID actionId,
            UpdateIncidentActionDueDateRequest request
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentActionItem before =
                requireAction(
                        incidentId,
                        actionId
                );

        actionRepository.updateDueAt(
                incidentId,
                actionId,
                request.dueAt()
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "ACTION_DUE_CHANGED",
                "Action '"
                        + before.title()
                        + "' due date updated."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public IncidentActionBoardResponse delete(
            UUID incidentId,
            UUID actionId
    ) {
        requireMutableIncident(
                incidentId
        );

        IncidentActionItem action =
                requireAction(
                        incidentId,
                        actionId
                );

        int deleted =
                actionRepository.delete(
                        incidentId,
                        actionId
                );

        if (deleted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Action "
                            + actionId
                            + " was not found."
            );
        }

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "ACTION_DELETED",
                "Response action deleted: '"
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

    private IncidentActionItem requireAction(
            UUID incidentId,
            UUID actionId
    ) {
        return actionRepository
                .findById(
                        incidentId,
                        actionId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Action "
                                        + actionId
                                        + " was not found."
                        )
                );
    }

    private int count(
            List<IncidentActionItem> actions,
            IncidentActionStatus status
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
