package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.BenefitRealizationResponse;
import com.terrascope.platform.api.model.BenefitRealizationSnapshot;
import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.BenefitRealizationMetrics;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class BenefitRealizationRepository {

    private static final String SNAPSHOT_COLUMNS = """
            id,
            snapshot_number,
            band,
            closure_ready,
            total_objective_count,
            planned_objective_count,
            in_progress_objective_count,
            blocked_objective_count,
            completed_objective_count,
            cancelled_objective_count,
            validated_objective_count,
            unvalidated_completed_objective_count,
            achieved_count,
            partial_count,
            not_achieved_count,
            validation_coverage_pct,
            realized_benefit_rate_pct,
            average_score_delta,
            average_assurance_debt_delta,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public BenefitRealizationRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public BenefitRealizationMetrics loadMetrics() {
        return jdbcClient
                .sql("""
                        WITH latest_validation AS (
                            SELECT DISTINCT ON (
                                validation.objective_id
                            )
                                validation.objective_id,
                                validation.outcome,
                                validation.score_delta,
                                validation.assurance_debt_delta

                            FROM resilience_portfolio_objective_validations validation

                            ORDER BY
                                validation.objective_id,
                                validation.validation_number DESC,
                                validation.created_at DESC,
                                validation.id DESC
                        )

                        SELECT
                            COUNT(*)::INTEGER
                                AS total_objective_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'PLANNED'
                            )::INTEGER
                                AS planned_objective_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'IN_PROGRESS'
                            )::INTEGER
                                AS in_progress_objective_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'BLOCKED'
                            )::INTEGER
                                AS blocked_objective_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'COMPLETED'
                            )::INTEGER
                                AS completed_objective_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'CANCELLED'
                            )::INTEGER
                                AS cancelled_objective_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'COMPLETED'
                                  AND latest.objective_id IS NOT NULL
                            )::INTEGER
                                AS validated_objective_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'COMPLETED'
                                  AND latest.objective_id IS NULL
                            )::INTEGER
                                AS unvalidated_completed_objective_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'COMPLETED'
                                  AND latest.outcome = 'ACHIEVED'
                            )::INTEGER
                                AS achieved_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'COMPLETED'
                                  AND latest.outcome = 'PARTIAL'
                            )::INTEGER
                                AS partial_count,

                            COUNT(*) FILTER (
                                WHERE objective.status = 'COMPLETED'
                                  AND latest.outcome = 'NOT_ACHIEVED'
                            )::INTEGER
                                AS not_achieved_count,

                            COALESCE(
                                AVG(
                                    latest.score_delta
                                ) FILTER (
                                    WHERE objective.status = 'COMPLETED'
                                      AND latest.objective_id IS NOT NULL
                                ),
                                0
                            )::DOUBLE PRECISION
                                AS average_score_delta,

                            COALESCE(
                                AVG(
                                    latest.assurance_debt_delta
                                ) FILTER (
                                    WHERE objective.status = 'COMPLETED'
                                      AND latest.objective_id IS NOT NULL
                                ),
                                0
                            )::DOUBLE PRECISION
                                AS average_assurance_debt_delta

                        FROM resilience_portfolio_objectives objective

                        LEFT JOIN latest_validation latest
                          ON latest.objective_id =
                             objective.id
                        """)
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) ->
                                new BenefitRealizationMetrics(
                                        resultSet.getInt(
                                                "total_objective_count"
                                        ),

                                        resultSet.getInt(
                                                "planned_objective_count"
                                        ),

                                        resultSet.getInt(
                                                "in_progress_objective_count"
                                        ),

                                        resultSet.getInt(
                                                "blocked_objective_count"
                                        ),

                                        resultSet.getInt(
                                                "completed_objective_count"
                                        ),

                                        resultSet.getInt(
                                                "cancelled_objective_count"
                                        ),

                                        resultSet.getInt(
                                                "validated_objective_count"
                                        ),

                                        resultSet.getInt(
                                                "unvalidated_completed_objective_count"
                                        ),

                                        resultSet.getInt(
                                                "achieved_count"
                                        ),

                                        resultSet.getInt(
                                                "partial_count"
                                        ),

                                        resultSet.getInt(
                                                "not_achieved_count"
                                        ),

                                        resultSet.getDouble(
                                                "average_score_delta"
                                        ),

                                        resultSet.getDouble(
                                                "average_assurance_debt_delta"
                                        )
                                )
                )
                .single();
    }

    public int nextSnapshotNumber() {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(snapshot_number),
                                0
                            ) + 1

                        FROM resilience_benefit_realization_snapshots
                        """)
                .query(
                        Integer.class
                )
                .single();
    }

    public BenefitRealizationSnapshot saveSnapshot(
            UUID id,
            int snapshotNumber,
            BenefitRealizationResponse realization
    ) {
        UUID snapshotId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_benefit_realization_snapshots (
                                    id,
                                    snapshot_number,
                                    band,
                                    closure_ready,
                                    total_objective_count,
                                    planned_objective_count,
                                    in_progress_objective_count,
                                    blocked_objective_count,
                                    completed_objective_count,
                                    cancelled_objective_count,
                                    validated_objective_count,
                                    unvalidated_completed_objective_count,
                                    achieved_count,
                                    partial_count,
                                    not_achieved_count,
                                    validation_coverage_pct,
                                    realized_benefit_rate_pct,
                                    average_score_delta,
                                    average_assurance_debt_delta
                                )
                                VALUES (
                                    :id,
                                    :snapshotNumber,
                                    :band,
                                    :closureReady,
                                    :totalObjectiveCount,
                                    :plannedObjectiveCount,
                                    :inProgressObjectiveCount,
                                    :blockedObjectiveCount,
                                    :completedObjectiveCount,
                                    :cancelledObjectiveCount,
                                    :validatedObjectiveCount,
                                    :unvalidatedCompletedObjectiveCount,
                                    :achievedCount,
                                    :partialCount,
                                    :notAchievedCount,
                                    :validationCoveragePct,
                                    :realizedBenefitRatePct,
                                    :averageScoreDelta,
                                    :averageAssuranceDebtDelta
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
                                "band",
                                realization.band().name()
                        )
                        .param(
                                "closureReady",
                                realization.closureReady()
                        )
                        .param(
                                "totalObjectiveCount",
                                realization.totalObjectiveCount()
                        )
                        .param(
                                "plannedObjectiveCount",
                                realization.plannedObjectiveCount()
                        )
                        .param(
                                "inProgressObjectiveCount",
                                realization.inProgressObjectiveCount()
                        )
                        .param(
                                "blockedObjectiveCount",
                                realization.blockedObjectiveCount()
                        )
                        .param(
                                "completedObjectiveCount",
                                realization.completedObjectiveCount()
                        )
                        .param(
                                "cancelledObjectiveCount",
                                realization.cancelledObjectiveCount()
                        )
                        .param(
                                "validatedObjectiveCount",
                                realization.validatedObjectiveCount()
                        )
                        .param(
                                "unvalidatedCompletedObjectiveCount",
                                realization.unvalidatedCompletedObjectiveCount()
                        )
                        .param(
                                "achievedCount",
                                realization.achievedCount()
                        )
                        .param(
                                "partialCount",
                                realization.partialCount()
                        )
                        .param(
                                "notAchievedCount",
                                realization.notAchievedCount()
                        )
                        .param(
                                "validationCoveragePct",
                                realization.validationCoveragePct()
                        )
                        .param(
                                "realizedBenefitRatePct",
                                realization.realizedBenefitRatePct()
                        )
                        .param(
                                "averageScoreDelta",
                                realization.averageScoreDelta()
                        )
                        .param(
                                "averageAssuranceDebtDelta",
                                realization.averageAssuranceDebtDelta()
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findSnapshotById(
                snapshotId
        );
    }

    public List<BenefitRealizationSnapshot> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SNAPSHOT_COLUMNS
                        + """
                        FROM resilience_benefit_realization_snapshots

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

    private BenefitRealizationSnapshot findSnapshotById(
            UUID snapshotId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SNAPSHOT_COLUMNS
                        + """
                        FROM resilience_benefit_realization_snapshots
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

    private BenefitRealizationSnapshot mapSnapshot(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new BenefitRealizationSnapshot(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "snapshot_number"
                ),

                BenefitRealizationBand.valueOf(
                        resultSet.getString(
                                "band"
                        )
                ),

                resultSet.getBoolean(
                        "closure_ready"
                ),

                resultSet.getInt(
                        "total_objective_count"
                ),

                resultSet.getInt(
                        "planned_objective_count"
                ),

                resultSet.getInt(
                        "in_progress_objective_count"
                ),

                resultSet.getInt(
                        "blocked_objective_count"
                ),

                resultSet.getInt(
                        "completed_objective_count"
                ),

                resultSet.getInt(
                        "cancelled_objective_count"
                ),

                resultSet.getInt(
                        "validated_objective_count"
                ),

                resultSet.getInt(
                        "unvalidated_completed_objective_count"
                ),

                resultSet.getInt(
                        "achieved_count"
                ),

                resultSet.getInt(
                        "partial_count"
                ),

                resultSet.getInt(
                        "not_achieved_count"
                ),

                resultSet.getDouble(
                        "validation_coverage_pct"
                ),

                resultSet.getDouble(
                        "realized_benefit_rate_pct"
                ),

                resultSet.getDouble(
                        "average_score_delta"
                ),

                resultSet.getDouble(
                        "average_assurance_debt_delta"
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
