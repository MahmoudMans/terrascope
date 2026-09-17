package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CreatePreparednessCorrectiveActionRequest;
import com.terrascope.platform.api.model.PreparednessCorrectiveAction;
import com.terrascope.platform.domain.CorrectiveActionStatus;
import com.terrascope.platform.domain.ImprovementValidationProjection;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.PreparednessValidationResult;
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
public class ResilienceValidationRepository {

    private static final String CORRECTIVE_SELECT_COLUMNS = """
            id,
            incident_id,
            exercise_id,
            improvement_action_id,
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

    public ResilienceValidationRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<ImprovementValidationProjection> findValidationProjections(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            improvement.id
                                AS improvement_action_id,

                            improvement.title
                                AS improvement_title,

                            improvement.priority
                                AS improvement_priority,

                            COALESCE(
                                exercise_totals.total_count,
                                0
                            )::INTEGER
                                AS total_exercise_count,

                            latest_exercise.id
                                AS latest_exercise_id,

                            latest_exercise.validation_result
                                AS latest_validation_result,

                            latest_exercise.readiness_score
                                AS latest_readiness_score,

                            COALESCE(
                                corrective_totals.open_count,
                                0
                            )::INTEGER
                                AS open_corrective_action_count

                        FROM incident_improvement_actions improvement

                        LEFT JOIN LATERAL (
                            SELECT
                                COUNT(*) AS total_count
                            FROM incident_preparedness_exercises exercise
                            WHERE exercise.improvement_action_id =
                                  improvement.id
                        ) exercise_totals
                            ON TRUE

                        LEFT JOIN LATERAL (
                            SELECT
                                exercise.id,
                                exercise.validation_result,
                                exercise.readiness_score

                            FROM incident_preparedness_exercises exercise

                            WHERE exercise.improvement_action_id =
                                  improvement.id

                              AND exercise.status =
                                  'COMPLETED'

                              AND exercise.validation_result
                                  <> 'NOT_ASSESSED'

                            ORDER BY
                                COALESCE(
                                    exercise.performed_at,
                                    exercise.completed_at,
                                    exercise.updated_at
                                ) DESC,
                                exercise.id DESC

                            LIMIT 1
                        ) latest_exercise
                            ON TRUE

                        LEFT JOIN LATERAL (
                            SELECT
                                COUNT(*) AS open_count

                            FROM incident_preparedness_corrective_actions corrective

                            WHERE corrective.improvement_action_id =
                                  improvement.id

                              AND corrective.status IN (
                                  'OPEN',
                                  'IN_PROGRESS'
                              )
                        ) corrective_totals
                            ON TRUE

                        WHERE improvement.incident_id =
                              :incidentId

                          AND improvement.status =
                              'COMPLETED'

                        ORDER BY
                            improvement.updated_at DESC,
                            improvement.id DESC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) -> {
                            String latestResult =
                                    resultSet.getString(
                                            "latest_validation_result"
                                    );

                            Integer latestScore =
                                    resultSet.getObject(
                                            "latest_readiness_score",
                                            Integer.class
                                    );

                            return new ImprovementValidationProjection(
                                    resultSet.getObject(
                                            "improvement_action_id",
                                            UUID.class
                                    ),

                                    resultSet.getString(
                                            "improvement_title"
                                    ),

                                    IncidentActionPriority.valueOf(
                                            resultSet.getString(
                                                    "improvement_priority"
                                            )
                                    ),

                                    resultSet.getInt(
                                            "total_exercise_count"
                                    ),

                                    resultSet.getObject(
                                            "latest_exercise_id",
                                            UUID.class
                                    ),

                                    latestResult == null
                                            ? null
                                            : PreparednessValidationResult.valueOf(
                                                    latestResult
                                            ),

                                    latestScore,

                                    resultSet.getInt(
                                            "open_corrective_action_count"
                                    )
                            );
                        }
                )
                .list();
    }

    public List<PreparednessCorrectiveAction> findCorrectiveActions(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + CORRECTIVE_SELECT_COLUMNS
                        + """
                        FROM incident_preparedness_corrective_actions
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
                        this::mapCorrectiveAction
                )
                .list();
    }

    public Optional<PreparednessCorrectiveAction> findCorrectiveAction(
            UUID incidentId,
            UUID correctiveActionId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + CORRECTIVE_SELECT_COLUMNS
                        + """
                        FROM incident_preparedness_corrective_actions
                        WHERE incident_id = :incidentId
                          AND id = :correctiveActionId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "correctiveActionId",
                        correctiveActionId
                )
                .query(
                        this::mapCorrectiveAction
                )
                .optional();
    }

    public PreparednessCorrectiveAction createCorrectiveAction(
            UUID id,
            UUID incidentId,
            UUID exerciseId,
            UUID improvementActionId,
            CreatePreparednessCorrectiveActionRequest request,
            IncidentActionPriority priority,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_preparedness_corrective_actions (
                                    id,
                                    incident_id,
                                    exercise_id,
                                    improvement_action_id,
                                    title,
                                    description,
                                    priority,
                                    owner_name,
                                    due_at
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :exerciseId,
                                    :improvementActionId,
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
                                "exerciseId",
                                exerciseId
                        )
                        .param(
                                "improvementActionId",
                                improvementActionId
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

        return requireCorrectiveAction(
                incidentId,
                createdId
        );
    }

    public PreparednessCorrectiveAction updateCorrectiveStatus(
            UUID incidentId,
            UUID correctiveActionId,
            CorrectiveActionStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_preparedness_corrective_actions

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
                                  AND id = :correctiveActionId

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
                                "correctiveActionId",
                                correctiveActionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Corrective status update returned no row."
                                )
                        );

        return requireCorrectiveAction(
                incidentId,
                updatedId
        );
    }

    public PreparednessCorrectiveAction updateCorrectiveOwner(
            UUID incidentId,
            UUID correctiveActionId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_preparedness_corrective_actions
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :correctiveActionId
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
                                "correctiveActionId",
                                correctiveActionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Corrective owner update returned no row."
                                )
                        );

        return requireCorrectiveAction(
                incidentId,
                updatedId
        );
    }

    public PreparednessCorrectiveAction updateCorrectiveDueAt(
            UUID incidentId,
            UUID correctiveActionId,
            Instant dueAt
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_preparedness_corrective_actions
                                SET
                                    due_at = :dueAt,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :correctiveActionId
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
                                "correctiveActionId",
                                correctiveActionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Corrective due-date update returned no row."
                                )
                        );

        return requireCorrectiveAction(
                incidentId,
                updatedId
        );
    }

    public int deleteCorrectiveAction(
            UUID incidentId,
            UUID correctiveActionId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM incident_preparedness_corrective_actions
                        WHERE incident_id = :incidentId
                          AND id = :correctiveActionId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "correctiveActionId",
                        correctiveActionId
                )
                .update();
    }

    private PreparednessCorrectiveAction requireCorrectiveAction(
            UUID incidentId,
            UUID correctiveActionId
    ) {
        return findCorrectiveAction(
                incidentId,
                correctiveActionId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Corrective action disappeared after mutation."
                )
        );
    }

    private PreparednessCorrectiveAction mapCorrectiveAction(
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

        return new PreparednessCorrectiveAction(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),

                resultSet.getObject(
                        "exercise_id",
                        UUID.class
                ),

                resultSet.getObject(
                        "improvement_action_id",
                        UUID.class
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

                CorrectiveActionStatus.valueOf(
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
