package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreatePreparednessCorrectiveActionRequest;
import com.terrascope.platform.api.model.ImprovementValidationRecord;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentPreparednessExercise;
import com.terrascope.platform.api.model.PreparednessCorrectiveAction;
import com.terrascope.platform.api.model.ResilienceValidationBoardResponse;
import com.terrascope.platform.api.model.UpdateCorrectiveActionDueDateRequest;
import com.terrascope.platform.api.model.UpdateCorrectiveActionOwnerRequest;
import com.terrascope.platform.api.model.UpdateCorrectiveActionStatusRequest;
import com.terrascope.platform.domain.CorrectiveActionStatus;
import com.terrascope.platform.domain.ImprovementValidationProjection;
import com.terrascope.platform.domain.ImprovementValidationStatus;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.PreparednessExerciseStatus;
import com.terrascope.platform.domain.PreparednessValidationResult;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.PreparednessExerciseRepository;
import com.terrascope.platform.repository.ResilienceValidationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ResilienceValidationService {

    private final ResilienceValidationRepository repository;
    private final PreparednessExerciseRepository preparednessRepository;
    private final IncidentResponseRepository incidentRepository;

    public ResilienceValidationService(
            ResilienceValidationRepository repository,
            PreparednessExerciseRepository preparednessRepository,
            IncidentResponseRepository incidentRepository
    ) {
        this.repository = repository;
        this.preparednessRepository = preparednessRepository;
        this.incidentRepository = incidentRepository;
    }

    public ResilienceValidationBoardResponse board(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        List<ImprovementValidationRecord> validations =
                repository
                        .findValidationProjections(
                                incidentId
                        )
                        .stream()
                        .map(
                                this::mapValidation
                        )
                        .toList();

        List<PreparednessCorrectiveAction> correctiveActions =
                repository.findCorrectiveActions(
                        incidentId
                );

        Instant now =
                Instant.now();

        int untestedCount =
                countValidation(
                        validations,
                        ImprovementValidationStatus.UNTESTED
                );

        int validatedCount =
                countValidation(
                        validations,
                        ImprovementValidationStatus.VALIDATED
                );

        int needsRevalidationCount =
                countValidation(
                        validations,
                        ImprovementValidationStatus.NEEDS_REVALIDATION
                );

        int failedCount =
                countValidation(
                        validations,
                        ImprovementValidationStatus.FAILED
                );

        int openCorrectiveActionCount =
                countCorrectiveStatus(
                        correctiveActions,
                        CorrectiveActionStatus.OPEN
                );

        int inProgressCorrectiveActionCount =
                countCorrectiveStatus(
                        correctiveActions,
                        CorrectiveActionStatus.IN_PROGRESS
                );

        int completedCorrectiveActionCount =
                countCorrectiveStatus(
                        correctiveActions,
                        CorrectiveActionStatus.COMPLETED
                );

        int waivedCorrectiveActionCount =
                countCorrectiveStatus(
                        correctiveActions,
                        CorrectiveActionStatus.WAIVED
                );

        int overdueCorrectiveActionCount =
                (int) correctiveActions
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

        double averageLatestReadinessScore =
                validations
                        .stream()
                        .map(
                                ImprovementValidationRecord::latestReadinessScore
                        )
                        .filter(
                                java.util.Objects::nonNull
                        )
                        .mapToInt(
                                Integer::intValue
                        )
                        .average()
                        .orElse(
                                0.0
                        );

        averageLatestReadinessScore =
                Math.round(
                        averageLatestReadinessScore
                        * 10.0
                ) / 10.0;

        return new ResilienceValidationBoardResponse(
                incidentId,

                validations.size(),

                untestedCount,
                validatedCount,
                needsRevalidationCount,
                failedCount,

                correctiveActions.size(),
                openCorrectiveActionCount,
                inProgressCorrectiveActionCount,
                completedCorrectiveActionCount,
                waivedCorrectiveActionCount,
                overdueCorrectiveActionCount,

                averageLatestReadinessScore,

                validations,
                correctiveActions
        );
    }

    @Transactional
    public ResilienceValidationBoardResponse createCorrectiveAction(
            UUID incidentId,
            UUID exerciseId,
            CreatePreparednessCorrectiveActionRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentPreparednessExercise exercise =
                preparednessRepository
                        .findById(
                                incidentId,
                                exerciseId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Preparedness exercise "
                                                + exerciseId
                                                + " was not found."
                                )
                        );

        if (
                exercise.status()
                != PreparednessExerciseStatus.COMPLETED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Corrective actions require a completed preparedness exercise."
            );
        }

        if (
                exercise.validationResult()
                != PreparednessValidationResult.PARTIAL
                && exercise.validationResult()
                != PreparednessValidationResult.FAILED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Corrective actions require a PARTIAL or FAILED preparedness result."
            );
        }

        IncidentActionPriority priority =
                IncidentActionPriority.fromValue(
                        request.priority()
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        PreparednessCorrectiveAction action =
                repository.createCorrectiveAction(
                        UUID.randomUUID(),
                        incidentId,
                        exercise.id(),
                        exercise.improvementActionId(),
                        request,
                        priority,
                        owner
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_CORRECTIVE_ACTION_CREATED",
                "Corrective action created from preparedness exercise '"
                        + exercise.title()
                        + "': '"
                        + action.title()
                        + "'."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public ResilienceValidationBoardResponse updateCorrectiveStatus(
            UUID incidentId,
            UUID correctiveActionId,
            UpdateCorrectiveActionStatusRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        PreparednessCorrectiveAction before =
                requireMutableCorrectiveAction(
                        incidentId,
                        correctiveActionId
                );

        CorrectiveActionStatus status =
                CorrectiveActionStatus.fromValue(
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

        PreparednessCorrectiveAction after =
                repository.updateCorrectiveStatus(
                        incidentId,
                        correctiveActionId,
                        status
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_CORRECTIVE_STATUS_CHANGED",
                "Corrective action '"
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
    public ResilienceValidationBoardResponse updateCorrectiveOwner(
            UUID incidentId,
            UUID correctiveActionId,
            UpdateCorrectiveActionOwnerRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        PreparednessCorrectiveAction before =
                requireMutableCorrectiveAction(
                        incidentId,
                        correctiveActionId
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

        repository.updateCorrectiveOwner(
                incidentId,
                correctiveActionId,
                owner
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_CORRECTIVE_OWNER_CHANGED",
                "Corrective action '"
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
    public ResilienceValidationBoardResponse updateCorrectiveDueAt(
            UUID incidentId,
            UUID correctiveActionId,
            UpdateCorrectiveActionDueDateRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        PreparednessCorrectiveAction before =
                requireMutableCorrectiveAction(
                        incidentId,
                        correctiveActionId
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

        repository.updateCorrectiveDueAt(
                incidentId,
                correctiveActionId,
                afterDue
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_CORRECTIVE_DUE_CHANGED",
                "Corrective action '"
                        + before.title()
                        + "' due date was updated."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public ResilienceValidationBoardResponse deleteCorrectiveAction(
            UUID incidentId,
            UUID correctiveActionId
    ) {
        requireClosedIncident(
                incidentId
        );

        PreparednessCorrectiveAction action =
                requireCorrectiveAction(
                        incidentId,
                        correctiveActionId
                );

        int deleted =
                repository.deleteCorrectiveAction(
                        incidentId,
                        correctiveActionId
                );

        if (
                deleted != 1
        ) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Corrective action "
                            + correctiveActionId
                            + " was not found."
            );
        }

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_CORRECTIVE_ACTION_DELETED",
                "Preparedness corrective action deleted: '"
                        + action.title()
                        + "'."
        );

        return board(
                incidentId
        );
    }

    private ImprovementValidationRecord mapValidation(
            ImprovementValidationProjection projection
    ) {
        ImprovementValidationStatus status =
                ImprovementValidationStatus.derive(
                        projection.latestValidationResult(),
                        projection.openCorrectiveActionCount()
                );

        return new ImprovementValidationRecord(
                projection.improvementActionId(),
                projection.title(),
                projection.priority(),

                projection.totalExerciseCount(),

                projection.latestExerciseId(),
                projection.latestValidationResult(),
                projection.latestReadinessScore(),

                projection.openCorrectiveActionCount(),

                status
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
                    "Resilience validation requires a CLOSED incident."
            );
        }
    }

    private PreparednessCorrectiveAction requireCorrectiveAction(
            UUID incidentId,
            UUID correctiveActionId
    ) {
        return repository
                .findCorrectiveAction(
                        incidentId,
                        correctiveActionId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Corrective action "
                                        + correctiveActionId
                                        + " was not found."
                        )
                );
    }

    private PreparednessCorrectiveAction requireMutableCorrectiveAction(
            UUID incidentId,
            UUID correctiveActionId
    ) {
        PreparednessCorrectiveAction action =
                requireCorrectiveAction(
                        incidentId,
                        correctiveActionId
                );

        if (
                action.status()
                        .terminal()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Completed or waived corrective actions are immutable."
            );
        }

        return action;
    }

    private int countValidation(
            List<ImprovementValidationRecord> validations,
            ImprovementValidationStatus status
    ) {
        return (int) validations
                .stream()
                .filter(validation ->
                        validation.validationStatus()
                                == status
                )
                .count();
    }

    private int countCorrectiveStatus(
            List<PreparednessCorrectiveAction> actions,
            CorrectiveActionStatus status
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
