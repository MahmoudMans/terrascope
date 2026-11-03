package com.terrascope.platform.repository;

import com.terrascope.platform.activity.ActivityEntry;
import com.terrascope.platform.activity.ActivityStore;
import com.terrascope.platform.security.Role;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class ActivityRepository implements ActivityStore {

    private static final String COLUMNS = """
            id,
            occurred_at,
            actor_id,
            actor_username,
            actor_display_name,
            actor_role,
            http_method,
            route,
            action,
            entity_type,
            entity_id,
            status_code
            """;

    private final JdbcClient jdbcClient;

    private final RowMapper<ActivityEntry> rowMapper =
            this::mapRow;

    public ActivityRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void record(
            ActivityEntry entry
    ) {
        jdbcClient
                .sql("""
                        INSERT INTO activity_log (
                            id,
                            occurred_at,
                            actor_id,
                            actor_username,
                            actor_display_name,
                            actor_role,
                            http_method,
                            route,
                            action,
                            entity_type,
                            entity_id,
                            status_code
                        )
                        VALUES (
                            :id,
                            :occurredAt,
                            :actorId,
                            :actorUsername,
                            :actorDisplayName,
                            :actorRole,
                            :method,
                            :route,
                            :action,
                            :entityType,
                            :entityId,
                            :statusCode
                        )
                        """)
                .param(
                        "id",
                        entry.id()
                )
                .param(
                        "occurredAt",
                        entry
                                .occurredAt()
                                .atOffset(
                                        ZoneOffset.UTC
                                )
                )
                .param(
                        "actorId",
                        entry.actorId(),
                        Types.OTHER
                )
                .param(
                        "actorUsername",
                        entry.actorUsername()
                )
                .param(
                        "actorDisplayName",
                        entry.actorDisplayName()
                )
                .param(
                        "actorRole",
                        entry.actorRole() == null
                                ? null
                                : entry.actorRole().name(),
                        Types.VARCHAR
                )
                .param(
                        "method",
                        entry.method()
                )
                .param(
                        "route",
                        entry.route()
                )
                .param(
                        "action",
                        entry.action()
                )
                .param(
                        "entityType",
                        entry.entityType(),
                        Types.VARCHAR
                )
                .param(
                        "entityId",
                        entry.entityId(),
                        Types.VARCHAR
                )
                .param(
                        "statusCode",
                        entry.statusCode()
                )
                .update();
    }

    @Override
    public List<ActivityEntry> recent(
            int limit,
            Instant before,
            String actor
    ) {
        StringBuilder sql =
                new StringBuilder("SELECT ")
                        .append(COLUMNS)
                        .append(" FROM activity_log WHERE TRUE");

        if (before != null) {
            sql.append(" AND occurred_at < :before");
        }

        if (actor != null) {
            sql.append(" AND actor_username = :actor");
        }

        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit");

        JdbcClient.StatementSpec statement =
                jdbcClient
                        .sql(sql.toString())
                        .param(
                                "limit",
                                limit
                        );

        if (before != null) {
            statement =
                    statement.param(
                            "before",
                            before.atOffset(
                                    ZoneOffset.UTC
                            )
                    );
        }

        if (actor != null) {
            statement =
                    statement.param(
                            "actor",
                            actor
                    );
        }

        return statement
                .query(
                        rowMapper
                )
                .list();
    }

    @Override
    public int deleteOlderThan(
            Instant cutoff
    ) {
        return jdbcClient
                .sql("""
                        DELETE FROM activity_log
                        WHERE occurred_at < :cutoff
                        """)
                .param(
                        "cutoff",
                        cutoff.atOffset(
                                ZoneOffset.UTC
                        )
                )
                .update();
    }

    private ActivityEntry mapRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        OffsetDateTime occurredAt =
                resultSet.getObject(
                        "occurred_at",
                        OffsetDateTime.class
                );

        String role =
                resultSet.getString(
                        "actor_role"
                );

        return new ActivityEntry(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                occurredAt.toInstant(),
                resultSet.getObject(
                        "actor_id",
                        UUID.class
                ),
                resultSet.getString(
                        "actor_username"
                ),
                resultSet.getString(
                        "actor_display_name"
                ),
                role == null
                        ? null
                        : Role.valueOf(role),
                resultSet.getString(
                        "http_method"
                ),
                resultSet.getString(
                        "route"
                ),
                resultSet.getString(
                        "action"
                ),
                resultSet.getString(
                        "entity_type"
                ),
                resultSet.getString(
                        "entity_id"
                ),
                resultSet.getInt(
                        "status_code"
                )
        );
    }
}
