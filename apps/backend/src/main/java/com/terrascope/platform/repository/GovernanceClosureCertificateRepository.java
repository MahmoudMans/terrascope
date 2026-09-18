package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.BenefitRealizationSnapshot;
import com.terrascope.platform.api.model.GovernanceClosureCertificate;
import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.GovernanceClosureDossierMaterial;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GovernanceClosureCertificateRepository {

    private static final String CERTIFICATE_COLUMNS = """
            id,
            certificate_number,
            benefit_snapshot_id,
            benefit_snapshot_number,
            band,
            closure_ready,
            total_objective_count,
            completed_objective_count,
            validated_objective_count,
            achieved_count,
            validation_coverage_pct,
            realized_benefit_rate_pct,
            average_score_delta,
            average_assurance_debt_delta,
            previous_certificate_hash,
            dossier_version,
            dossier_hash,
            certificate_version,
            certificate_hash,
            executive_summary,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public GovernanceClosureCertificateRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public int nextCertificateNumber() {
        return GovernanceTransactionalSequenceAllocator.next(
                jdbcClient,
                "GOV_CLOSURE_CERTIFICATES_CERTIFICATE_NUMBER_V1"
        );
    }

    public Optional<GovernanceClosureCertificate> findLatest() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + CERTIFICATE_COLUMNS
                        + """
                        FROM resilience_governance_closure_certificates

                        ORDER BY
                            certificate_number DESC,
                            created_at DESC,
                            id DESC

                        LIMIT 1
                        """)
                .query(
                        this::mapCertificate
                )
                .optional();
    }

    public GovernanceClosureCertificate save(
            UUID id,

            int certificateNumber,

            GovernanceClosureDossierMaterial material,

            String previousCertificateHash,

            String dossierVersion,
            String dossierHash,

            String certificateVersion,
            String certificateHash,

            String executiveSummary
    ) {
        UUID certificateId =
                jdbcClient
                        .sql("""
                                INSERT INTO resilience_governance_closure_certificates (
                                    id,
                                    certificate_number,
                                    benefit_snapshot_id,
                                    benefit_snapshot_number,
                                    band,
                                    closure_ready,
                                    total_objective_count,
                                    completed_objective_count,
                                    validated_objective_count,
                                    achieved_count,
                                    validation_coverage_pct,
                                    realized_benefit_rate_pct,
                                    average_score_delta,
                                    average_assurance_debt_delta,
                                    previous_certificate_hash,
                                    dossier_version,
                                    dossier_hash,
                                    certificate_version,
                                    certificate_hash,
                                    executive_summary
                                )
                                VALUES (
                                    :id,
                                    :certificateNumber,
                                    :benefitSnapshotId,
                                    :benefitSnapshotNumber,
                                    :band,
                                    :closureReady,
                                    :totalObjectiveCount,
                                    :completedObjectiveCount,
                                    :validatedObjectiveCount,
                                    :achievedCount,
                                    :validationCoveragePct,
                                    :realizedBenefitRatePct,
                                    :averageScoreDelta,
                                    :averageAssuranceDebtDelta,
                                    :previousCertificateHash,
                                    :dossierVersion,
                                    :dossierHash,
                                    :certificateVersion,
                                    :certificateHash,
                                    :executiveSummary
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "certificateNumber",
                                certificateNumber
                        )
                        .param(
                                "benefitSnapshotId",
                                material.benefitSnapshotId()
                        )
                        .param(
                                "benefitSnapshotNumber",
                                material.benefitSnapshotNumber()
                        )
                        .param(
                                "band",
                                material.band().name()
                        )
                        .param(
                                "closureReady",
                                material.closureReady()
                        )
                        .param(
                                "totalObjectiveCount",
                                material.totalObjectiveCount()
                        )
                        .param(
                                "completedObjectiveCount",
                                material.completedObjectiveCount()
                        )
                        .param(
                                "validatedObjectiveCount",
                                material.validatedObjectiveCount()
                        )
                        .param(
                                "achievedCount",
                                material.achievedCount()
                        )
                        .param(
                                "validationCoveragePct",
                                material.validationCoveragePct()
                        )
                        .param(
                                "realizedBenefitRatePct",
                                material.realizedBenefitRatePct()
                        )
                        .param(
                                "averageScoreDelta",
                                material.averageScoreDelta()
                        )
                        .param(
                                "averageAssuranceDebtDelta",
                                material.averageAssuranceDebtDelta()
                        )
                        .param(
                                "previousCertificateHash",
                                previousCertificateHash,
                                java.sql.Types.VARCHAR
                        )
                        .param(
                                "dossierVersion",
                                dossierVersion
                        )
                        .param(
                                "dossierHash",
                                dossierHash
                        )
                        .param(
                                "certificateVersion",
                                certificateVersion
                        )
                        .param(
                                "certificateHash",
                                certificateHash
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
                certificateId
        );
    }

    public List<GovernanceClosureCertificate> findHistory() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + CERTIFICATE_COLUMNS
                        + """
                        FROM resilience_governance_closure_certificates

                        ORDER BY
                            certificate_number DESC,
                            created_at DESC
                        """)
                .query(
                        this::mapCertificate
                )
                .list();
    }

    public List<GovernanceClosureCertificate> findChain() {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + CERTIFICATE_COLUMNS
                        + """
                        FROM resilience_governance_closure_certificates

                        ORDER BY
                            certificate_number ASC,
                            created_at ASC,
                            id ASC
                        """)
                .query(
                        this::mapCertificate
                )
                .list();
    }

    public Optional<BenefitRealizationSnapshot> findBenefitSnapshot(
            UUID snapshotId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
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

                        FROM resilience_benefit_realization_snapshots

                        WHERE id = :snapshotId
                        """)
                .param(
                        "snapshotId",
                        snapshotId
                )
                .query(
                        this::mapBenefitSnapshot
                )
                .optional();
    }

    public GovernanceClosureCertificate findCertificate(
            UUID certificateId
    ) {
        return findById(
                certificateId
        );
    }

    private GovernanceClosureCertificate findById(
            UUID certificateId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + CERTIFICATE_COLUMNS
                        + """
                        FROM resilience_governance_closure_certificates

                        WHERE id = :certificateId
                        """)
                .param(
                        "certificateId",
                        certificateId
                )
                .query(
                        this::mapCertificate
                )
                .single();
    }

    private GovernanceClosureCertificate mapCertificate(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new GovernanceClosureCertificate(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getInt(
                        "certificate_number"
                ),

                resultSet.getObject(
                        "benefit_snapshot_id",
                        UUID.class
                ),

                resultSet.getInt(
                        "benefit_snapshot_number"
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
                        "completed_objective_count"
                ),

                resultSet.getInt(
                        "validated_objective_count"
                ),

                resultSet.getInt(
                        "achieved_count"
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

                resultSet.getString(
                        "previous_certificate_hash"
                ),

                resultSet.getString(
                        "dossier_version"
                ),

                resultSet.getString(
                        "dossier_hash"
                ),

                resultSet.getString(
                        "certificate_version"
                ),

                resultSet.getString(
                        "certificate_hash"
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

    private BenefitRealizationSnapshot mapBenefitSnapshot(
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
