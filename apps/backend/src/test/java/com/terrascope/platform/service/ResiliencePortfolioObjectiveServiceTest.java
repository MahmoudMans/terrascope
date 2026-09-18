package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CompletePortfolioObjectiveRequest;
import com.terrascope.platform.api.model.CreatePortfolioObjectiveRequest;
import com.terrascope.platform.api.model.PortfolioObjective;
import com.terrascope.platform.api.model.PortfolioObjectiveBoardResponse;
import com.terrascope.platform.domain.PortfolioObjectivePriority;
import com.terrascope.platform.domain.PortfolioObjectiveStatus;
import com.terrascope.platform.repository.ResiliencePortfolioObjectiveRepository;
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

class ResiliencePortfolioObjectiveServiceTest {

    private ResiliencePortfolioObjectiveRepository repository;
    private ResiliencePortfolioObjectiveService service;

    private UUID reviewId;
    private UUID objectiveId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        ResiliencePortfolioObjectiveRepository.class
                );

        service =
                new ResiliencePortfolioObjectiveService(
                        repository
                );

        reviewId =
                UUID.randomUUID();

        objectiveId =
                UUID.randomUUID();
    }

    @Test
    void boardSummarizesProgressAndOverdueObjectives() {
        Instant now =
                Instant.now();

        when(
                repository.findAll()
        ).thenReturn(
                List.of(
                        objective(
                                PortfolioObjectiveStatus.IN_PROGRESS,
                                40,
                                now.minusSeconds(
                                        3600
                                )
                        ),

                        objective(
                                PortfolioObjectiveStatus.COMPLETED,
                                100,
                                now.minusSeconds(
                                        7200
                                )
                        )
                )
        );

        PortfolioObjectiveBoardResponse board =
                service.board();

        assertThat(
                board.totalCount()
        ).isEqualTo(2);

        assertThat(
                board.inProgressCount()
        ).isEqualTo(1);

        assertThat(
                board.completedCount()
        ).isEqualTo(1);

        assertThat(
                board.overdueCount()
        ).isEqualTo(1);

        assertThat(
                board.averageProgressPct()
        ).isEqualTo(70.0);
    }

    @Test
    void objectiveRequiresExistingPortfolioReview() {
        when(
                repository.findReviewNumber(
                        reviewId
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(() ->
                service.create(
                        createRequest()
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void createWritesPersistentHistoryEvent() {
        CreatePortfolioObjectiveRequest request =
                createRequest();

        PortfolioObjective created =
                objective(
                        PortfolioObjectiveStatus.PLANNED,
                        0,
                        request.targetAt()
                );

        when(
                repository.findReviewNumber(
                        reviewId
                )
        ).thenReturn(
                Optional.of(7)
        );

        when(
                repository.create(
                        any(),
                        eq(7),
                        eq(request),
                        eq(
                                PortfolioObjectivePriority.HIGH
                        ),
                        eq(
                                "Executive Resilience Office"
                        )
                )
        ).thenReturn(
                created
        );

        when(
                repository.findAll()
        ).thenReturn(
                List.of(
                        created
                )
        );

        service.create(
                request
        );

        verify(
                repository
        ).appendEvent(
                any(),
                eq(created.id()),
                eq(
                        "OBJECTIVE_CREATED"
                ),
                any()
        );
    }

    @Test
    void completionSetsOneHundredPercentAndEvidence() {
        PortfolioObjective before =
                objective(
                        PortfolioObjectiveStatus.IN_PROGRESS,
                        80,
                        Instant.now()
                );

        PortfolioObjective completed =
                new PortfolioObjective(
                        before.id(),

                        before.portfolioReviewId(),
                        before.portfolioReviewNumber(),

                        before.title(),
                        before.description(),

                        before.priority(),
                        PortfolioObjectiveStatus.COMPLETED,

                        before.owner(),

                        before.targetAt(),

                        100,

                        before.successMetric(),
                        "Executive steering committee accepted the completion evidence.",

                        before.createdAt(),
                        Instant.now(),
                        Instant.now()
                );

        when(
                repository.findById(
                        before.id()
                )
        ).thenReturn(
                Optional.of(
                        before
                )
        );

        when(
                repository.complete(
                        eq(before.id()),
                        any()
                )
        ).thenReturn(
                completed
        );

        when(
                repository.findAll()
        ).thenReturn(
                List.of(
                        completed
                )
        );

        PortfolioObjectiveBoardResponse board =
                service.complete(
                        before.id(),
                        new CompletePortfolioObjectiveRequest(
                                completed.completionEvidence()
                        )
                );

        assertThat(
                board.completedCount()
        ).isEqualTo(1);

        assertThat(
                board.objectives()
                        .getFirst()
                        .progressPct()
        ).isEqualTo(100);

        verify(
                repository
        ).appendEvent(
                any(),
                eq(before.id()),
                eq(
                        "OBJECTIVE_COMPLETED"
                ),
                any()
        );
    }

    private CreatePortfolioObjectiveRequest createRequest() {
        return new CreatePortfolioObjectiveRequest(
                reviewId,

                "Raise verified audit coverage",
                "Expand verified audit-ledger coverage across the resilience portfolio.",

                "HIGH",

                "Executive Resilience Office",

                Instant.parse(
                        "2030-12-31T23:59:59Z"
                ),

                "Verified audit-ledger coverage reaches at least 90% of certified incidents."
        );
    }

    private PortfolioObjective objective(
            PortfolioObjectiveStatus status,
            int progress,
            Instant targetAt
    ) {
        Instant now =
                Instant.now();

        return new PortfolioObjective(
                objectiveId,

                reviewId,
                7,

                "Raise verified audit coverage",
                "Expand governance assurance coverage.",

                PortfolioObjectivePriority.HIGH,
                status,

                "Executive Resilience Office",

                targetAt,

                progress,

                "Verified audit coverage reaches at least 90%.",

                status
                        == PortfolioObjectiveStatus.COMPLETED
                        ? "Completion evidence."
                        : "",

                now,
                now,

                status
                        == PortfolioObjectiveStatus.COMPLETED
                        ? now
                        : null
        );
    }
}
