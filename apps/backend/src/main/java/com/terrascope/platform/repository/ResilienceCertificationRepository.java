package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.IncidentResilienceCertification;
import com.terrascope.platform.api.model.ResilienceCertificationReadinessResponse;
import com.terrascope.platform.domain.ResilienceCertificationMetrics;
import com.terrascope.platform.domain.ResilienceCertificationStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ResilienceCertificationRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            certification_number,
            status,
            completed_improvement_count,
            validated_count,
            untested_count,
            needs_revalidation_count,
            failed_count,
            open_corrective_action_count,
            total_exercise_count,
            passed_exercise_count,
            average_latest_readiness_score,
            after_action_review_count,
            sitrep_count,
            executive_summary,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public ResilienceCertificationRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public ResilienceCertificationMetrics loadMetrics(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT

                            (
                                SELECT COUNT(*)
                                FROM incident_after_action_reviews review
                                WHERE review.incident_id = :incidentId
                            )::INTEGER
                                AS after_action_review_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_situation_reports sitrep
                                WHERE sitrep.incident_id = :incidentId
                            )::INTEGER
                                AS sitrep_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_preparedness_exercises exercise
                                WHERE exercise.incident_id = :incidentId
                            )::INTEGER
                                AS total_exercise_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_preparedness_exercises exercise
                                WHERE exercise.incident_id = :incidentId
                                  AND exercise.status = 'COMPLETED'
                                  AND exercise.validation_result = 'PASSED'
                            )::INTEGER
                                AS passed_exercise_count
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) ->
                                new ResilienceCertificationMetrics(
                                        resultSet.getInt(
                                                "after_action_review_count"
                                        ),
                                        resultSet.getInt(
                                                "sitrep_count"
                                        ),
                                        resultSet.getInt(
                                                "total_exercise_count"
                                        ),
                                        resultSet.getInt(
                                                "passed_exercise_count"
                                        )
                                )
                )
                .single();
    }

    public void lockIncident(
            UUID incidentId
    ) {
        jdbcClient
                .sql("""
                        SELECT id
                        FROM incident_cases
                        WHERE id = :incidentId
                        FOR UPDATE
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        UUID.class
                )
                .single();
    }

    public int nextCertificationNumber(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(certification_number),
                                0
                            ) + 1
                        FROM incident_resilience_certifications
                        WHERE incident_id = :incidentId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        Integer.class
                )
                .single();
    }

    public IncidentResilienceCertification saveCertification(
            UUID id,
            int certificationNumber,
            ResilienceCertificationReadinessResponse readiness,
            String executiveSummary
    ) {
        UUID certificationId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_resilience_certifications (
                                    id,
                                    incident_id,
                                    certification_number,
                                    status,
                                    completed_improvement_count,
                                    validated_count,
                                    untested_count,
                                    needs_revalidation_count,
                                    failed_count,
                                    open_corrective_action_count,
                                    total_exercise_count,
                                    passed_exercise_count,
                                    average_latest_readiness_score,
                                    after_action_review_count,
                                    sitrep_count,
                                    executive_summary
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :certificationNumber,
                                    'CERTIFIED',
                                    :completedImprovementCount,
                                    :validatedCount,
                                    :untestedCount,
                                    :needsRevalidationCount,
                                    :failedCount,
                                    :openCorrectiveActionCount,
                                    :totalExerciseCount,
                                    :passedExerciseCount,
                                    :averageLatestReadinessScore,
                                    :afterActionReviewCount,
                                    :sitrepCount,
                                    :executiveSummary
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "incidentId",
                                readiness.incidentId()
                        )
                        .param(
                                "certificationNumber",
                                certificationNumber
                        )
                        .param(
                                "completedImprovementCount",
                                readiness.completedImprovementCount()
                        )
                        .param(
                                "validatedCount",
                                readiness.validatedCount()
                        )
                        .param(
                                "untestedCount",
                                readiness.untestedCount()
                        )
                        .param(
                                "needsRevalidationCount",
                                readiness.needsRevalidationCount()
                        )
                        .param(
                                "failedCount",
                                readiness.failedCount()
                        )
                        .param(
                                "openCorrectiveActionCount",
                                readiness.openCorrectiveActionCount()
                        )
                        .param(
                                "totalExerciseCount",
                                readiness.totalExerciseCount()
                        )
                        .param(
                                "passedExerciseCount",
                                readiness.passedExerciseCount()
                        )
                        .param(
                                "averageLatestReadinessScore",
                                readiness.averageLatestReadinessScore()
                        )
                        .param(
                                "afterActionReviewCount",
                                readiness.afterActionReviewCount()
                        )
                        .param(
                                "sitrepCount",
                                readiness.sitrepCount()
                        )
                        .param(
                                "executiveSummary",
                                executiveSummary
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                certificationId
        );
    }

    public List<IncidentResilienceCertification> findHistory(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_resilience_certifications
                        WHERE incident_id = :incidentId
                        ORDER BY
                            certification_number DESC,
                            created_at DESC
                        LIMIT 20
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapCertification
                )
                .list();
    }

    private IncidentResilienceCertification findById(
            UUID certificationId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_resilience_certifications
                        WHERE id = :certificationId
                        """)
                .param(
                        "certificationId",
                        certificationId
                )
                .query(
                        this::mapCertification
                )
                .single();
    }

    private IncidentResilienceCertification mapCertification(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new IncidentResilienceCertification(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "certification_number"
                ),

                ResilienceCertificationStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),

                resultSet.getInt(
                        "completed_improvement_count"
                ),

                resultSet.getInt(
                        "validated_count"
                ),

                resultSet.getInt(
                        "untested_count"
                ),

                resultSet.getInt(
                        "needs_revalidation_count"
                ),

                resultSet.getInt(
                        "failed_count"
                ),

                resultSet.getInt(
                        "open_corrective_action_count"
                ),

                resultSet.getInt(
                        "total_exercise_count"
                ),

                resultSet.getInt(
                        "passed_exercise_count"
                ),

                resultSet.getDouble(
                        "average_latest_readiness_score"
                ),

                resultSet.getInt(
                        "after_action_review_count"
                ),

                resultSet.getInt(
                        "sitrep_count"
                ),

                resultSet.getString(
                        "executive_summary"
                ),

                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }
}
