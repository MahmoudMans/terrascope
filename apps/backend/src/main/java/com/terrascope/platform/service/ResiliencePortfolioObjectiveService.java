package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CompletePortfolioObjectiveRequest;
import com.terrascope.platform.api.model.CreatePortfolioObjectiveRequest;
import com.terrascope.platform.api.model.PortfolioObjective;
import com.terrascope.platform.api.model.PortfolioObjectiveBoardResponse;
import com.terrascope.platform.api.model.PortfolioObjectiveHistoryResponse;
import com.terrascope.platform.api.model.UpdatePortfolioObjectiveOwnerRequest;
import com.terrascope.platform.api.model.UpdatePortfolioObjectiveProgressRequest;
import com.terrascope.platform.api.model.UpdatePortfolioObjectiveStatusRequest;
import com.terrascope.platform.api.model.UpdatePortfolioObjectiveTargetRequest;
import com.terrascope.platform.domain.PortfolioObjectivePriority;
import com.terrascope.platform.domain.PortfolioObjectiveStatus;
import com.terrascope.platform.repository.ResiliencePortfolioObjectiveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ResiliencePortfolioObjectiveService {

    private final ResiliencePortfolioObjectiveRepository repository;

    public ResiliencePortfolioObjectiveService(
            ResiliencePortfolioObjectiveRepository repository
    ) {
        this.repository = repository;
    }

    public PortfolioObjectiveBoardResponse board() {
        List<PortfolioObjective> objectives =
                repository.findAll();

        Instant now =
                Instant.now();

        int plannedCount =
                countStatus(
                        objectives,
                        PortfolioObjectiveStatus.PLANNED
                );

        int inProgressCount =
                countStatus(
                        objectives,
                        PortfolioObjectiveStatus.IN_PROGRESS
                );

        int blockedCount =
                countStatus(
                        objectives,
                        PortfolioObjectiveStatus.BLOCKED
                );

        int completedCount =
                countStatus(
                        objectives,
                        PortfolioObjectiveStatus.COMPLETED
                );

        int cancelledCount =
                countStatus(
                        objectives,
                        PortfolioObjectiveStatus.CANCELLED
                );

        int overdueCount =
                (int) objectives
                        .stream()
                        .filter(objective ->
                                objective.status().mutable()
                                && objective.targetAt() != null
                                && objective.targetAt()
                                        .isBefore(
                                                now
                                        )
                        )
                        .count();

        double averageProgressPct =
                objectives
                        .stream()
                        .mapToInt(
                                PortfolioObjective::progressPct
                        )
                        .average()
                        .orElse(
                                0.0
                        );

        averageProgressPct =
                Math.round(
                        averageProgressPct
                        * 10.0
                ) / 10.0;

        return new PortfolioObjectiveBoardResponse(
                objectives.size(),

                plannedCount,
                inProgressCount,
                blockedCount,
                completedCount,
                cancelledCount,

                overdueCount,

                averageProgressPct,

                objectives
        );
    }

    public PortfolioObjectiveHistoryResponse history(
            UUID objectiveId
    ) {
        requireObjective(
                objectiveId
        );

        return new PortfolioObjectiveHistoryResponse(
                objectiveId,
                repository.findHistory(
                        objectiveId
                )
        );
    }

    @Transactional
    public PortfolioObjectiveBoardResponse create(
            CreatePortfolioObjectiveRequest request
    ) {
        int reviewNumber =
                repository
                        .findReviewNumber(
                                request.portfolioReviewId()
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Portfolio review does not exist."
                                )
                        );

        PortfolioObjectivePriority priority =
                PortfolioObjectivePriority.fromValue(
                        request.priority()
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        PortfolioObjective objective =
                repository.create(
                        UUID.randomUUID(),
                        reviewNumber,
                        request,
                        priority,
                        owner
                );

        repository.appendEvent(
                UUID.randomUUID(),
                objective.id(),
                "OBJECTIVE_CREATED",
                "Strategic resilience objective created from executive portfolio review #"
                        + objective.portfolioReviewNumber()
                        + ": '"
                        + objective.title()
                        + "'."
        );

        return board();
    }

    @Transactional
    public PortfolioObjectiveBoardResponse updateStatus(
            UUID objectiveId,
            UpdatePortfolioObjectiveStatusRequest request
    ) {
        PortfolioObjective before =
                requireMutableObjective(
                        objectiveId
                );

        PortfolioObjectiveStatus status =
                PortfolioObjectiveStatus.fromValue(
                        request.status()
                );

        if (
                status
                == PortfolioObjectiveStatus.COMPLETED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use the completion endpoint to complete a strategic objective."
            );
        }

        if (
                before.status()
                == status
        ) {
            return board();
        }

        PortfolioObjective after =
                repository.updateStatus(
                        objectiveId,
                        status
                );

        repository.appendEvent(
                UUID.randomUUID(),
                objectiveId,
                "OBJECTIVE_STATUS_CHANGED",
                "Objective status changed from "
                        + before.status().name()
                        + " to "
                        + after.status().name()
                        + "."
        );

        return board();
    }

    @Transactional
    public PortfolioObjectiveBoardResponse updateOwner(
            UUID objectiveId,
            UpdatePortfolioObjectiveOwnerRequest request
    ) {
        PortfolioObjective before =
                requireMutableObjective(
                        objectiveId
                );

        String owner =
                normalizeOwner(
                        request.owner()
                );

        if (
                before.owner()
                        .equals(owner)
        ) {
            return board();
        }

        repository.updateOwner(
                objectiveId,
                owner
        );

        repository.appendEvent(
                UUID.randomUUID(),
                objectiveId,
                "OBJECTIVE_OWNER_CHANGED",
                "Objective owner changed from '"
                        + before.owner()
                        + "' to '"
                        + owner
                        + "'."
        );

        return board();
    }

    @Transactional
    public PortfolioObjectiveBoardResponse updateTarget(
            UUID objectiveId,
            UpdatePortfolioObjectiveTargetRequest request
    ) {
        PortfolioObjective before =
                requireMutableObjective(
                        objectiveId
                );

        Instant beforeTarget =
                before.targetAt();

        Instant afterTarget =
                request.targetAt();

        if (
                beforeTarget == null
                ? afterTarget == null
                : beforeTarget.equals(
                        afterTarget
                )
        ) {
            return board();
        }

        repository.updateTargetAt(
                objectiveId,
                afterTarget
        );

        repository.appendEvent(
                UUID.randomUUID(),
                objectiveId,
                "OBJECTIVE_TARGET_CHANGED",
                "Strategic objective target date was updated."
        );

        return board();
    }

    @Transactional
    public PortfolioObjectiveBoardResponse updateProgress(
            UUID objectiveId,
            UpdatePortfolioObjectiveProgressRequest request
    ) {
        PortfolioObjective before =
                requireMutableObjective(
                        objectiveId
                );

        if (
                before.progressPct()
                == request.progressPct()
        ) {
            return board();
        }

        PortfolioObjective after =
                repository.updateProgress(
                        objectiveId,
                        request.progressPct()
                );

        repository.appendEvent(
                UUID.randomUUID(),
                objectiveId,
                "OBJECTIVE_PROGRESS_CHANGED",
                "Objective progress changed from "
                        + before.progressPct()
                        + "% to "
                        + after.progressPct()
                        + "%."
        );

        return board();
    }

    @Transactional
    public PortfolioObjectiveBoardResponse complete(
            UUID objectiveId,
            CompletePortfolioObjectiveRequest request
    ) {
        PortfolioObjective before =
                requireMutableObjective(
                        objectiveId
                );

        String evidence =
                request.completionEvidence()
                        .trim();

        PortfolioObjective completed =
                repository.complete(
                        objectiveId,
                        evidence
                );

        repository.appendEvent(
                UUID.randomUUID(),
                objectiveId,
                "OBJECTIVE_COMPLETED",
                "Strategic objective '"
                        + before.title()
                        + "' completed at 100% progress with governance evidence."
        );

        if (
                completed.status()
                != PortfolioObjectiveStatus.COMPLETED
                || completed.progressPct() != 100
        ) {
            throw new IllegalStateException(
                    "Objective completion contract was not satisfied."
            );
        }

        return board();
    }

    private PortfolioObjective requireObjective(
            UUID objectiveId
    ) {
        return repository
                .findById(
                        objectiveId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Strategic objective "
                                        + objectiveId
                                        + " was not found."
                        )
                );
    }

    private PortfolioObjective requireMutableObjective(
            UUID objectiveId
    ) {
        PortfolioObjective objective =
                requireObjective(
                        objectiveId
                );

        if (
                !objective.status()
                        .mutable()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Completed or cancelled strategic objectives are immutable."
            );
        }

        return objective;
    }

    private int countStatus(
            List<PortfolioObjective> objectives,
            PortfolioObjectiveStatus status
    ) {
        return (int) objectives
                .stream()
                .filter(objective ->
                        objective.status()
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
