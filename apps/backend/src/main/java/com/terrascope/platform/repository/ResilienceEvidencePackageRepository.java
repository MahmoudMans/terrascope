package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.ResilienceEvidencePackage;
import com.terrascope.platform.domain.ResilienceEvidenceManifest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ResilienceEvidencePackageRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            package_number,
            certification_id,
            certification_number,
            certification_readiness_score,
            certification_count,
            after_action_review_count,
            sitrep_count,
            improvement_count,
            exercise_count,
            corrective_action_count,
            timeline_event_count,
            manifest_version,
            manifest_hash,
            executive_summary,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public ResilienceEvidencePackageRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public ResilienceEvidenceManifest loadManifest(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            incident.id
                                AS incident_id,

                            latest_certification.id
                                AS certification_id,

                            COALESCE(
                                latest_certification.certification_number,
                                0
                            )::INTEGER
                                AS certification_number,

                            COALESCE(
                                latest_certification.average_latest_readiness_score,
                                0
                            )::DOUBLE PRECISION
                                AS certification_readiness_score,

                            (
                                SELECT COUNT(*)
                                FROM incident_resilience_certifications certification
                                WHERE certification.incident_id = :incidentId
                            )::INTEGER
                                AS certification_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_after_action_reviews review
                                WHERE review.incident_id = :incidentId
                            )::INTEGER
                                AS after_action_review_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_situation_reports report
                                WHERE report.incident_id = :incidentId
                            )::INTEGER
                                AS sitrep_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_improvement_actions improvement
                                WHERE improvement.incident_id = :incidentId
                            )::INTEGER
                                AS improvement_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_preparedness_exercises exercise
                                WHERE exercise.incident_id = :incidentId
                            )::INTEGER
                                AS exercise_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_preparedness_corrective_actions corrective
                                WHERE corrective.incident_id = :incidentId
                            )::INTEGER
                                AS corrective_action_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_timeline_events timeline
                                WHERE timeline.incident_id = :incidentId
                            )::INTEGER
                                AS timeline_event_count

                        FROM incident_cases incident

                        LEFT JOIN LATERAL (
                            SELECT
                                certification.id,
                                certification.certification_number,
                                certification.average_latest_readiness_score

                            FROM incident_resilience_certifications certification

                            WHERE certification.incident_id =
                                  incident.id

                            ORDER BY
                                certification.certification_number DESC,
                                certification.created_at DESC,
                                certification.id DESC

                            LIMIT 1
                        ) latest_certification
                            ON TRUE

                        WHERE incident.id = :incidentId
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
                                new ResilienceEvidenceManifest(
                                        resultSet.getObject(
                                                "incident_id",
                                                UUID.class
                                        ),

                                        resultSet.getObject(
                                                "certification_id",
                                                UUID.class
                                        ),

                                        resultSet.getInt(
                                                "certification_number"
                                        ),

                                        resultSet.getDouble(
                                                "certification_readiness_score"
                                        ),

                                        resultSet.getInt(
                                                "certification_count"
                                        ),

                                        resultSet.getInt(
                                                "after_action_review_count"
                                        ),

                                        resultSet.getInt(
                                                "sitrep_count"
                                        ),

                                        resultSet.getInt(
                                                "improvement_count"
                                        ),

                                        resultSet.getInt(
                                                "exercise_count"
                                        ),

                                        resultSet.getInt(
                                                "corrective_action_count"
                                        ),

                                        resultSet.getInt(
                                                "timeline_event_count"
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

    public int nextPackageNumber(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(package_number),
                                0
                            ) + 1

                        FROM incident_resilience_evidence_packages

                        WHERE incident_id =
                              :incidentId
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

    public ResilienceEvidencePackage savePackage(
            UUID id,
            int packageNumber,
            ResilienceEvidenceManifest manifest,
            String manifestVersion,
            String manifestHash,
            String executiveSummary
    ) {
        UUID packageId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_resilience_evidence_packages (
                                    id,
                                    incident_id,
                                    package_number,
                                    certification_id,
                                    certification_number,
                                    certification_readiness_score,
                                    certification_count,
                                    after_action_review_count,
                                    sitrep_count,
                                    improvement_count,
                                    exercise_count,
                                    corrective_action_count,
                                    timeline_event_count,
                                    manifest_version,
                                    manifest_hash,
                                    executive_summary
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :packageNumber,
                                    :certificationId,
                                    :certificationNumber,
                                    :certificationReadinessScore,
                                    :certificationCount,
                                    :afterActionReviewCount,
                                    :sitrepCount,
                                    :improvementCount,
                                    :exerciseCount,
                                    :correctiveActionCount,
                                    :timelineEventCount,
                                    :manifestVersion,
                                    :manifestHash,
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
                                manifest.incidentId()
                        )
                        .param(
                                "packageNumber",
                                packageNumber
                        )
                        .param(
                                "certificationId",
                                manifest.certificationId()
                        )
                        .param(
                                "certificationNumber",
                                manifest.certificationNumber()
                        )
                        .param(
                                "certificationReadinessScore",
                                manifest.certificationReadinessScore()
                        )
                        .param(
                                "certificationCount",
                                manifest.certificationCount()
                        )
                        .param(
                                "afterActionReviewCount",
                                manifest.afterActionReviewCount()
                        )
                        .param(
                                "sitrepCount",
                                manifest.sitrepCount()
                        )
                        .param(
                                "improvementCount",
                                manifest.improvementCount()
                        )
                        .param(
                                "exerciseCount",
                                manifest.exerciseCount()
                        )
                        .param(
                                "correctiveActionCount",
                                manifest.correctiveActionCount()
                        )
                        .param(
                                "timelineEventCount",
                                manifest.timelineEventCount()
                        )
                        .param(
                                "manifestVersion",
                                manifestVersion
                        )
                        .param(
                                "manifestHash",
                                manifestHash
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
                manifest.incidentId(),
                packageId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Evidence package disappeared after creation."
                )
        );
    }

    public List<ResilienceEvidencePackage> findHistory(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_resilience_evidence_packages
                        WHERE incident_id = :incidentId
                        ORDER BY
                            package_number DESC,
                            created_at DESC
                        LIMIT 20
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapPackage
                )
                .list();
    }

    public Optional<ResilienceEvidencePackage> findById(
            UUID incidentId,
            UUID packageId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_resilience_evidence_packages
                        WHERE incident_id = :incidentId
                          AND id = :packageId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "packageId",
                        packageId
                )
                .query(
                        this::mapPackage
                )
                .optional();
    }

    private ResilienceEvidencePackage mapPackage(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new ResilienceEvidencePackage(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "package_number"
                ),

                resultSet.getObject(
                        "certification_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "certification_number"
                ),

                resultSet.getDouble(
                        "certification_readiness_score"
                ),

                resultSet.getInt(
                        "certification_count"
                ),

                resultSet.getInt(
                        "after_action_review_count"
                ),

                resultSet.getInt(
                        "sitrep_count"
                ),

                resultSet.getInt(
                        "improvement_count"
                ),

                resultSet.getInt(
                        "exercise_count"
                ),

                resultSet.getInt(
                        "corrective_action_count"
                ),

                resultSet.getInt(
                        "timeline_event_count"
                ),

                resultSet.getString(
                        "manifest_version"
                ),

                resultSet.getString(
                        "manifest_hash"
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
