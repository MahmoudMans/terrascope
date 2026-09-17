package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CreateDamageAssessmentRequest;
import com.terrascope.platform.api.model.DamageAssessment;
import com.terrascope.platform.domain.DamageAssetCategory;
import com.terrascope.platform.domain.DamageRecoveryStatus;
import com.terrascope.platform.domain.DamageSeverity;
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
public class DamageRecoveryRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            category,
            asset_name,
            description,
            severity,
            status,
            affected_units,
            estimated_loss_musd,
            owner_name,
            recovery_eta,
            repair_progress_pct,
            created_at,
            updated_at,
            verified_at,
            restored_at
            """;

    private final JdbcClient jdbcClient;

    public DamageRecoveryRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<DamageAssessment> findAll(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_damage_assessments
                        WHERE incident_id = :incidentId
                        ORDER BY
                            CASE status
                                WHEN 'REPORTED' THEN 0
                                WHEN 'REPAIRING' THEN 1
                                WHEN 'VERIFIED' THEN 2
                                ELSE 3
                            END,
                            CASE severity
                                WHEN 'DESTROYED' THEN 0
                                WHEN 'MAJOR' THEN 1
                                WHEN 'MODERATE' THEN 2
                                ELSE 3
                            END,
                            estimated_loss_musd DESC,
                            updated_at DESC,
                            id DESC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapAssessment
                )
                .list();
    }

    public Optional<DamageAssessment> findById(
            UUID incidentId,
            UUID assessmentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_damage_assessments
                        WHERE incident_id = :incidentId
                          AND id = :assessmentId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "assessmentId",
                        assessmentId
                )
                .query(
                        this::mapAssessment
                )
                .optional();
    }

    public DamageAssessment create(
            UUID id,
            UUID incidentId,
            CreateDamageAssessmentRequest request,
            DamageAssetCategory category,
            DamageSeverity severity,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_damage_assessments (
                                    id,
                                    incident_id,
                                    category,
                                    asset_name,
                                    description,
                                    severity,
                                    affected_units,
                                    estimated_loss_musd,
                                    owner_name,
                                    recovery_eta
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :category,
                                    :assetName,
                                    :description,
                                    :severity,
                                    :affectedUnits,
                                    :estimatedLossMusd,
                                    :owner,
                                    :recoveryEta
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
                                "category",
                                category.name()
                        )
                        .param(
                                "assetName",
                                request.assetName().trim()
                        )
                        .param(
                                "description",
                                safeText(
                                        request.description()
                                )
                        )
                        .param(
                                "severity",
                                severity.name()
                        )
                        .param(
                                "affectedUnits",
                                request.affectedUnits()
                        )
                        .param(
                                "estimatedLossMusd",
                                request.estimatedLossMusd()
                        )
                        .param(
                                "owner",
                                owner
                        )
                        .param(
                                "recoveryEta",
                                databaseTimestamp(
                                        request.recoveryEta()
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireAssessment(
                incidentId,
                createdId
        );
    }

    public DamageAssessment verify(
            UUID incidentId,
            UUID assessmentId
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_damage_assessments
                                SET
                                    status = 'VERIFIED',
                                    verified_at = COALESCE(
                                        verified_at,
                                        NOW()
                                    ),
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :assessmentId
                                RETURNING id
                                """)
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "assessmentId",
                                assessmentId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Damage verification returned no row."
                                )
                        );

        return requireAssessment(
                incidentId,
                updatedId
        );
    }

    public DamageAssessment updateProgress(
            UUID incidentId,
            UUID assessmentId,
            int repairProgressPct,
            DamageRecoveryStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_damage_assessments
                                SET
                                    repair_progress_pct =
                                        :repairProgressPct,

                                    status = :status,

                                    verified_at =
                                        COALESCE(
                                            verified_at,
                                            NOW()
                                        ),

                                    restored_at =
                                        CASE
                                            WHEN :status = 'RESTORED'
                                                THEN COALESCE(
                                                    restored_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END,

                                    recovery_eta =
                                        CASE
                                            WHEN :status = 'RESTORED'
                                                THEN NULL
                                            ELSE recovery_eta
                                        END,

                                    updated_at = NOW()

                                WHERE incident_id = :incidentId
                                  AND id = :assessmentId

                                RETURNING id
                                """)
                        .param(
                                "repairProgressPct",
                                repairProgressPct
                        )
                        .param(
                                "status",
                                status.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "assessmentId",
                                assessmentId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Damage progress update returned no row."
                                )
                        );

        return requireAssessment(
                incidentId,
                updatedId
        );
    }

    public DamageAssessment updateSeverity(
            UUID incidentId,
            UUID assessmentId,
            DamageSeverity severity
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_damage_assessments
                                SET
                                    severity = :severity,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :assessmentId
                                RETURNING id
                                """)
                        .param(
                                "severity",
                                severity.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "assessmentId",
                                assessmentId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Damage severity update returned no row."
                                )
                        );

        return requireAssessment(
                incidentId,
                updatedId
        );
    }

    public DamageAssessment updateOwner(
            UUID incidentId,
            UUID assessmentId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_damage_assessments
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :assessmentId
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
                                "assessmentId",
                                assessmentId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Damage owner update returned no row."
                                )
                        );

        return requireAssessment(
                incidentId,
                updatedId
        );
    }

    public DamageAssessment updateRecoveryEta(
            UUID incidentId,
            UUID assessmentId,
            Instant recoveryEta
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_damage_assessments
                                SET
                                    recovery_eta = :recoveryEta,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :assessmentId
                                RETURNING id
                                """)
                        .param(
                                "recoveryEta",
                                databaseTimestamp(
                                        recoveryEta
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "assessmentId",
                                assessmentId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Damage recovery ETA update returned no row."
                                )
                        );

        return requireAssessment(
                incidentId,
                updatedId
        );
    }

    public int delete(
            UUID incidentId,
            UUID assessmentId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM incident_damage_assessments
                        WHERE incident_id = :incidentId
                          AND id = :assessmentId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "assessmentId",
                        assessmentId
                )
                .update();
    }

    private DamageAssessment requireAssessment(
            UUID incidentId,
            UUID assessmentId
    ) {
        return findById(
                incidentId,
                assessmentId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Damage assessment disappeared after mutation."
                )
        );
    }

    private DamageAssessment mapAssessment(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime recoveryEta =
                resultSet.getObject(
                        "recovery_eta",
                        OffsetDateTime.class
                );

        OffsetDateTime verifiedAt =
                resultSet.getObject(
                        "verified_at",
                        OffsetDateTime.class
                );

        OffsetDateTime restoredAt =
                resultSet.getObject(
                        "restored_at",
                        OffsetDateTime.class
                );

        return new DamageAssessment(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),
                DamageAssetCategory.valueOf(
                        resultSet.getString(
                                "category"
                        )
                ),
                resultSet.getString(
                        "asset_name"
                ),
                resultSet.getString(
                        "description"
                ),
                DamageSeverity.valueOf(
                        resultSet.getString(
                                "severity"
                        )
                ),
                DamageRecoveryStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),
                resultSet.getInt(
                        "affected_units"
                ),
                resultSet.getDouble(
                        "estimated_loss_musd"
                ),
                resultSet.getString(
                        "owner_name"
                ),
                recoveryEta == null
                        ? null
                        : recoveryEta.toInstant(),
                resultSet.getInt(
                        "repair_progress_pct"
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
                verifiedAt == null
                        ? null
                        : verifiedAt.toInstant(),
                restoredAt == null
                        ? null
                        : restoredAt.toInstant()
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
