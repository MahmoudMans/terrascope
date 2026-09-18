package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.PortfolioObjectiveValidation;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.domain.PortfolioAssuranceBand;
import com.terrascope.platform.domain.PortfolioObjectiveOutcome;
import com.terrascope.platform.domain.PortfolioObjectiveOutcomeBaseline;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PortfolioObjectiveOutcomeRepository {

    private static final String VALIDATION_COLUMNS = """
            id,
            objective_id,
            validation_number,
            baseline_snapshot_id,
            baseline_snapshot_number,
            validation_snapshot_id,
            validation_snapshot_number,
            outcome,
            baseline_score,
            validation_score,
            score_delta,
            baseline_assurance_debt,
            validation_assurance_debt,
            assurance_debt_delta,
            certified_incident_delta,
            validated_improvement_delta,
            audit_verified_incident_delta,
            executive_summary,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public PortfolioObjectiveOutcomeRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<PortfolioObjectiveOutcomeBaseline> findBaselineForReview(
            UUID portfolioReviewId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            snapshot.id
                                AS snapshot_id,

                            snapshot.snapshot_number,

                            snapshot.score,

                            snapshot.certified_incident_count,

                            snapshot.audit_verified_incident_count,

                            snapshot.validated_improvement_count,

                            snapshot.untested_improvement_count,

                            snapshot.needs_revalidation_count,

                            snapshot.failed_improvement_count,

                            snapshot.open_corrective_action_count

                        FROM resilience_portfolio_reviews review

                        JOIN resilience_portfolio_snapshots snapshot
                          ON snapshot.id =
                             review.current_snapshot_id

                        WHERE review.id =
                              :portfolioReviewId
                        """)
                .param(
                        "portfolioReviewId",
                        portfolioReviewId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) ->
                                new PortfolioObjectiveOutcomeBaseline(
                                        resultSet.getObject(
                                                "snapshot_id",
                                                UUID.class
                                        ),

                                        resultSet.getInt(
                                                "snapshot_number"
                                        ),

                                        resultSet.getInt(
                                                "score"
                                        ),

                                        resultSet.getInt(
                                                "certified_incident_count"
                                        ),

                                        resultSet.getInt(
                                                "audit_verified_incident_count"
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
                                        )
                                )
                )
                .optional();
    }

    public int nextValidationNumber(
            UUID objectiveId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(validation_number),
                                0
                            ) + 1

                        FROM resilience_portfolio_objective_validations

                        WHERE objective_id =
                              :objectiveId
                        """)
                .param(
                        "objectiveId",
                        objectiveId
                )
                .query(
                        Integer.class
                )
                .single();
    }

    public PortfolioObjectiveValidation saveValidation(
            UUID id,
            UUID objectiveId,
            int validationNumber,
            PortfolioObjectiveOutcomeBaseline baseline,
            ResiliencePortfolioSnapshot validationSnapshot,
            PortfolioObjectiveOutcome outcome,
            int validationDebt,
            String executiveSummary
    ) {
        int scoreDelta =
                validationSnapshot.score()
                - baseline.score();

        int debtDelta =
                validationDebt
                - baseline.assuranceDebt();

        int certifiedDelta =
                validationSnapshot.certifiedIncidentCount()
                - baseline.certifiedIncidentCount();

        int validatedDelta =
                validationSnapshot.validatedImprovementCount()
                - baseline.validatedImprovementCount();

        int auditDelta =
                validationSnapshot.auditVerifiedIncidentCount()
                - baseline.auditVerifiedIncidentCount();

        UUID validationId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_portfolio_objective_validations (
                                    id,
                                    objective_id,
                                    validation_number,
                                    baseline_snapshot_id,
                                    baseline_snapshot_number,
                                    validation_snapshot_id,
                                    validation_snapshot_number,
                                    outcome,
                                    baseline_score,
                                    validation_score,
                                    score_delta,
                                    baseline_assurance_debt,
                                    validation_assurance_debt,
                                    assurance_debt_delta,
                                    certified_incident_delta,
                                    validated_improvement_delta,
                                    audit_verified_incident_delta,
                                    executive_summary
                                )
                                VALUES (
                                    :id,
                                    :objectiveId,
                                    :validationNumber,
                                    :baselineSnapshotId,
                                    :baselineSnapshotNumber,
                                    :validationSnapshotId,
                                    :validationSnapshotNumber,
                                    :outcome,
                                    :baselineScore,
                                    :validationScore,
                                    :scoreDelta,
                                    :baselineAssuranceDebt,
                                    :validationAssuranceDebt,
                                    :assuranceDebtDelta,
                                    :certifiedIncidentDelta,
                                    :validatedImprovementDelta,
                                    :auditVerifiedIncidentDelta,
                                    :executiveSummary
                                )
                                RETURNING id
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
                                "validationNumber",
                                validationNumber
                        )
                        .param(
                                "baselineSnapshotId",
                                baseline.snapshotId()
                        )
                        .param(
                                "baselineSnapshotNumber",
                                baseline.snapshotNumber()
                        )
                        .param(
                                "validationSnapshotId",
                                validationSnapshot.id()
                        )
                        .param(
                                "validationSnapshotNumber",
                                validationSnapshot.snapshotNumber()
                        )
                        .param(
                                "outcome",
                                outcome.name()
                        )
                        .param(
                                "baselineScore",
                                baseline.score()
                        )
                        .param(
                                "validationScore",
                                validationSnapshot.score()
                        )
                        .param(
                                "scoreDelta",
                                scoreDelta
                        )
                        .param(
                                "baselineAssuranceDebt",
                                baseline.assuranceDebt()
                        )
                        .param(
                                "validationAssuranceDebt",
                                validationDebt
                        )
                        .param(
                                "assuranceDebtDelta",
                                debtDelta
                        )
                        .param(
                                "certifiedIncidentDelta",
                                certifiedDelta
                        )
                        .param(
                                "validatedImprovementDelta",
                                validatedDelta
                        )
                        .param(
                                "auditVerifiedIncidentDelta",
                                auditDelta
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
                validationId
        );
    }

    public List<PortfolioObjectiveValidation> findHistory(
            UUID objectiveId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + VALIDATION_COLUMNS
                        + """
                        FROM resilience_portfolio_objective_validations

                        WHERE objective_id =
                              :objectiveId

                        ORDER BY
                            validation_number DESC,
                            created_at DESC
                        """)
                .param(
                        "objectiveId",
                        objectiveId
                )
                .query(
                        this::mapValidation
                )
                .list();
    }

    private PortfolioObjectiveValidation findById(
            UUID validationId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + VALIDATION_COLUMNS
                        + """
                        FROM resilience_portfolio_objective_validations
                        WHERE id = :validationId
                        """)
                .param(
                        "validationId",
                        validationId
                )
                .query(
                        this::mapValidation
                )
                .single();
    }

    private PortfolioObjectiveValidation mapValidation(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new PortfolioObjectiveValidation(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "objective_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "validation_number"
                ),

                resultSet.getObject(
                        "baseline_snapshot_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "baseline_snapshot_number"
                ),

                resultSet.getObject(
                        "validation_snapshot_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "validation_snapshot_number"
                ),

                PortfolioObjectiveOutcome.valueOf(
                        resultSet.getString(
                                "outcome"
                        )
                ),

                resultSet.getInt(
                        "baseline_score"
                ),

                resultSet.getInt(
                        "validation_score"
                ),

                resultSet.getInt(
                        "score_delta"
                ),

                resultSet.getInt(
                        "baseline_assurance_debt"
                ),

                resultSet.getInt(
                        "validation_assurance_debt"
                ),

                resultSet.getInt(
                        "assurance_debt_delta"
                ),

                resultSet.getInt(
                        "certified_incident_delta"
                ),

                resultSet.getInt(
                        "validated_improvement_delta"
                ),

                resultSet.getInt(
                        "audit_verified_incident_delta"
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
