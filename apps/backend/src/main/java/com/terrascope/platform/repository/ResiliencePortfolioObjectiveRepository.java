package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CreatePortfolioObjectiveRequest;
import com.terrascope.platform.api.model.PortfolioObjective;
import com.terrascope.platform.api.model.PortfolioObjectiveEvent;
import com.terrascope.platform.domain.PortfolioObjectivePriority;
import com.terrascope.platform.domain.PortfolioObjectiveStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ResiliencePortfolioObjectiveRepository {

    private static final String OBJECTIVE_COLUMNS = """
            id,
            portfolio_review_id,
            portfolio_review_number,
            title,
            description,
            priority,
            status,
            owner_name,
            target_at,
            progress_pct,
            success_metric,
            completion_evidence,
            created_at,
            updated_at,
            completed_at
            """;

    private final JdbcClient jdbcClient;

    public ResiliencePortfolioObjectiveRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Integer> findReviewNumber(
            UUID portfolioReviewId
    ) {
        return jdbcClient
                .sql("""
                        SELECT review_number
                        FROM resilience_portfolio_reviews
                        WHERE id = :portfolioReviewId
                        """)
                .param(
                        "portfolioReviewId",
                        portfolioReviewId
                )
                .query(
                        Integer.class
                )
                .optional();
    }

    public List<PortfolioObjective> findAll() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + OBJECTIVE_COLUMNS
                        + """
                        FROM resilience_portfolio_objectives

                        ORDER BY
                            CASE status
                                WHEN 'BLOCKED' THEN 0
                                WHEN 'IN_PROGRESS' THEN 1
                                WHEN 'PLANNED' THEN 2
                                WHEN 'COMPLETED' THEN 3
                                ELSE 4
                            END,

                            CASE priority
                                WHEN 'CRITICAL' THEN 0
                                WHEN 'HIGH' THEN 1
                                WHEN 'MEDIUM' THEN 2
                                ELSE 3
                            END,

                            target_at ASC NULLS LAST,
                            updated_at DESC,
                            id DESC
                        """)
                .query(
                        this::mapObjective
                )
                .list();
    }

    public Optional<PortfolioObjective> findById(
            UUID objectiveId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + OBJECTIVE_COLUMNS
                        + """
                        FROM resilience_portfolio_objectives
                        WHERE id = :objectiveId
                        """)
                .param(
                        "objectiveId",
                        objectiveId
                )
                .query(
                        this::mapObjective
                )
                .optional();
    }

    public PortfolioObjective create(
            UUID id,
            int reviewNumber,
            CreatePortfolioObjectiveRequest request,
            PortfolioObjectivePriority priority,
            String owner
    ) {
        UUID objectiveId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_portfolio_objectives (
                                    id,
                                    portfolio_review_id,
                                    portfolio_review_number,
                                    title,
                                    description,
                                    priority,
                                    owner_name,
                                    target_at,
                                    success_metric
                                )
                                VALUES (
                                    :id,
                                    :portfolioReviewId,
                                    :portfolioReviewNumber,
                                    :title,
                                    :description,
                                    :priority,
                                    :owner,
                                    :targetAt,
                                    :successMetric
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "portfolioReviewId",
                                request.portfolioReviewId()
                        )
                        .param(
                                "portfolioReviewNumber",
                                reviewNumber
                        )
                        .param(
                                "title",
                                request.title().trim()
                        )
                        .param(
                                "description",
                                safeText(
                                        request.description()
                                )
                        )
                        .param(
                                "priority",
                                priority.name()
                        )
                        .param(
                                "owner",
                                owner
                        )
                        .param(
                                "targetAt",
                                databaseTimestamp(
                                        request.targetAt()
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .param(
                                "successMetric",
                                request.successMetric().trim()
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireObjective(
                objectiveId
        );
    }

    public PortfolioObjective updateStatus(
            UUID objectiveId,
            PortfolioObjectiveStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE resilience_portfolio_objectives
                                SET
                                    status = :status,
                                    updated_at = NOW()

                                WHERE id = :objectiveId

                                RETURNING id
                                """)
                        .param(
                                "status",
                                status.name()
                        )
                        .param(
                                "objectiveId",
                                objectiveId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Portfolio objective status update returned no row."
                                )
                        );

        return requireObjective(
                updatedId
        );
    }

    public PortfolioObjective updateOwner(
            UUID objectiveId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE resilience_portfolio_objectives
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()

                                WHERE id = :objectiveId

                                RETURNING id
                                """)
                        .param(
                                "owner",
                                owner
                        )
                        .param(
                                "objectiveId",
                                objectiveId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Portfolio objective owner update returned no row."
                                )
                        );

        return requireObjective(
                updatedId
        );
    }

    public PortfolioObjective updateTargetAt(
            UUID objectiveId,
            Instant targetAt
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE resilience_portfolio_objectives
                                SET
                                    target_at = :targetAt,
                                    updated_at = NOW()

                                WHERE id = :objectiveId

                                RETURNING id
                                """)
                        .param(
                                "targetAt",
                                databaseTimestamp(
                                        targetAt
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .param(
                                "objectiveId",
                                objectiveId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Portfolio objective target update returned no row."
                                )
                        );

        return requireObjective(
                updatedId
        );
    }

    public PortfolioObjective updateProgress(
            UUID objectiveId,
            int progressPct
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE resilience_portfolio_objectives
                                SET
                                    progress_pct = :progressPct,
                                    updated_at = NOW()

                                WHERE id = :objectiveId

                                RETURNING id
                                """)
                        .param(
                                "progressPct",
                                progressPct
                        )
                        .param(
                                "objectiveId",
                                objectiveId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Portfolio objective progress update returned no row."
                                )
                        );

        return requireObjective(
                updatedId
        );
    }

    public PortfolioObjective complete(
            UUID objectiveId,
            String completionEvidence
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE resilience_portfolio_objectives

                                SET
                                    status = 'COMPLETED',
                                    progress_pct = 100,
                                    completion_evidence = :completionEvidence,
                                    completed_at = COALESCE(
                                        completed_at,
                                        NOW()
                                    ),
                                    updated_at = NOW()

                                WHERE id = :objectiveId

                                RETURNING id
                                """)
                        .param(
                                "completionEvidence",
                                completionEvidence
                        )
                        .param(
                                "objectiveId",
                                objectiveId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Portfolio objective completion returned no row."
                                )
                        );

        return requireObjective(
                updatedId
        );
    }

    public void appendEvent(
            UUID id,
            UUID objectiveId,
            String eventType,
            String detail
    ) {
        jdbcClient
                .sql("""
                        INSERT INTO resilience_portfolio_objective_events (
                            id,
                            objective_id,
                            event_type,
                            detail
                        )
                        VALUES (
                            :id,
                            :objectiveId,
                            :eventType,
                            :detail
                        )
                        """)
                .param(
                        "id",
                        id
                )
                .param(
                        "objectiveId",
                        objectiveId
                )
                .param(
                        "eventType",
                        eventType
                )
                .param(
                        "detail",
                        detail
                )
                .update();
    }

    public List<PortfolioObjectiveEvent> findHistory(
            UUID objectiveId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            objective_id,
                            event_type,
                            detail,
                            created_at

                        FROM resilience_portfolio_objective_events

                        WHERE objective_id = :objectiveId

                        ORDER BY
                            created_at ASC,
                            id ASC
                        """)
                .param(
                        "objectiveId",
                        objectiveId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) ->
                                new PortfolioObjectiveEvent(
                                        resultSet.getObject(
                                                "id",
                                                UUID.class
                                        ),

                                        resultSet.getObject(
                                                "objective_id",
                                                UUID.class
                                        ),

                                        resultSet.getString(
                                                "event_type"
                                        ),

                                        resultSet.getString(
                                                "detail"
                                        ),

                                        resultSet
                                                .getObject(
                                                        "created_at",
                                                        OffsetDateTime.class
                                                )
                                                .toInstant()
                                )
                )
                .list();
    }

    private PortfolioObjective requireObjective(
            UUID objectiveId
    ) {
        return findById(
                objectiveId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Portfolio objective disappeared after mutation."
                )
        );
    }

    private PortfolioObjective mapObjective(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime targetAt =
                resultSet.getObject(
                        "target_at",
                        OffsetDateTime.class
                );

        OffsetDateTime completedAt =
                resultSet.getObject(
                        "completed_at",
                        OffsetDateTime.class
                );

        return new PortfolioObjective(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "portfolio_review_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "portfolio_review_number"
                ),

                resultSet.getString(
                        "title"
                ),

                resultSet.getString(
                        "description"
                ),

                PortfolioObjectivePriority.valueOf(
                        resultSet.getString(
                                "priority"
                        )
                ),

                PortfolioObjectiveStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),

                resultSet.getString(
                        "owner_name"
                ),

                targetAt == null
                        ? null
                        : targetAt.toInstant(),

                resultSet.getInt(
                        "progress_pct"
                ),

                resultSet.getString(
                        "success_metric"
                ),

                resultSet.getString(
                        "completion_evidence"
                ),

                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),

                resultSet
                        .getObject(
                                "updated_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),

                completedAt == null
                        ? null
                        : completedAt.toInstant()
        );
    }

    private OffsetDateTime databaseTimestamp(
            Instant value
    ) {
        return value == null
                ? null
                : value.atOffset(
                        ZoneOffset.UTC
                );
    }

    private String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }
}
