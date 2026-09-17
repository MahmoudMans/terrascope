package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.AlertEvent;
import com.terrascope.platform.api.model.AlertRule;
import com.terrascope.platform.api.model.CreateAlertRuleRequest;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import com.terrascope.platform.domain.AlertEventStatus;
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
public class HazardAlertRepository {

    private final JdbcClient jdbcClient;

    public HazardAlertRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public List<AlertRule> findRules() {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            name,
                            hazard_type,
                            center_latitude,
                            center_longitude,
                            radius_km,
                            minimum_intensity,
                            enabled,
                            created_at,
                            updated_at
                        FROM hazard_alert_rules
                        ORDER BY
                            enabled DESC,
                            created_at DESC,
                            id DESC
                        """)
                .query(
                        this::mapRule
                )
                .list();
    }

    public List<AlertEvent> findEvents() {
        return jdbcClient
                .sql("""
                        SELECT
                            event.id,
                            event.rule_id,
                            rule.name AS rule_name,
                            event.source_event_id,
                            event.hazard_type,
                            event.title,
                            event.event_latitude,
                            event.event_longitude,
                            event.intensity,
                            event.observed_at,
                            event.distance_km,
                            event.status,
                            event.triggered_at,
                            event.updated_at
                        FROM hazard_alert_events event
                        JOIN hazard_alert_rules rule
                          ON rule.id = event.rule_id
                        WHERE event.status <> 'DISMISSED'
                        ORDER BY
                            CASE event.status
                                WHEN 'NEW' THEN 0
                                WHEN 'ACKNOWLEDGED' THEN 1
                                ELSE 2
                            END,
                            event.triggered_at DESC,
                            event.id DESC
                        LIMIT 200
                        """)
                .query(
                        this::mapEvent
                )
                .list();
    }

    public AlertRule createRule(
            UUID id,
            CreateAlertRuleRequest request,
            String hazardType
    ) {
        return jdbcClient
                .sql("""
                        INSERT INTO hazard_alert_rules (
                            id,
                            name,
                            hazard_type,
                            center_latitude,
                            center_longitude,
                            radius_km,
                            minimum_intensity
                        )
                        VALUES (
                            :id,
                            :name,
                            :hazardType,
                            :latitude,
                            :longitude,
                            :radiusKm,
                            :minimumIntensity
                        )
                        RETURNING
                            id,
                            name,
                            hazard_type,
                            center_latitude,
                            center_longitude,
                            radius_km,
                            minimum_intensity,
                            enabled,
                            created_at,
                            updated_at
                        """)
                .param(
                        "id",
                        id
                )
                .param(
                        "name",
                        request.name().trim()
                )
                .param(
                        "hazardType",
                        hazardType
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
                        "radiusKm",
                        request.radiusKm()
                )
                .param(
                        "minimumIntensity",
                        request.minimumIntensity()
                )
                .query(
                        this::mapRule
                )
                .single();
    }

    public Optional<AlertRule> setRuleEnabled(
            UUID id,
            boolean enabled
    ) {
        return jdbcClient
                .sql("""
                        UPDATE hazard_alert_rules
                        SET
                            enabled = :enabled,
                            updated_at = NOW()
                        WHERE id = :id
                        RETURNING
                            id,
                            name,
                            hazard_type,
                            center_latitude,
                            center_longitude,
                            radius_km,
                            minimum_intensity,
                            enabled,
                            created_at,
                            updated_at
                        """)
                .param(
                        "enabled",
                        enabled
                )
                .param(
                        "id",
                        id
                )
                .query(
                        this::mapRule
                )
                .optional();
    }

    public int deleteRule(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM hazard_alert_rules
                        WHERE id = :id
                        """)
                .param(
                        "id",
                        id
                )
                .update();
    }

    public Optional<AlertEvent> insertAlert(
            UUID id,
            AlertRule rule,
            RegionalHazardPoint point,
            double distanceKm
    ) {
        OffsetDateTime observedAt =
                point
                        .observedAt()
                        .atOffset(
                                ZoneOffset.UTC
                        );

        return jdbcClient
                .sql("""
                        INSERT INTO hazard_alert_events (
                            id,
                            rule_id,
                            source_event_id,
                            hazard_type,
                            title,
                            event_latitude,
                            event_longitude,
                            intensity,
                            observed_at,
                            distance_km
                        )
                        VALUES (
                            :id,
                            :ruleId,
                            :sourceEventId,
                            :hazardType,
                            :title,
                            :latitude,
                            :longitude,
                            :intensity,
                            :observedAt,
                            :distanceKm
                        )
                        ON CONFLICT (
                            rule_id,
                            hazard_type,
                            source_event_id
                        )
                        DO NOTHING
                        RETURNING
                            id
                        """)
                .param(
                        "id",
                        id
                )
                .param(
                        "ruleId",
                        rule.id()
                )
                .param(
                        "sourceEventId",
                        point.id().trim()
                )
                .param(
                        "hazardType",
                        point.hazardType()
                )
                .param(
                        "title",
                        point.title().trim()
                )
                .param(
                        "latitude",
                        point.latitude()
                )
                .param(
                        "longitude",
                        point.longitude()
                )
                .param(
                        "intensity",
                        point.intensity(),
                        Types.DOUBLE
                )
                .param(
                        "observedAt",
                        observedAt
                )
                .param(
                        "distanceKm",
                        distanceKm
                )
                .query(
                        UUID.class
                )
                .optional()
                .flatMap(
                        insertedId ->
                                findEventById(
                                        insertedId
                                )
                );
    }

    public Optional<AlertEvent> updateEventStatus(
            UUID id,
            AlertEventStatus status
    ) {
        return jdbcClient
                .sql("""
                        UPDATE hazard_alert_events
                        SET
                            status = :status,
                            updated_at = NOW()
                        WHERE id = :id
                        RETURNING
                            id
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
                .flatMap(
                        this::findEventById
                );
    }

    private Optional<AlertEvent> findEventById(
            UUID id
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            event.id,
                            event.rule_id,
                            rule.name AS rule_name,
                            event.source_event_id,
                            event.hazard_type,
                            event.title,
                            event.event_latitude,
                            event.event_longitude,
                            event.intensity,
                            event.observed_at,
                            event.distance_km,
                            event.status,
                            event.triggered_at,
                            event.updated_at
                        FROM hazard_alert_events event
                        JOIN hazard_alert_rules rule
                          ON rule.id = event.rule_id
                        WHERE event.id = :id
                        """)
                .param(
                        "id",
                        id
                )
                .query(
                        this::mapEvent
                )
                .optional();
    }

    private AlertRule mapRule(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new AlertRule(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getString(
                        "name"
                ),
                resultSet.getString(
                        "hazard_type"
                ),
                resultSet.getDouble(
                        "center_latitude"
                ),
                resultSet.getDouble(
                        "center_longitude"
                ),
                resultSet.getDouble(
                        "radius_km"
                ),
                resultSet.getDouble(
                        "minimum_intensity"
                ),
                resultSet.getBoolean(
                        "enabled"
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
                        .toInstant()
        );
    }

    private AlertEvent mapEvent(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        double rawIntensity =
                resultSet.getDouble(
                        "intensity"
                );

        Double intensity =
                resultSet.wasNull()
                        ? null
                        : rawIntensity;

        return new AlertEvent(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "rule_id",
                        UUID.class
                ),
                resultSet.getString(
                        "rule_name"
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
                resultSet.getDouble(
                        "event_latitude"
                ),
                resultSet.getDouble(
                        "event_longitude"
                ),
                intensity,
                resultSet
                        .getObject(
                                "observed_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),
                resultSet.getDouble(
                        "distance_km"
                ),
                AlertEventStatus.valueOf(
                        resultSet.getString(
                                "status"
                        )
                ),
                resultSet
                        .getObject(
                                "triggered_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),
                resultSet
                        .getObject(
                                "updated_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }
}
