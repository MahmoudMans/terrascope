package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CreateImprovementActionRequest;
import com.terrascope.platform.api.model.IncidentImprovementAction;
import com.terrascope.platform.domain.ImprovementActionStatus;
import com.terrascope.platform.domain.ImprovementCategory;
import com.terrascope.platform.domain.IncidentActionPriority;
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
public class IncidentImprovementActionRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            after_action_review_id,
            category,
            title,
            description,
            priority,
            status,
            owner_name,
            due_at,
            created_at,
            updated_at,
            completed_at
            """;

    private final JdbcClient jdbcClient;

    public IncidentImprovementActionRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<IncidentImprovementAction> findAll(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_improvement_actions
                        WHERE incident_id = :incidentId
                        ORDER BY
                            CASE status
                                WHEN 'IN_PROGRESS' THEN 0
                                WHEN 'OPEN' THEN 1
                                WHEN 'COMPLETED' THEN 2
                                ELSE 3
                            END,
                            CASE priority
                                WHEN 'CRITICAL' THEN 0
                                WHEN 'HIGH' THEN 1
                                WHEN 'MEDIUM' THEN 2
                                ELSE 3
                            END,
                            due_at ASC NULLS LAST,
                            updated_at DESC,
                            id DESC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapAction
                )
                .list();
    }

    public Optional<IncidentImprovementAction> findById(
            UUID incidentId,
            UUID actionId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_improvement_actions
                        WHERE incident_id = :incidentId
                          AND id = :actionId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "actionId",
                        actionId
                )
                .query(
                        this::mapAction
                )
                .optional();
    }

    public boolean reviewBelongsToIncident(
            UUID incidentId,
            UUID reviewId
    ) {
        Integer count =
                jdbcClient
                        .sql("""
                                SELECT COUNT(*)
                                FROM incident_after_action_reviews
                                WHERE incident_id = :incidentId
                                  AND id = :reviewId
                                """)
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "reviewId",
                                reviewId
                        )
                        .query(
                                Integer.class
                        )
                        .single();

        return count != null
                && count == 1;
    }

    public IncidentImprovementAction create(
            UUID id,
            UUID incidentId,
            CreateImprovementActionRequest request,
            ImprovementCategory category,
            IncidentActionPriority priority,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_improvement_actions (
                                    id,
                                    incident_id,
                                    after_action_review_id,
                                    category,
                                    title,
                                    description,
                                    priority,
                                    owner_name,
                                    due_at
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :afterActionReviewId,
                                    :category,
                                    :title,
                                    :description,
                                    :priority,
                                    :owner,
                                    :dueAt
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "afterActionReviewId",
                                request.afterActionReviewId()
                        )
                        .param(
                                "category",
                                category.name()
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
                                "dueAt",
                                databaseTimestamp(
                                        request.dueAt()
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireAction(
                incidentId,
                createdId
        );
    }

    public IncidentImprovementAction updateStatus(
            UUID incidentId,
            UUID actionId,
            ImprovementActionStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_improvement_actions
                                SET
                                    status = :status,

                                    completed_at =
                                        CASE
                                            WHEN :status = 'COMPLETED'
                                                THEN COALESCE(
                                                    completed_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END,

                                    updated_at = NOW()

                                WHERE incident_id = :incidentId
                                  AND id = :actionId

                                RETURNING id
                                """)
                        .param(
                                "status",
                                status.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "actionId",
                                actionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Improvement status update returned no row."
                                )
                        );

        return requireAction(
                incidentId,
                updatedId
        );
    }

    public IncidentImprovementAction updatePriority(
            UUID incidentId,
            UUID actionId,
            IncidentActionPriority priority
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_improvement_actions
                                SET
                                    priority = :priority,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :actionId
                                RETURNING id
                                """)
                        .param(
                                "priority",
                                priority.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "actionId",
                                actionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Improvement priority update returned no row."
                                )
                        );

        return requireAction(
                incidentId,
                updatedId
        );
    }

    public IncidentImprovementAction updateOwner(
            UUID incidentId,
            UUID actionId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_improvement_actions
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :actionId
                                RETURNING id
                                """)
                        .param(
                                "owner",
                                owner
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "actionId",
                                actionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Improvement owner update returned no row."
                                )
                        );

        return requireAction(
                incidentId,
                updatedId
        );
    }

    public IncidentImprovementAction updateDueAt(
            UUID incidentId,
            UUID actionId,
            Instant dueAt
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_improvement_actions
                                SET
                                    due_at = :dueAt,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :actionId
                                RETURNING id
                                """)
                        .param(
                                "dueAt",
                                databaseTimestamp(
                                        dueAt
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "actionId",
                                actionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Improvement due-date update returned no row."
                                )
                        );

        return requireAction(
                incidentId,
                updatedId
        );
    }

    public int delete(
            UUID incidentId,
            UUID actionId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM incident_improvement_actions
                        WHERE incident_id = :incidentId
                          AND id = :actionId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "actionId",
                        actionId
                )
                .update();
    }

    private IncidentImprovementAction requireAction(
            UUID incidentId,
            UUID actionId
    ) {
        return findById(
                incidentId,
                actionId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Improvement action disappeared after mutation."
                )
        );
    }

    private IncidentImprovementAction mapAction(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime dueAt =
                resultSet.getObject(
                        "due_at",
                        OffsetDateTime.class
                );

        OffsetDateTime completedAt =
                resultSet.getObject(
                        "completed_at",
                        OffsetDateTime.class
                );

        return new IncidentImprovementAction(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),

                resultSet.getObject(
                        "after_action_review_id",
                        UUID.class
                ),

                ImprovementCategory.valueOf(
                        resultSet.getString(
                                "category"
                        )
                ),

                resultSet.getString(
                        "title"
                ),

                resultSet.getString(
                        "description"
                ),

                IncidentActionPriority.valueOf(
                        resultSet.getString(
                                "priority"
                        )
                ),

                ImprovementActionStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),

                resultSet.getString(
                        "owner_name"
                ),

                dueAt == null
                        ? null
                        : dueAt.toInstant(),

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
