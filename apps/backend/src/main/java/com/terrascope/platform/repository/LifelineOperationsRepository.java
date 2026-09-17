package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CreateLifelineAssetRequest;
import com.terrascope.platform.api.model.LifelineAsset;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.LifelineAssetCategory;
import com.terrascope.platform.domain.LifelineAssetStatus;
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
public class LifelineOperationsRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            category,
            name,
            description,
            priority,
            status,
            affected_population,
            owner_name,
            restoration_eta,
            created_at,
            updated_at,
            restored_at
            """;

    private final JdbcClient jdbcClient;

    public LifelineOperationsRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<LifelineAsset> findAll(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_lifeline_assets
                        WHERE incident_id = :incidentId
                        ORDER BY
                            CASE status
                                WHEN 'OUTAGE' THEN 0
                                WHEN 'DEGRADED' THEN 1
                                WHEN 'OPERATIONAL' THEN 2
                                ELSE 3
                            END,
                            CASE priority
                                WHEN 'CRITICAL' THEN 0
                                WHEN 'HIGH' THEN 1
                                WHEN 'MEDIUM' THEN 2
                                ELSE 3
                            END,
                            affected_population DESC,
                            updated_at DESC,
                            id DESC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapAsset
                )
                .list();
    }

    public Optional<LifelineAsset> findById(
            UUID incidentId,
            UUID assetId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_lifeline_assets
                        WHERE incident_id = :incidentId
                          AND id = :assetId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "assetId",
                        assetId
                )
                .query(
                        this::mapAsset
                )
                .optional();
    }

    public LifelineAsset create(
            UUID id,
            UUID incidentId,
            CreateLifelineAssetRequest request,
            LifelineAssetCategory category,
            IncidentActionPriority priority,
            LifelineAssetStatus status,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_lifeline_assets (
                                    id,
                                    incident_id,
                                    category,
                                    name,
                                    description,
                                    priority,
                                    status,
                                    affected_population,
                                    owner_name,
                                    restoration_eta,
                                    restored_at
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :category,
                                    :name,
                                    :description,
                                    :priority,
                                    :status,
                                    :affectedPopulation,
                                    :owner,
                                    :restorationEta,
                                    CASE
                                        WHEN :status = 'RESTORED'
                                            THEN NOW()
                                        ELSE NULL
                                    END
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
                                "name",
                                request.name().trim()
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
                                "status",
                                status.name()
                        )
                        .param(
                                "affectedPopulation",
                                request.affectedPopulation()
                        )
                        .param(
                                "owner",
                                owner
                        )
                        .param(
                                "restorationEta",
                                databaseTimestamp(
                                        request.restorationEta()
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireAsset(
                incidentId,
                createdId
        );
    }

    public LifelineAsset updateStatus(
            UUID incidentId,
            UUID assetId,
            LifelineAssetStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_lifeline_assets
                                SET
                                    status = :status,

                                    affected_population =
                                        CASE
                                            WHEN :status IN (
                                                'OPERATIONAL',
                                                'RESTORED'
                                            )
                                                THEN 0
                                            ELSE affected_population
                                        END,

                                    restoration_eta =
                                        CASE
                                            WHEN :status IN (
                                                'OPERATIONAL',
                                                'RESTORED'
                                            )
                                                THEN NULL
                                            ELSE restoration_eta
                                        END,

                                    restored_at =
                                        CASE
                                            WHEN :status = 'RESTORED'
                                                THEN COALESCE(
                                                    restored_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END,

                                    updated_at = NOW()

                                WHERE incident_id = :incidentId
                                  AND id = :assetId

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
                                "assetId",
                                assetId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Lifeline status update returned no row."
                                )
                        );

        return requireAsset(
                incidentId,
                updatedId
        );
    }

    public LifelineAsset updateImpact(
            UUID incidentId,
            UUID assetId,
            int affectedPopulation
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_lifeline_assets
                                SET
                                    affected_population =
                                        :affectedPopulation,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :assetId
                                RETURNING id
                                """)
                        .param(
                                "affectedPopulation",
                                affectedPopulation
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "assetId",
                                assetId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Lifeline impact update returned no row."
                                )
                        );

        return requireAsset(
                incidentId,
                updatedId
        );
    }

    public LifelineAsset updateOwner(
            UUID incidentId,
            UUID assetId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_lifeline_assets
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :assetId
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
                                "assetId",
                                assetId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Lifeline owner update returned no row."
                                )
                        );

        return requireAsset(
                incidentId,
                updatedId
        );
    }

    public LifelineAsset updateRestorationEta(
            UUID incidentId,
            UUID assetId,
            Instant restorationEta
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_lifeline_assets
                                SET
                                    restoration_eta =
                                        :restorationEta,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :assetId
                                RETURNING id
                                """)
                        .param(
                                "restorationEta",
                                databaseTimestamp(
                                        restorationEta
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "assetId",
                                assetId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Lifeline ETA update returned no row."
                                )
                        );

        return requireAsset(
                incidentId,
                updatedId
        );
    }

    public int delete(
            UUID incidentId,
            UUID assetId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM incident_lifeline_assets
                        WHERE incident_id = :incidentId
                          AND id = :assetId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "assetId",
                        assetId
                )
                .update();
    }

    private LifelineAsset requireAsset(
            UUID incidentId,
            UUID assetId
    ) {
        return findById(
                incidentId,
                assetId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Lifeline asset disappeared after mutation."
                )
        );
    }

    private LifelineAsset mapAsset(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime restorationEta =
                resultSet.getObject(
                        "restoration_eta",
                        OffsetDateTime.class
                );

        OffsetDateTime restoredAt =
                resultSet.getObject(
                        "restored_at",
                        OffsetDateTime.class
                );

        return new LifelineAsset(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),
                LifelineAssetCategory.valueOf(
                        resultSet.getString(
                                "category"
                        )
                ),
                resultSet.getString(
                        "name"
                ),
                resultSet.getString(
                        "description"
                ),
                IncidentActionPriority.valueOf(
                        resultSet.getString(
                                "priority"
                        )
                ),
                LifelineAssetStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),
                resultSet.getInt(
                        "affected_population"
                ),
                resultSet.getString(
                        "owner_name"
                ),
                restorationEta == null
                        ? null
                        : restorationEta.toInstant(),
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
