package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AssessPreparednessExerciseRequest;
import com.terrascope.platform.api.model.CreatePreparednessExerciseRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentPreparednessExercise;
import com.terrascope.platform.api.model.PreparednessExerciseBoardResponse;
import com.terrascope.platform.api.model.UpdatePreparednessExerciseOwnerRequest;
import com.terrascope.platform.api.model.UpdatePreparednessExerciseScheduleRequest;
import com.terrascope.platform.api.model.UpdatePreparednessExerciseStatusRequest;
import com.terrascope.platform.domain.ImprovementActionStatus;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.PreparednessExerciseStatus;
import com.terrascope.platform.domain.PreparednessExerciseType;
import com.terrascope.platform.domain.PreparednessValidationResult;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.PreparednessExerciseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PreparednessExerciseService {

    private final PreparednessExerciseRepository repository;
    private final IncidentResponseRepository incidentRepository;

    public PreparednessExerciseService(
            PreparednessExerciseRepository repository,
            IncidentResponseRepository incidentRepository
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
    }

    public PreparednessExerciseBoardResponse board(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        List<IncidentPreparednessExercise> exercises =
                repository.findAll(
                        incidentId
                );

        Instant now =
                Instant.now();

        int plannedCount =
                countStatus(
                        exercises,
                        PreparednessExerciseStatus.PLANNED
                );

        int inProgressCount =
                countStatus(
                        exercises,
                        PreparednessExerciseStatus.IN_PROGRESS
                );

        int completedCount =
                countStatus(
                        exercises,
                        PreparednessExerciseStatus.COMPLETED
                );

        int cancelledCount =
                countStatus(
                        exercises,
                        PreparednessExerciseStatus.CANCELLED
                );

        int passedCount =
                countResult(
                        exercises,
                        PreparednessValidationResult.PASSED
                );

        int partialCount =
                countResult(
                        exercises,
                        PreparednessValidationResult.PARTIAL
                );

        int failedCount =
                countResult(
                        exercises,
                        PreparednessValidationResult.FAILED
                );

        int overdueCount =
                (int) exercises
                        .stream()
                        .filter(exercise ->
                                exercise.status().mutable()
                                && exercise.scheduledAt() != null
                                && exercise.scheduledAt()
                                        .isBefore(
                                                now
                                        )
                        )
                        .count();

        double averageReadinessScore =
                exercises
                        .stream()
                        .map(
                                IncidentPreparednessExercise::readinessScore
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

        averageReadinessScore =
                Math.round(
                        averageReadinessScore
                        * 10.0
                ) / 10.0;

        return new PreparednessExerciseBoardResponse(
                incidentId,

                exercises.size(),
                plannedCount,
                inProgressCount,
                completedCount,
                cancelledCount,

                passedCount,
                partialCount,
                failedCount,

                overdueCount,

                averageReadinessScore,

                exercises
        );
    }

    @Transactional
    public PreparednessExerciseBoardResponse create(
            UUID incidentId,
            CreatePreparednessExerciseRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        Optional<ImprovementActionStatus> improvementStatus =
                repository.improvementStatus(
                        incidentId,
                        request.improvementActionId()
                );

        if (
                improvementStatus.isEmpty()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Improvement action does not belong to this incident."
            );
        }

        if (
                improvementStatus.get()
                != ImprovementActionStatus.COMPLETED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Preparedness exercises require a COMPLETED improvement action."
            );
        }

        PreparednessExerciseType exerciseType =
                PreparednessExerciseType.fromValue(
                        request.exerciseType()
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        IncidentPreparednessExercise exercise =
                repository.create(
                        UUID.randomUUID(),
                        incidentId,
                        request,
                        exerciseType,
                        owner
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_EXERCISE_CREATED",
                "Preparedness exercise created for completed improvement action: '"
                        + exercise.title()
                        + "' ("
                        + exercise.exerciseType().name()
                        + ")."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public PreparednessExerciseBoardResponse updateStatus(
            UUID incidentId,
            UUID exerciseId,
            UpdatePreparednessExerciseStatusRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentPreparednessExercise before =
                requireMutableExercise(
                        incidentId,
                        exerciseId
                );

        PreparednessExerciseStatus status =
                PreparednessExerciseStatus.fromValue(
                        request.status()
                );

        if (
                status == PreparednessExerciseStatus.COMPLETED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use the preparedness assessment endpoint to complete an exercise."
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

        IncidentPreparednessExercise after =
                repository.updateStatus(
                        incidentId,
                        exerciseId,
                        status
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_EXERCISE_STATUS_CHANGED",
                "Preparedness exercise '"
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
    public PreparednessExerciseBoardResponse updateOwner(
            UUID incidentId,
            UUID exerciseId,
            UpdatePreparednessExerciseOwnerRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentPreparednessExercise before =
                requireMutableExercise(
                        incidentId,
                        exerciseId
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
                exerciseId,
                owner
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_EXERCISE_OWNER_CHANGED",
                "Preparedness exercise '"
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
    public PreparednessExerciseBoardResponse updateSchedule(
            UUID incidentId,
            UUID exerciseId,
            UpdatePreparednessExerciseScheduleRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentPreparednessExercise before =
                requireMutableExercise(
                        incidentId,
                        exerciseId
                );

        Instant beforeSchedule =
                before.scheduledAt();

        Instant afterSchedule =
                request.scheduledAt();

        if (
                beforeSchedule == null
                ? afterSchedule == null
                : beforeSchedule.equals(
                        afterSchedule
                )
        ) {
            return board(
                    incidentId
            );
        }

        repository.updateSchedule(
                incidentId,
                exerciseId,
                afterSchedule
        );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_EXERCISE_SCHEDULE_CHANGED",
                "Preparedness exercise '"
                        + before.title()
                        + "' schedule was updated."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public PreparednessExerciseBoardResponse assess(
            UUID incidentId,
            UUID exerciseId,
            AssessPreparednessExerciseRequest request
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentPreparednessExercise before =
                requireMutableExercise(
                        incidentId,
                        exerciseId
                );

        PreparednessValidationResult result =
                PreparednessValidationResult.fromValue(
                        request.result()
                );

        if (
                !result.assessed()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Assessment result must be PASSED, PARTIAL, or FAILED."
            );
        }

        Instant performedAt =
                request.performedAt() == null
                        ? Instant.now()
                        : request.performedAt();

        IncidentPreparednessExercise after =
                repository.assess(
                        incidentId,
                        exerciseId,
                        result,
                        performedAt,
                        request
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_EXERCISE_ASSESSED",
                "Preparedness exercise '"
                        + before.title()
                        + "' assessed "
                        + after.validationResult().name()
                        + " with readiness score "
                        + after.readinessScore()
                        + "/100."
        );

        return board(
                incidentId
        );
    }

    @Transactional
    public PreparednessExerciseBoardResponse delete(
            UUID incidentId,
            UUID exerciseId
    ) {
        requireClosedIncident(
                incidentId
        );

        IncidentPreparednessExercise exercise =
                requireExercise(
                        incidentId,
                        exerciseId
                );

        int deleted =
                repository.delete(
                        incidentId,
                        exerciseId
                );

        if (
                deleted != 1
        ) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Preparedness exercise "
                            + exerciseId
                            + " was not found."
            );
        }

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "PREPAREDNESS_EXERCISE_DELETED",
                "Preparedness exercise deleted: '"
                        + exercise.title()
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
                    "Preparedness exercises require a CLOSED incident."
            );
        }
    }

    private IncidentPreparednessExercise requireExercise(
            UUID incidentId,
            UUID exerciseId
    ) {
        return repository
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
    }

    private IncidentPreparednessExercise requireMutableExercise(
            UUID incidentId,
            UUID exerciseId
    ) {
        IncidentPreparednessExercise exercise =
                requireExercise(
                        incidentId,
                        exerciseId
                );

        if (
                !exercise.status()
                        .mutable()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Completed or cancelled preparedness exercises are immutable."
            );
        }

        return exercise;
    }

    private int countStatus(
            List<IncidentPreparednessExercise> exercises,
            PreparednessExerciseStatus status
    ) {
        return (int) exercises
                .stream()
                .filter(exercise ->
                        exercise.status()
                                == status
                )
                .count();
    }

    private int countResult(
            List<IncidentPreparednessExercise> exercises,
            PreparednessValidationResult result
    ) {
        return (int) exercises
                .stream()
                .filter(exercise ->
                        exercise.validationResult()
                                == result
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
