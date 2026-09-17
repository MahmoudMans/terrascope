package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.AssessPreparednessExerciseRequest;
import com.terrascope.platform.api.model.CreatePreparednessExerciseRequest;
import com.terrascope.platform.api.model.IncidentPreparednessExercise;
import com.terrascope.platform.domain.ImprovementActionStatus;
import com.terrascope.platform.domain.PreparednessExerciseStatus;
import com.terrascope.platform.domain.PreparednessExerciseType;
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
public class PreparednessExerciseRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            improvement_action_id,
            exercise_type,
            title,
            description,
            status,
            owner_name,
            scheduled_at,
            performed_at,
            validation_result,
            readiness_score,
            evidence_summary,
            findings,
            corrective_action,
            created_at,
            updated_at,
            completed_at
            """;

    private final JdbcClient jdbcClient;

    public PreparednessExerciseRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<IncidentPreparednessExercise> findAll(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_preparedness_exercises
                        WHERE incident_id = :incidentId
                        ORDER BY
                            CASE status
                                WHEN 'IN_PROGRESS' THEN 0
                                WHEN 'PLANNED' THEN 1
                                WHEN 'COMPLETED' THEN 2
                                ELSE 3
                            END,
                            scheduled_at ASC NULLS LAST,
                            updated_at DESC,
                            id DESC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapExercise
                )
                .list();
    }

    public Optional<IncidentPreparednessExercise> findById(
            UUID incidentId,
            UUID exerciseId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_preparedness_exercises
                        WHERE incident_id = :incidentId
                          AND id = :exerciseId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "exerciseId",
                        exerciseId
                )
                .query(
                        this::mapExercise
                )
                .optional();
    }

    public Optional<ImprovementActionStatus> improvementStatus(
            UUID incidentId,
            UUID improvementActionId
    ) {
        return jdbcClient
                .sql("""
                        SELECT status
                        FROM incident_improvement_actions
                        WHERE incident_id = :incidentId
                          AND id = :improvementActionId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "improvementActionId",
                        improvementActionId
                )
                .query(
                        String.class
                )
                .optional()
                .map(
                        ImprovementActionStatus::valueOf
                );
    }

    public IncidentPreparednessExercise create(
            UUID id,
            UUID incidentId,
            CreatePreparednessExerciseRequest request,
            PreparednessExerciseType exerciseType,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_preparedness_exercises (
                                    id,
                                    incident_id,
                                    improvement_action_id,
                                    exercise_type,
                                    title,
                                    description,
                                    owner_name,
                                    scheduled_at
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :improvementActionId,
                                    :exerciseType,
                                    :title,
                                    :description,
                                    :owner,
                                    :scheduledAt
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
                                "improvementActionId",
                                request.improvementActionId()
                        )
                        .param(
                                "exerciseType",
                                exerciseType.name()
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
                                "owner",
                                owner
                        )
                        .param(
                                "scheduledAt",
                                databaseTimestamp(
                                        request.scheduledAt()
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireExercise(
                incidentId,
                createdId
        );
    }

    public IncidentPreparednessExercise updateStatus(
            UUID incidentId,
            UUID exerciseId,
            PreparednessExerciseStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_preparedness_exercises
                                SET
                                    status = :status,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :exerciseId
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
                                "exerciseId",
                                exerciseId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Preparedness status update returned no row."
                                )
                        );

        return requireExercise(
                incidentId,
                updatedId
        );
    }

    public IncidentPreparednessExercise updateOwner(
            UUID incidentId,
            UUID exerciseId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_preparedness_exercises
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :exerciseId
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
                                "exerciseId",
                                exerciseId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Preparedness owner update returned no row."
                                )
                        );

        return requireExercise(
                incidentId,
                updatedId
        );
    }

    public IncidentPreparednessExercise updateSchedule(
            UUID incidentId,
            UUID exerciseId,
            Instant scheduledAt
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_preparedness_exercises
                                SET
                                    scheduled_at = :scheduledAt,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :exerciseId
                                RETURNING id
                                """)
                        .param(
                                "scheduledAt",
                                databaseTimestamp(
                                        scheduledAt
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "exerciseId",
                                exerciseId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Preparedness schedule update returned no row."
                                )
                        );

        return requireExercise(
                incidentId,
                updatedId
        );
    }

    public IncidentPreparednessExercise assess(
            UUID incidentId,
            UUID exerciseId,
            PreparednessValidationResult result,
            Instant performedAt,
            AssessPreparednessExerciseRequest request
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_preparedness_exercises
                                SET
                                    status = 'COMPLETED',
                                    performed_at = :performedAt,
                                    validation_result = :validationResult,
                                    readiness_score = :readinessScore,
                                    evidence_summary = :evidenceSummary,
                                    findings = :findings,
                                    corrective_action = :correctiveAction,
                                    completed_at = COALESCE(
                                        completed_at,
                                        NOW()
                                    ),
                                    updated_at = NOW()

                                WHERE incident_id = :incidentId
                                  AND id = :exerciseId

                                RETURNING id
                                """)
                        .param(
                                "performedAt",
                                databaseTimestamp(
                                        performedAt
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .param(
                                "validationResult",
                                result.name()
                        )
                        .param(
                                "readinessScore",
                                request.readinessScore()
                        )
                        .param(
                                "evidenceSummary",
                                safeText(
                                        request.evidenceSummary()
                                )
                        )
                        .param(
                                "findings",
                                safeText(
                                        request.findings()
                                )
                        )
                        .param(
                                "correctiveAction",
                                safeText(
                                        request.correctiveAction()
                                )
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "exerciseId",
                                exerciseId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Preparedness assessment returned no row."
                                )
                        );

        return requireExercise(
                incidentId,
                updatedId
        );
    }

    public int delete(
            UUID incidentId,
            UUID exerciseId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM incident_preparedness_exercises
                        WHERE incident_id = :incidentId
                          AND id = :exerciseId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "exerciseId",
                        exerciseId
                )
                .update();
    }

    private IncidentPreparednessExercise requireExercise(
            UUID incidentId,
            UUID exerciseId
    ) {
        return findById(
                incidentId,
                exerciseId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Preparedness exercise disappeared after mutation."
                )
        );
    }

    private IncidentPreparednessExercise mapExercise(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime scheduledAt =
                resultSet.getObject(
                        "scheduled_at",
                        OffsetDateTime.class
                );

        OffsetDateTime performedAt =
                resultSet.getObject(
                        "performed_at",
                        OffsetDateTime.class
                );

        Integer readinessScore =
                resultSet.getObject(
                        "readiness_score",
                        Integer.class
                );

        OffsetDateTime completedAt =
                resultSet.getObject(
                        "completed_at",
                        OffsetDateTime.class
                );

        return new IncidentPreparednessExercise(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),

                resultSet.getObject(
                        "improvement_action_id",
                        UUID.class
                ),

                PreparednessExerciseType.valueOf(
                        resultSet.getString(
                                "exercise_type"
                        )
                ),

                resultSet.getString(
                        "title"
                ),

                resultSet.getString(
                        "description"
                ),

                PreparednessExerciseStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),

                resultSet.getString(
                        "owner_name"
                ),

                scheduledAt == null
                        ? null
                        : scheduledAt.toInstant(),

                performedAt == null
                        ? null
                        : performedAt.toInstant(),

                PreparednessValidationResult.valueOf(
                        resultSet.getString(
                                "validation_result"
                        )
                ),

                readinessScore,

                resultSet.getString(
                        "evidence_summary"
                ),

                resultSet.getString(
                        "findings"
                ),

                resultSet.getString(
                        "corrective_action"
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
