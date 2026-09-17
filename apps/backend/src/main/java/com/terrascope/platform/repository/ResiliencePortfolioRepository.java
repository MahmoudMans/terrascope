package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.ResiliencePortfolioResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.domain.PortfolioAssuranceBand;
import com.terrascope.platform.domain.ResiliencePortfolioBaseMetrics;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ResiliencePortfolioRepository {

    private static final String SNAPSHOT_COLUMNS = """
            id,
            snapshot_number,
            score,
            band,
            total_incident_count,
            open_incident_count,
            closed_incident_count,
            certified_incident_count,
            evidence_packaged_incident_count,
            audit_ledger_incident_count,
            audit_verified_incident_count,
            completed_improvement_count,
            validated_improvement_count,
            untested_improvement_count,
            needs_revalidation_count,
            failed_improvement_count,
            open_corrective_action_count,
            total_exercise_count,
            completed_exercise_count,
            passed_exercise_count,
            average_latest_readiness_score,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public ResiliencePortfolioRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public ResiliencePortfolioBaseMetrics loadBaseMetrics() {
        return jdbcClient
                .sql("""
                        SELECT

                            (
                                SELECT COUNT(*)
                                FROM incident_cases
                            )::INTEGER
                                AS total_incident_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_cases
                                WHERE status <> 'CLOSED'
                            )::INTEGER
                                AS open_incident_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_cases
                                WHERE status = 'CLOSED'
                            )::INTEGER
                                AS closed_incident_count,

                            (
                                SELECT COUNT(
                                    DISTINCT incident_id
                                )
                                FROM incident_resilience_certifications
                            )::INTEGER
                                AS certified_incident_count,

                            (
                                SELECT COUNT(
                                    DISTINCT incident_id
                                )
                                FROM incident_resilience_evidence_packages
                            )::INTEGER
                                AS evidence_packaged_incident_count,

                            (
                                SELECT COUNT(
                                    DISTINCT incident_id
                                )
                                FROM incident_resilience_audit_receipts
                            )::INTEGER
                                AS audit_ledger_incident_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_preparedness_exercises
                            )::INTEGER
                                AS total_exercise_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_preparedness_exercises
                                WHERE status = 'COMPLETED'
                            )::INTEGER
                                AS completed_exercise_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_preparedness_exercises
                                WHERE status = 'COMPLETED'
                                  AND validation_result = 'PASSED'
                            )::INTEGER
                                AS passed_exercise_count
                        """)
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) ->
                                new ResiliencePortfolioBaseMetrics(
                                        resultSet.getInt(
                                                "total_incident_count"
                                        ),

                                        resultSet.getInt(
                                                "open_incident_count"
                                        ),

                                        resultSet.getInt(
                                                "closed_incident_count"
                                        ),

                                        resultSet.getInt(
                                                "certified_incident_count"
                                        ),

                                        resultSet.getInt(
                                                "evidence_packaged_incident_count"
                                        ),

                                        resultSet.getInt(
                                                "audit_ledger_incident_count"
                                        ),

                                        resultSet.getInt(
                                                "total_exercise_count"
                                        ),

                                        resultSet.getInt(
                                                "completed_exercise_count"
                                        ),

                                        resultSet.getInt(
                                                "passed_exercise_count"
                                        )
                                )
                )
                .single();
    }

    public List<UUID> findClosedIncidentIds() {
        return jdbcClient
                .sql("""
                        SELECT id
                        FROM incident_cases
                        WHERE status = 'CLOSED'
                        ORDER BY
                            opened_at ASC,
                            id ASC
                        """)
                .query(
                        UUID.class
                )
                .list();
    }

    public List<UUID> findAuditLedgerIncidentIds() {
        return jdbcClient
                .sql("""
                        SELECT DISTINCT incident_id
                        FROM incident_resilience_audit_receipts
                        ORDER BY incident_id
                        """)
                .query(
                        UUID.class
                )
                .list();
    }

    public int nextSnapshotNumber() {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(snapshot_number),
                                0
                            ) + 1
                        FROM resilience_portfolio_snapshots
                        """)
                .query(
                        Integer.class
                )
                .single();
    }

    public ResiliencePortfolioSnapshot saveSnapshot(
            UUID id,
            int snapshotNumber,
            ResiliencePortfolioResponse portfolio
    ) {
        UUID snapshotId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_portfolio_snapshots (
                                    id,
                                    snapshot_number,
                                    score,
                                    band,
                                    total_incident_count,
                                    open_incident_count,
                                    closed_incident_count,
                                    certified_incident_count,
                                    evidence_packaged_incident_count,
                                    audit_ledger_incident_count,
                                    audit_verified_incident_count,
                                    completed_improvement_count,
                                    validated_improvement_count,
                                    untested_improvement_count,
                                    needs_revalidation_count,
                                    failed_improvement_count,
                                    open_corrective_action_count,
                                    total_exercise_count,
                                    completed_exercise_count,
                                    passed_exercise_count,
                                    average_latest_readiness_score
                                )
                                VALUES (
                                    :id,
                                    :snapshotNumber,
                                    :score,
                                    :band,
                                    :totalIncidentCount,
                                    :openIncidentCount,
                                    :closedIncidentCount,
                                    :certifiedIncidentCount,
                                    :evidencePackagedIncidentCount,
                                    :auditLedgerIncidentCount,
                                    :auditVerifiedIncidentCount,
                                    :completedImprovementCount,
                                    :validatedImprovementCount,
                                    :untestedImprovementCount,
                                    :needsRevalidationCount,
                                    :failedImprovementCount,
                                    :openCorrectiveActionCount,
                                    :totalExerciseCount,
                                    :completedExerciseCount,
                                    :passedExerciseCount,
                                    :averageLatestReadinessScore
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "snapshotNumber",
                                snapshotNumber
                        )
                        .param(
                                "score",
                                portfolio.score()
                        )
                        .param(
                                "band",
                                portfolio.band().name()
                        )
                        .param(
                                "totalIncidentCount",
                                portfolio.totalIncidentCount()
                        )
                        .param(
                                "openIncidentCount",
                                portfolio.openIncidentCount()
                        )
                        .param(
                                "closedIncidentCount",
                                portfolio.closedIncidentCount()
                        )
                        .param(
                                "certifiedIncidentCount",
                                portfolio.certifiedIncidentCount()
                        )
                        .param(
                                "evidencePackagedIncidentCount",
                                portfolio.evidencePackagedIncidentCount()
                        )
                        .param(
                                "auditLedgerIncidentCount",
                                portfolio.auditLedgerIncidentCount()
                        )
                        .param(
                                "auditVerifiedIncidentCount",
                                portfolio.auditVerifiedIncidentCount()
                        )
                        .param(
                                "completedImprovementCount",
                                portfolio.completedImprovementCount()
                        )
                        .param(
                                "validatedImprovementCount",
                                portfolio.validatedImprovementCount()
                        )
                        .param(
                                "untestedImprovementCount",
                                portfolio.untestedImprovementCount()
                        )
                        .param(
                                "needsRevalidationCount",
                                portfolio.needsRevalidationCount()
                        )
                        .param(
                                "failedImprovementCount",
                                portfolio.failedImprovementCount()
                        )
                        .param(
                                "openCorrectiveActionCount",
                                portfolio.openCorrectiveActionCount()
                        )
                        .param(
                                "totalExerciseCount",
                                portfolio.totalExerciseCount()
                        )
                        .param(
                                "completedExerciseCount",
                                portfolio.completedExerciseCount()
                        )
                        .param(
                                "passedExerciseCount",
                                portfolio.passedExerciseCount()
                        )
                        .param(
                                "averageLatestReadinessScore",
                                portfolio.averageLatestReadinessScore()
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findSnapshotById(
                snapshotId
        );
    }

    public List<ResiliencePortfolioSnapshot> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SNAPSHOT_COLUMNS
                        + """
                        FROM resilience_portfolio_snapshots
                        ORDER BY
                            snapshot_number DESC,
                            created_at DESC
                        LIMIT 30
                        """)
                .query(
                        this::mapSnapshot
                )
                .list();
    }

    private ResiliencePortfolioSnapshot findSnapshotById(
            UUID snapshotId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SNAPSHOT_COLUMNS
                        + """
                        FROM resilience_portfolio_snapshots
                        WHERE id = :snapshotId
                        """)
                .param(
                        "snapshotId",
                        snapshotId
                )
                .query(
                        this::mapSnapshot
                )
                .single();
    }

    private ResiliencePortfolioSnapshot mapSnapshot(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new ResiliencePortfolioSnapshot(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "snapshot_number"
                ),

                resultSet.getInt(
                        "score"
                ),

                PortfolioAssuranceBand.valueOf(
                        resultSet.getString(
                                "band"
                        )
                ),

                resultSet.getInt(
                        "total_incident_count"
                ),

                resultSet.getInt(
                        "open_incident_count"
                ),

                resultSet.getInt(
                        "closed_incident_count"
                ),

                resultSet.getInt(
                        "certified_incident_count"
                ),

                resultSet.getInt(
                        "evidence_packaged_incident_count"
                ),

                resultSet.getInt(
                        "audit_ledger_incident_count"
                ),

                resultSet.getInt(
                        "audit_verified_incident_count"
                ),

                resultSet.getInt(
                        "completed_improvement_count"
                ),

                resultSet.getInt(
                        "validated_improvement_count"
                ),

                resultSet.getInt(
                        "untested_improvement_count"
                ),

                resultSet.getInt(
                        "needs_revalidation_count"
                ),

                resultSet.getInt(
                        "failed_improvement_count"
                ),

                resultSet.getInt(
                        "open_corrective_action_count"
                ),

                resultSet.getInt(
                        "total_exercise_count"
                ),

                resultSet.getInt(
                        "completed_exercise_count"
                ),

                resultSet.getInt(
                        "passed_exercise_count"
                ),

                resultSet.getDouble(
                        "average_latest_readiness_score"
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
