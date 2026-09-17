package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CreateIncidentRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentTimelineEvent;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IncidentResponseRepository {

    private static final String INCIDENT_SELECT = """
            SELECT
                incident.id,
                incident.alert_event_id,
                incident.source_event_id,
                incident.hazard_type,
                incident.title,
                incident.description,
                incident.event_latitude,
                incident.event_longitude,
                incident.severity,
                incident.status,
                incident.owner_name,
                (
                    SELECT COUNT(*)
                    FROM incident_timeline_events timeline
                    WHERE timeline.incident_id = incident.id
                ) AS timeline_event_count,
                incident.opened_at,
                incident.updated_at,
                incident.closed_at
            FROM incident_cases incident
            """;

    private final JdbcClient jdbcClient;

    public IncidentResponseRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<IncidentCase> findAll() {
        return jdbcClient
                .sql(
                        INCIDENT_SELECT
                                + """
                                ORDER BY
                                    CASE incident.status
                                        WHEN 'RESPONDING' THEN 0
                                        WHEN 'OPEN' THEN 1
                                        WHEN 'MONITORING' THEN 2
                                        WHEN 'STABILIZED' THEN 3
                                        ELSE 4
                                    END,
                                    CASE incident.severity
                                        WHEN 'CRITICAL' THEN 0
                                        WHEN 'SEVERE' THEN 1
                                        WHEN 'HIGH' THEN 2
                                        WHEN 'MODERATE' THEN 3
                                        ELSE 4
                                    END,
                                    incident.updated_at DESC,
                                    incident.id DESC
                                LIMIT 200
                                """
                )
                .query(
                        this::mapIncident
                )
                .list();
    }

    public Optional<IncidentCase> findById(
            UUID id
    ) {
        return jdbcClient
                .sql(
                        INCIDENT_SELECT
                                + """
                                WHERE incident.id = :id
                                """
                )
                .param(
                        "id",
                        id
                )
                .query(
                        this::mapIncident
                )
                .optional();
    }

    public Optional<IncidentCase> findBySource(
            String hazardType,
            String sourceEventId
    ) {
        return jdbcClient
                .sql(
                        INCIDENT_SELECT
                                + """
                                WHERE incident.hazard_type = :hazardType
                                  AND incident.source_event_id = :sourceEventId
                                """
                )
                .param(
                        "hazardType",
                        hazardType
                )
                .param(
                        "sourceEventId",
                        sourceEventId
                )
                .query(
                        this::mapIncident
                )
                .optional();
    }

    public IncidentCase create(
            UUID id,
            CreateIncidentRequest request,
            String hazardType,
            IncidentSeverity severity,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_cases (
                                    id,
                                    alert_event_id,
                                    source_event_id,
                                    hazard_type,
                                    title,
                                    description,
                                    event_latitude,
                                    event_longitude,
                                    severity,
                                    owner_name
                                )
                                VALUES (
                                    :id,
                                    :alertEventId,
                                    :sourceEventId,
                                    :hazardType,
                                    :title,
                                    :description,
                                    :latitude,
                                    :longitude,
                                    :severity,
                                    :owner
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "alertEventId",
                                request.alertEventId(),
                                Types.OTHER
                        )
                        .param(
                                "sourceEventId",
                                request
                                        .sourceEventId()
                                        .trim()
                        )
                        .param(
                                "hazardType",
                                hazardType
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
                                "severity",
                                severity.name()
                        )
                        .param(
                                "owner",
                                owner
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireIncident(
                createdId
        );
    }

    public IncidentCase updateStatus(
            UUID id,
            IncidentStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_cases
                                SET
                                    status = :status,
                                    updated_at = NOW(),
                                    closed_at =
                                        CASE
                                            WHEN :status = 'CLOSED'
                                                THEN COALESCE(
                                                    closed_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END
                                WHERE id = :id
                                RETURNING id
                                """)
                        .param(
                                "status",
                                status.name()
                        )
                        .param(
                                "id",
                                id
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Incident update returned no row."
                                )
                        );

        return requireIncident(
                updatedId
        );
    }

    public IncidentCase updateSeverity(
            UUID id,
            IncidentSeverity severity
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_cases
                                SET
                                    severity = :severity,
                                    updated_at = NOW()
                                WHERE id = :id
                                RETURNING id
                                """)
                        .param(
                                "severity",
                                severity.name()
                        )
                        .param(
                                "id",
                                id
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Incident severity update returned no row."
                                )
                        );

        return requireIncident(
                updatedId
        );
    }

    public IncidentCase updateOwner(
            UUID id,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_cases
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE id = :id
                                RETURNING id
                                """)
                        .param(
                                "owner",
                                owner
                        )
                        .param(
                                "id",
                                id
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Incident owner update returned no row."
                                )
                        );

        return requireIncident(
                updatedId
        );
    }

    public IncidentTimelineEvent appendTimeline(
            UUID id,
            UUID incidentId,
            String eventType,
            String message
    ) {
        return jdbcClient
                .sql("""
                        INSERT INTO incident_timeline_events (
                            id,
                            incident_id,
                            event_type,
                            message
                        )
                        VALUES (
                            :id,
                            :incidentId,
                            :eventType,
                            :message
                        )
                        RETURNING
                            id,
                            incident_id,
                            event_type,
                            message,
                            created_at
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
                        "eventType",
                        eventType
                )
                .param(
                        "message",
                        message
                )
                .query(
                        this::mapTimeline
                )
                .single();
    }

    public List<IncidentTimelineEvent> timeline(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            incident_id,
                            event_type,
                            message,
                            created_at
                        FROM incident_timeline_events
                        WHERE incident_id = :incidentId
                        ORDER BY
                            created_at DESC,
                            id DESC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapTimeline
                )
                .list();
    }

    private IncidentCase requireIncident(
            UUID id
    ) {
        return findById(
                id
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Incident "
                                + id
                                + " disappeared after mutation."
                )
        );
    }

    private IncidentCase mapIncident(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime closedAt =
                resultSet.getObject(
                        "closed_at",
                        OffsetDateTime.class
                );

        return new IncidentCase(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "alert_event_id",
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
                        "event_latitude"
                ),
                resultSet.getDouble(
                        "event_longitude"
                ),
                IncidentSeverity.valueOf(
                        resultSet.getString(
                                "severity"
                        )
                ),
                IncidentStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),
                resultSet.getString(
                        "owner_name"
                ),
                resultSet.getInt(
                        "timeline_event_count"
                ),
                resultSet
                        .getObject(
                                "opened_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),
                resultSet
                        .getObject(
                                "updated_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),
                closedAt == null
                        ? null
                        : closedAt.toInstant()
        );
    }

    private IncidentTimelineEvent mapTimeline(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new IncidentTimelineEvent(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),
                resultSet.getString(
                        "event_type"
                ),
                resultSet.getString(
                        "message"
                ),
                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
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
