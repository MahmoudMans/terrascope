package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CreateIncidentActionRequest;
import com.terrascope.platform.api.model.IncidentActionItem;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentActionStatus;
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
public class IncidentActionRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            title,
            description,
            priority,
            status,
            owner_name,
            due_at,
            created_at,
            updated_at,
            completed_at
            """;

    private final JdbcClient jdbcClient;

    public IncidentActionRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<IncidentActionItem> findAll(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_action_items
                        WHERE incident_id = :incidentId
                        ORDER BY
                            CASE status
                                WHEN 'IN_PROGRESS' THEN 0
                                WHEN 'BLOCKED' THEN 1
                                WHEN 'TODO' THEN 2
                                ELSE 3
                            END,
                            CASE priority
                                WHEN 'CRITICAL' THEN 0
                                WHEN 'HIGH' THEN 1
                                WHEN 'MEDIUM' THEN 2
                                ELSE 3
                            END,
                            due_at NULLS LAST,
                            updated_at DESC,
                            id DESC
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapAction
                )
                .list();
    }

    public Optional<IncidentActionItem> findById(
            UUID incidentId,
            UUID actionId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_action_items
                        WHERE incident_id = :incidentId
                          AND id = :actionId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "actionId",
                        actionId
                )
                .query(
                        this::mapAction
                )
                .optional();
    }

    public IncidentActionItem create(
            UUID id,
            UUID incidentId,
            CreateIncidentActionRequest request,
            IncidentActionPriority priority,
            String owner
    ) {
        UUID createdId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_action_items (
                                    id,
                                    incident_id,
                                    title,
                                    description,
                                    priority,
                                    owner_name,
                                    due_at
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :title,
                                    :description,
                                    :priority,
                                    :owner,
                                    :dueAt
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
                                "title",
                                request.title().trim()
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
                                "owner",
                                owner
                        )
                        .param(
                                "dueAt",
                                databaseTimestamp(
                                        request.dueAt()
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return requireAction(
                incidentId,
                createdId
        );
    }

    public IncidentActionItem updateStatus(
            UUID incidentId,
            UUID actionId,
            IncidentActionStatus status
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_action_items
                                SET
                                    status = :status,
                                    updated_at = NOW(),
                                    completed_at =
                                        CASE
                                            WHEN :status = 'DONE'
                                                THEN COALESCE(
                                                    completed_at,
                                                    NOW()
                                                )
                                            ELSE NULL
                                        END
                                WHERE incident_id = :incidentId
                                  AND id = :actionId
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
                                "actionId",
                                actionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Action status update returned no row."
                                )
                        );

        return requireAction(
                incidentId,
                updatedId
        );
    }

    public IncidentActionItem updatePriority(
            UUID incidentId,
            UUID actionId,
            IncidentActionPriority priority
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_action_items
                                SET
                                    priority = :priority,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :actionId
                                RETURNING id
                                """)
                        .param(
                                "priority",
                                priority.name()
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "actionId",
                                actionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Action priority update returned no row."
                                )
                        );

        return requireAction(
                incidentId,
                updatedId
        );
    }

    public IncidentActionItem updateOwner(
            UUID incidentId,
            UUID actionId,
            String owner
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_action_items
                                SET
                                    owner_name = :owner,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :actionId
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
                                "actionId",
                                actionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Action owner update returned no row."
                                )
                        );

        return requireAction(
                incidentId,
                updatedId
        );
    }

    public IncidentActionItem updateDueAt(
            UUID incidentId,
            UUID actionId,
            Instant dueAt
    ) {
        UUID updatedId =
                jdbcClient
                        .sql("""
                                UPDATE incident_action_items
                                SET
                                    due_at = :dueAt,
                                    updated_at = NOW()
                                WHERE incident_id = :incidentId
                                  AND id = :actionId
                                RETURNING id
                                """)
                        .param(
                                "dueAt",
                                databaseTimestamp(
                                        dueAt
                                ),
                                Types.TIMESTAMP_WITH_TIMEZONE
                        )
                        .param(
                                "incidentId",
                                incidentId
                        )
                        .param(
                                "actionId",
                                actionId
                        )
                        .query(
                                UUID.class
                        )
                        .optional()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Action due date update returned no row."
                                )
                        );

        return requireAction(
                incidentId,
                updatedId
        );
    }

    public int delete(
            UUID incidentId,
            UUID actionId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM incident_action_items
                        WHERE incident_id = :incidentId
                          AND id = :actionId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .param(
                        "actionId",
                        actionId
                )
                .update();
    }

    private IncidentActionItem requireAction(
            UUID incidentId,
            UUID actionId
    ) {
        return findById(
                incidentId,
                actionId
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Action "
                                + actionId
                                + " disappeared after mutation."
                )
        );
    }

    private IncidentActionItem mapAction(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime dueAt =
                resultSet.getObject(
                        "due_at",
                        OffsetDateTime.class
                );

        OffsetDateTime completedAt =
                resultSet.getObject(
                        "completed_at",
                        OffsetDateTime.class
                );

        return new IncidentActionItem(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),
                resultSet.getString(
                        "title"
                ),
                resultSet.getString(
                        "description"
                ),
                IncidentActionPriority.valueOf(
                        resultSet.getString(
                                "priority"
                        )
                ),
                IncidentActionStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),
                resultSet.getString(
                        "owner_name"
                ),
                dueAt == null
                        ? null
                        : dueAt.toInstant(),
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
                completedAt == null
                        ? null
                        : completedAt.toInstant()
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
