package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.ResiliencePortfolioReview;
import com.terrascope.platform.api.model.ResiliencePortfolioReviewPreview;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.domain.PortfolioTrendDirection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ResiliencePortfolioReviewRepository {

    private static final String REVIEW_COLUMNS = """
            id,
            review_number,
            current_snapshot_id,
            current_snapshot_number,
            previous_snapshot_id,
            previous_snapshot_number,
            trend,
            current_score,
            previous_score,
            score_delta,
            current_assurance_debt,
            previous_assurance_debt,
            assurance_debt_delta,
            certified_incident_delta,
            validated_improvement_delta,
            audit_verified_incident_delta,
            executive_summary,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public ResiliencePortfolioReviewRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextReviewNumber() {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(review_number),
                                0
                            ) + 1
                        FROM resilience_portfolio_reviews
                        """)
                .query(
                        Integer.class
                )
                .single();
    }

    public ResiliencePortfolioReview saveReview(
            UUID id,
            int reviewNumber,
            ResiliencePortfolioSnapshot current,
            ResiliencePortfolioSnapshot previous,
            ResiliencePortfolioReviewPreview preview
    ) {
        UUID reviewId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_portfolio_reviews (
                                    id,
                                    review_number,
                                    current_snapshot_id,
                                    current_snapshot_number,
                                    previous_snapshot_id,
                                    previous_snapshot_number,
                                    trend,
                                    current_score,
                                    previous_score,
                                    score_delta,
                                    current_assurance_debt,
                                    previous_assurance_debt,
                                    assurance_debt_delta,
                                    certified_incident_delta,
                                    validated_improvement_delta,
                                    audit_verified_incident_delta,
                                    executive_summary
                                )
                                VALUES (
                                    :id,
                                    :reviewNumber,
                                    :currentSnapshotId,
                                    :currentSnapshotNumber,
                                    :previousSnapshotId,
                                    :previousSnapshotNumber,
                                    :trend,
                                    :currentScore,
                                    :previousScore,
                                    :scoreDelta,
                                    :currentAssuranceDebt,
                                    :previousAssuranceDebt,
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
                                "reviewNumber",
                                reviewNumber
                        )
                        .param(
                                "currentSnapshotId",
                                current.id()
                        )
                        .param(
                                "currentSnapshotNumber",
                                current.snapshotNumber()
                        )
                        .param(
                                "previousSnapshotId",
                                previous == null
                                        ? null
                                        : previous.id(),
                                java.sql.Types.OTHER
                        )
                        .param(
                                "previousSnapshotNumber",
                                previous == null
                                        ? null
                                        : previous.snapshotNumber(),
                                java.sql.Types.INTEGER
                        )
                        .param(
                                "trend",
                                preview.trend().name()
                        )
                        .param(
                                "currentScore",
                                preview.currentScore()
                        )
                        .param(
                                "previousScore",
                                preview.previousScore(),
                                java.sql.Types.INTEGER
                        )
                        .param(
                                "scoreDelta",
                                preview.scoreDelta()
                        )
                        .param(
                                "currentAssuranceDebt",
                                preview.currentAssuranceDebt()
                        )
                        .param(
                                "previousAssuranceDebt",
                                preview.previousAssuranceDebt(),
                                java.sql.Types.INTEGER
                        )
                        .param(
                                "assuranceDebtDelta",
                                preview.assuranceDebtDelta()
                        )
                        .param(
                                "certifiedIncidentDelta",
                                preview.certifiedIncidentDelta()
                        )
                        .param(
                                "validatedImprovementDelta",
                                preview.validatedImprovementDelta()
                        )
                        .param(
                                "auditVerifiedIncidentDelta",
                                preview.auditVerifiedIncidentDelta()
                        )
                        .param(
                                "executiveSummary",
                                preview.executiveSummary()
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findById(
                reviewId
        );
    }

    public List<ResiliencePortfolioReview> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + REVIEW_COLUMNS
                        + """
                        FROM resilience_portfolio_reviews
                        ORDER BY
                            review_number DESC,
                            created_at DESC
                        LIMIT 30
                        """)
                .query(
                        this::mapReview
                )
                .list();
    }

    private ResiliencePortfolioReview findById(
            UUID reviewId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + REVIEW_COLUMNS
                        + """
                        FROM resilience_portfolio_reviews
                        WHERE id = :reviewId
                        """)
                .param(
                        "reviewId",
                        reviewId
                )
                .query(
                        this::mapReview
                )
                .single();
    }

    private ResiliencePortfolioReview mapReview(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new ResiliencePortfolioReview(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "review_number"
                ),

                resultSet.getObject(
                        "current_snapshot_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "current_snapshot_number"
                ),

                resultSet.getObject(
                        "previous_snapshot_id",
                        UUID.class
                ),

                resultSet.getObject(
                        "previous_snapshot_number",
                        Integer.class
                ),

                PortfolioTrendDirection.valueOf(
                        resultSet.getString(
                                "trend"
                        )
                ),

                resultSet.getInt(
                        "current_score"
                ),

                resultSet.getObject(
                        "previous_score",
                        Integer.class
                ),

                resultSet.getInt(
                        "score_delta"
                ),

                resultSet.getInt(
                        "current_assurance_debt"
                ),

                resultSet.getObject(
                        "previous_assurance_debt",
                        Integer.class
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
