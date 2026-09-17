package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.CommandPulseSnapshot;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.CommandPulseMetrics;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class IncidentCommandPulseRepository {

    private final JdbcClient jdbcClient;

    public IncidentCommandPulseRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public CommandPulseMetrics loadMetrics(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT

                            (
                                SELECT COUNT(*)
                                FROM incident_action_items action
                                WHERE action.incident_id = :incidentId
                                  AND action.status <> 'DONE'
                            )::INTEGER
                                AS open_action_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_action_items action
                                WHERE action.incident_id = :incidentId
                                  AND action.status <> 'DONE'
                                  AND action.due_at IS NOT NULL
                                  AND action.due_at < NOW()
                            )::INTEGER
                                AS overdue_action_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_action_items action
                                WHERE action.incident_id = :incidentId
                                  AND action.status = 'BLOCKED'
                            )::INTEGER
                                AS blocked_action_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_resource_requests resource
                                WHERE resource.incident_id = :incidentId
                                  AND resource.status IN (
                                      'REQUESTED',
                                      'PARTIALLY_ALLOCATED'
                                  )
                                  AND resource.allocated_quantity
                                      < resource.requested_quantity
                            )::INTEGER
                                AS resource_shortage_count,

                            COALESCE(
                                (
                                    SELECT SUM(
                                        zone.population_at_risk
                                        - zone.evacuated_population
                                    )
                                    FROM incident_evacuation_zones zone
                                    WHERE zone.incident_id = :incidentId
                                      AND zone.status NOT IN (
                                          'CLEARED',
                                          'CANCELLED'
                                      )
                                ),
                                0
                            )::BIGINT
                                AS evacuation_remaining,

                            COALESCE(
                                (
                                    SELECT SUM(
                                        shelter.capacity
                                        - shelter.occupancy
                                    )
                                    FROM incident_shelters shelter
                                    WHERE shelter.incident_id = :incidentId
                                      AND shelter.status <> 'CLOSED'
                                ),
                                0
                            )::BIGINT
                                AS shelter_remaining_capacity,

                            (
                                SELECT COUNT(*)
                                FROM incident_lifeline_assets lifeline
                                WHERE lifeline.incident_id = :incidentId
                                  AND lifeline.status IN (
                                      'DEGRADED',
                                      'OUTAGE'
                                  )
                            )::INTEGER
                                AS lifeline_disrupted_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_lifeline_assets lifeline
                                WHERE lifeline.incident_id = :incidentId
                                  AND lifeline.status IN (
                                      'DEGRADED',
                                      'OUTAGE'
                                  )
                                  AND lifeline.priority = 'CRITICAL'
                            )::INTEGER
                                AS critical_lifeline_disruption_count,

                            COALESCE(
                                (
                                    SELECT SUM(
                                        lifeline.affected_population
                                    )
                                    FROM incident_lifeline_assets lifeline
                                    WHERE lifeline.incident_id = :incidentId
                                      AND lifeline.status IN (
                                          'DEGRADED',
                                          'OUTAGE'
                                      )
                                ),
                                0
                            )::BIGINT
                                AS lifeline_affected_population,

                            COALESCE(
                                (
                                    SELECT SUM(
                                        damage.estimated_loss_musd
                                    )
                                    FROM incident_damage_assessments damage
                                    WHERE damage.incident_id = :incidentId
                                      AND damage.status <> 'RESTORED'
                                ),
                                0
                            )::DOUBLE PRECISION
                                AS unresolved_damage_loss_musd,

                            (
                                SELECT COUNT(*)
                                FROM incident_damage_assessments damage
                                WHERE damage.incident_id = :incidentId
                                  AND damage.status <> 'RESTORED'
                                  AND damage.severity IN (
                                      'MAJOR',
                                      'DESTROYED'
                                  )
                            )::INTEGER
                                AS critical_damage_count,

                            COALESCE(
                                (
                                    SELECT AVG(
                                        damage.repair_progress_pct
                                    )
                                    FROM incident_damage_assessments damage
                                    WHERE damage.incident_id = :incidentId
                                ),
                                0
                            )::DOUBLE PRECISION
                                AS average_repair_progress_pct
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) ->
                                new CommandPulseMetrics(
                                        resultSet.getInt(
                                                "open_action_count"
                                        ),
                                        resultSet.getInt(
                                                "overdue_action_count"
                                        ),
                                        resultSet.getInt(
                                                "blocked_action_count"
                                        ),
                                        resultSet.getInt(
                                                "resource_shortage_count"
                                        ),
                                        resultSet.getLong(
                                                "evacuation_remaining"
                                        ),
                                        resultSet.getLong(
                                                "shelter_remaining_capacity"
                                        ),
                                        resultSet.getInt(
                                                "lifeline_disrupted_count"
                                        ),
                                        resultSet.getInt(
                                                "critical_lifeline_disruption_count"
                                        ),
                                        resultSet.getLong(
                                                "lifeline_affected_population"
                                        ),
                                        resultSet.getDouble(
                                                "unresolved_damage_loss_musd"
                                        ),
                                        resultSet.getInt(
                                                "critical_damage_count"
                                        ),
                                        resultSet.getDouble(
                                                "average_repair_progress_pct"
                                        )
                                )
                )
                .single();
    }

    public CommandPulseSnapshot saveSnapshot(
            UUID id,
            IncidentCommandPulseResponse pulse
    ) {
        UUID snapshotId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_command_snapshots (
                                    id,
                                    incident_id,
                                    score,
                                    band,
                                    blocker_count,
                                    open_action_count,
                                    overdue_action_count,
                                    blocked_action_count,
                                    resource_shortage_count,
                                    evacuation_remaining,
                                    shelter_remaining_capacity,
                                    lifeline_disrupted_count,
                                    critical_lifeline_disruption_count,
                                    lifeline_affected_population,
                                    unresolved_damage_loss_musd,
                                    critical_damage_count,
                                    average_repair_progress_pct
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :score,
                                    :band,
                                    :blockerCount,
                                    :openActionCount,
                                    :overdueActionCount,
                                    :blockedActionCount,
                                    :resourceShortageCount,
                                    :evacuationRemaining,
                                    :shelterRemainingCapacity,
                                    :lifelineDisruptedCount,
                                    :criticalLifelineDisruptionCount,
                                    :lifelineAffectedPopulation,
                                    :unresolvedDamageLossMusd,
                                    :criticalDamageCount,
                                    :averageRepairProgressPct
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "incidentId",
                                pulse.incidentId()
                        )
                        .param(
                                "score",
                                pulse.score()
                        )
                        .param(
                                "band",
                                pulse.band().name()
                        )
                        .param(
                                "blockerCount",
                                pulse.blockers().size()
                        )
                        .param(
                                "openActionCount",
                                pulse.openActionCount()
                        )
                        .param(
                                "overdueActionCount",
                                pulse.overdueActionCount()
                        )
                        .param(
                                "blockedActionCount",
                                pulse.blockedActionCount()
                        )
                        .param(
                                "resourceShortageCount",
                                pulse.resourceShortageCount()
                        )
                        .param(
                                "evacuationRemaining",
                                pulse.evacuationRemaining()
                        )
                        .param(
                                "shelterRemainingCapacity",
                                pulse.shelterRemainingCapacity()
                        )
                        .param(
                                "lifelineDisruptedCount",
                                pulse.lifelineDisruptedCount()
                        )
                        .param(
                                "criticalLifelineDisruptionCount",
                                pulse.criticalLifelineDisruptionCount()
                        )
                        .param(
                                "lifelineAffectedPopulation",
                                pulse.lifelineAffectedPopulation()
                        )
                        .param(
                                "unresolvedDamageLossMusd",
                                pulse.unresolvedDamageLossMusd()
                        )
                        .param(
                                "criticalDamageCount",
                                pulse.criticalDamageCount()
                        )
                        .param(
                                "averageRepairProgressPct",
                                pulse.averageRepairProgressPct()
                        )
                        .query(
                                UUID.class
                        )
                        .single();

        return findSnapshot(
                snapshotId
        );
    }

    public List<CommandPulseSnapshot> findHistory(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            incident_id,
                            score,
                            band,
                            blocker_count,
                            open_action_count,
                            overdue_action_count,
                            blocked_action_count,
                            resource_shortage_count,
                            evacuation_remaining,
                            shelter_remaining_capacity,
                            lifeline_disrupted_count,
                            critical_lifeline_disruption_count,
                            lifeline_affected_population,
                            unresolved_damage_loss_musd,
                            critical_damage_count,
                            average_repair_progress_pct,
                            created_at
                        FROM incident_command_snapshots
                        WHERE incident_id = :incidentId
                        ORDER BY created_at DESC,
                                 id DESC
                        LIMIT 20
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapSnapshot
                )
                .list();
    }

    private CommandPulseSnapshot findSnapshot(
            UUID snapshotId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            incident_id,
                            score,
                            band,
                            blocker_count,
                            open_action_count,
                            overdue_action_count,
                            blocked_action_count,
                            resource_shortage_count,
                            evacuation_remaining,
                            shelter_remaining_capacity,
                            lifeline_disrupted_count,
                            critical_lifeline_disruption_count,
                            lifeline_affected_population,
                            unresolved_damage_loss_musd,
                            critical_damage_count,
                            average_repair_progress_pct,
                            created_at
                        FROM incident_command_snapshots
                        WHERE id = :snapshotId
                        """)
                .param(
                        "snapshotId",
                        snapshotId
                )
                .query(
                        this::mapSnapshot
                )
                .single();
    }

    private CommandPulseSnapshot mapSnapshot(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new CommandPulseSnapshot(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),
                resultSet.getInt(
                        "score"
                ),
                CommandPressureBand.valueOf(
                        resultSet.getString(
                                "band"
                        )
                ),
                resultSet.getInt(
                        "blocker_count"
                ),
                resultSet.getInt(
                        "open_action_count"
                ),
                resultSet.getInt(
                        "overdue_action_count"
                ),
                resultSet.getInt(
                        "blocked_action_count"
                ),
                resultSet.getInt(
                        "resource_shortage_count"
                ),
                resultSet.getLong(
                        "evacuation_remaining"
                ),
                resultSet.getLong(
                        "shelter_remaining_capacity"
                ),
                resultSet.getInt(
                        "lifeline_disrupted_count"
                ),
                resultSet.getInt(
                        "critical_lifeline_disruption_count"
                ),
                resultSet.getLong(
                        "lifeline_affected_population"
                ),
                resultSet.getDouble(
                        "unresolved_damage_loss_musd"
                ),
                resultSet.getInt(
                        "critical_damage_count"
                ),
                resultSet.getDouble(
                        "average_repair_progress_pct"
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
