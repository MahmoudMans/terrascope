package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.SaveWatchlistEventRequest;
import com.terrascope.platform.api.model.WatchlistEntry;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.domain.WatchPriority;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WatchlistRepository {

    private static final String RETURNING_COLUMNS = """
            id,
            source_event_id,
            hazard_type,
            title,
            description,
            latitude,
            longitude,
            observed_at,
            source_provider,
            source_url,
            magnitude_value,
            magnitude_unit,
            priority,
            notes,
            created_at,
            updated_at
            """;

    private final JdbcClient jdbcClient;

    private final RowMapper<WatchlistEntry> rowMapper =
            this::mapRow;

    public WatchlistRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<WatchlistEntry> findAll() {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            source_event_id,
                            hazard_type,
                            title,
                            description,
                            latitude,
                            longitude,
                            observed_at,
                            source_provider,
                            source_url,
                            magnitude_value,
                            magnitude_unit,
                            priority,
                            notes,
                            created_at,
                            updated_at
                        FROM hazard_watchlist_entries
                        ORDER BY
                            CASE priority
                                WHEN 'CRITICAL' THEN 4
                                WHEN 'HIGH' THEN 3
                                WHEN 'MEDIUM' THEN 2
                                ELSE 1
                            END DESC,
                            created_at DESC,
                            id DESC
                        """)
                .query(
                        rowMapper
                )
                .list();
    }

    public Optional<WatchlistEntry> findById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            source_event_id,
                            hazard_type,
                            title,
                            description,
                            latitude,
                            longitude,
                            observed_at,
                            source_provider,
                            source_url,
                            magnitude_value,
                            magnitude_unit,
                            priority,
                            notes,
                            created_at,
                            updated_at
                        FROM hazard_watchlist_entries
                        WHERE id = :id
                        """)
                .param(
                        "id",
                        id
                )
                .query(
                        rowMapper
                )
                .optional();
    }

    public WatchlistEntry upsert(
            SaveWatchlistEventRequest request,
            HazardType hazardType,
            WatchPriority priority
    ) {
        UUID id =
                UUID.randomUUID();

        OffsetDateTime observedAt =
                request
                        .observedAt()
                        .atOffset(
                                ZoneOffset.UTC
                        );

        return jdbcClient
                .sql("""
                        INSERT INTO hazard_watchlist_entries (
                            id,
                            source_event_id,
                            hazard_type,
                            title,
                            description,
                            latitude,
                            longitude,
                            observed_at,
                            source_provider,
                            source_url,
                            magnitude_value,
                            magnitude_unit,
                            priority,
                            notes
                        )
                        VALUES (
                            :id,
                            :sourceEventId,
                            :hazardType,
                            :title,
                            :description,
                            :latitude,
                            :longitude,
                            :observedAt,
                            :sourceProvider,
                            :sourceUrl,
                            :magnitudeValue,
                            :magnitudeUnit,
                            :priority,
                            :notes
                        )
                        ON CONFLICT (
                            hazard_type,
                            source_event_id
                        )
                        DO UPDATE SET
                            title = EXCLUDED.title,
                            description = EXCLUDED.description,
                            latitude = EXCLUDED.latitude,
                            longitude = EXCLUDED.longitude,
                            observed_at = EXCLUDED.observed_at,
                            source_provider = EXCLUDED.source_provider,
                            source_url = EXCLUDED.source_url,
                            magnitude_value = EXCLUDED.magnitude_value,
                            magnitude_unit = EXCLUDED.magnitude_unit,
                            notes =
                                CASE
                                    WHEN BTRIM(EXCLUDED.notes) = ''
                                        THEN hazard_watchlist_entries.notes
                                    ELSE EXCLUDED.notes
                                END,
                            updated_at = NOW()
                        RETURNING
                        """
                        + RETURNING_COLUMNS)
                .param(
                        "id",
                        id
                )
                .param(
                        "sourceEventId",
                        request
                                .sourceEventId()
                                .trim()
                )
                .param(
                        "hazardType",
                        hazardType.key()
                )
                .param(
                        "title",
                        request
                                .title()
                                .trim()
                )
                .param(
                        "description",
                        safeText(
                                request.description()
                        )
                )
                .param(
                        "latitude",
                        request.latitude()
                )
                .param(
                        "longitude",
                        request.longitude()
                )
                .param(
                        "observedAt",
                        observedAt
                )
                .param(
                        "sourceProvider",
                        request
                                .sourceProvider()
                                .trim()
                )
                .param(
                        "sourceUrl",
                        safeText(
                                request.sourceUrl()
                        )
                )
                .param(
                        "magnitudeValue",
                        request.magnitudeValue(),
                        Types.DOUBLE
                )
                .param(
                        "magnitudeUnit",
                        safeText(
                                request.magnitudeUnit()
                        )
                )
                .param(
                        "priority",
                        priority.name()
                )
                .param(
                        "notes",
                        safeText(
                                request.notes()
                        )
                )
                .query(
                        rowMapper
                )
                .single();
    }

    public Optional<WatchlistEntry> updatePriority(
            UUID id,
            WatchPriority priority
    ) {
        return jdbcClient
                .sql("""
                        UPDATE hazard_watchlist_entries
                        SET
                            priority = :priority,
                            updated_at = NOW()
                        WHERE id = :id
                        RETURNING
                        """
                        + RETURNING_COLUMNS)
                .param(
                        "priority",
                        priority.name()
                )
                .param(
                        "id",
                        id
                )
                .query(
                        rowMapper
                )
                .optional();
    }

    public int deleteById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM hazard_watchlist_entries
                        WHERE id = :id
                        """)
                .param(
                        "id",
                        id
                )
                .update();
    }

    private WatchlistEntry mapRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        double magnitude =
                resultSet.getDouble(
                        "magnitude_value"
                );

        Double magnitudeValue =
                resultSet.wasNull()
                        ? null
                        : magnitude;

        OffsetDateTime observedAt =
                resultSet.getObject(
                        "observed_at",
                        OffsetDateTime.class
                );

        OffsetDateTime createdAt =
                resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class
                );

        OffsetDateTime updatedAt =
                resultSet.getObject(
                        "updated_at",
                        OffsetDateTime.class
                );

        return new WatchlistEntry(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getString(
                        "source_event_id"
                ),
                resultSet.getString(
                        "hazard_type"
                ),
                resultSet.getString(
                        "title"
                ),
                resultSet.getString(
                        "description"
                ),
                resultSet.getDouble(
                        "latitude"
                ),
                resultSet.getDouble(
                        "longitude"
                ),
                observedAt.toInstant(),
                resultSet.getString(
                        "source_provider"
                ),
                resultSet.getString(
                        "source_url"
                ),
                magnitudeValue,
                resultSet.getString(
                        "magnitude_unit"
                ),
                WatchPriority.valueOf(
                        resultSet.getString(
                                "priority"
                        )
                ),
                resultSet.getString(
                        "notes"
                ),
                createdAt.toInstant(),
                updatedAt.toInstant()
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
