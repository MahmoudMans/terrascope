package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.IncidentAfterActionReview;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentClosureReadinessResponse;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.domain.ClosureReadinessStatus;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.IncidentClosureReviewMetrics;
import com.terrascope.platform.domain.IncidentSeverity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class IncidentClosureReviewRepository {

    private static final String SELECT_COLUMNS = """
            id,
            incident_id,
            review_number,
            incident_title,
            hazard_type,
            incident_severity,
            opened_at,
            closed_at,
            duration_minutes,
            closure_status,
            closure_ready,
            final_command_score,
            final_command_band,
            peak_command_score,
            sitrep_count,
            timeline_event_count,
            action_count,
            resource_request_count,
            evacuation_zone_count,
            shelter_count,
            lifeline_asset_count,
            damage_assessment_count,
            unresolved_item_count,
            executive_summary,
            created_at
            """;

    private final JdbcClient jdbcClient;

    public IncidentClosureReviewRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public IncidentClosureReviewMetrics loadMetrics(
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

                            (
                                SELECT COUNT(*)
                                FROM incident_evacuation_zones zone
                                WHERE zone.incident_id = :incidentId
                                  AND zone.status IN (
                                      'MONITORING',
                                      'EVACUATING'
                                  )
                            )::INTEGER
                                AS active_evacuation_zone_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_shelters shelter
                                WHERE shelter.incident_id = :incidentId
                                  AND shelter.status <> 'CLOSED'
                            )::INTEGER
                                AS active_shelter_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_lifeline_assets lifeline
                                WHERE lifeline.incident_id = :incidentId
                                  AND lifeline.status IN (
                                      'DEGRADED',
                                      'OUTAGE'
                                  )
                            )::INTEGER
                                AS disrupted_lifeline_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_damage_assessments damage
                                WHERE damage.incident_id = :incidentId
                                  AND damage.status <> 'RESTORED'
                            )::INTEGER
                                AS unresolved_damage_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_situation_reports report
                                WHERE report.incident_id = :incidentId
                            )::INTEGER
                                AS sitrep_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_timeline_events timeline
                                WHERE timeline.incident_id = :incidentId
                            )::INTEGER
                                AS timeline_event_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_action_items action
                                WHERE action.incident_id = :incidentId
                            )::INTEGER
                                AS action_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_resource_requests resource
                                WHERE resource.incident_id = :incidentId
                            )::INTEGER
                                AS resource_request_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_evacuation_zones zone
                                WHERE zone.incident_id = :incidentId
                            )::INTEGER
                                AS evacuation_zone_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_shelters shelter
                                WHERE shelter.incident_id = :incidentId
                            )::INTEGER
                                AS shelter_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_lifeline_assets lifeline
                                WHERE lifeline.incident_id = :incidentId
                            )::INTEGER
                                AS lifeline_asset_count,

                            (
                                SELECT COUNT(*)
                                FROM incident_damage_assessments damage
                                WHERE damage.incident_id = :incidentId
                            )::INTEGER
                                AS damage_assessment_count,

                            COALESCE(
                                (
                                    SELECT MAX(
                                        snapshot.score
                                    )
                                    FROM incident_command_snapshots snapshot
                                    WHERE snapshot.incident_id = :incidentId
                                ),
                                0
                            )::INTEGER
                                AS peak_command_score
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
                                new IncidentClosureReviewMetrics(
                                        resultSet.getInt(
                                                "open_action_count"
                                        ),
                                        resultSet.getInt(
                                                "resource_shortage_count"
                                        ),
                                        resultSet.getInt(
                                                "active_evacuation_zone_count"
                                        ),
                                        resultSet.getInt(
                                                "active_shelter_count"
                                        ),
                                        resultSet.getInt(
                                                "disrupted_lifeline_count"
                                        ),
                                        resultSet.getInt(
                                                "unresolved_damage_count"
                                        ),

                                        resultSet.getInt(
                                                "sitrep_count"
                                        ),
                                        resultSet.getInt(
                                                "timeline_event_count"
                                        ),

                                        resultSet.getInt(
                                                "action_count"
                                        ),
                                        resultSet.getInt(
                                                "resource_request_count"
                                        ),
                                        resultSet.getInt(
                                                "evacuation_zone_count"
                                        ),
                                        resultSet.getInt(
                                                "shelter_count"
                                        ),
                                        resultSet.getInt(
                                                "lifeline_asset_count"
                                        ),
                                        resultSet.getInt(
                                                "damage_assessment_count"
                                        ),

                                        resultSet.getInt(
                                                "peak_command_score"
                                        )
                                )
                )
                .single();
    }

    public void lockIncident(
            UUID incidentId
    ) {
        jdbcClient
                .sql("""
                        SELECT id
                        FROM incident_cases
                        WHERE id = :incidentId
                        FOR UPDATE
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        UUID.class
                )
                .single();
    }

    public int nextReviewNumber(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                            COALESCE(
                                MAX(review_number),
                                0
                            ) + 1
                        FROM incident_after_action_reviews
                        WHERE incident_id = :incidentId
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        Integer.class
                )
                .single();
    }

    public IncidentAfterActionReview saveReview(
            UUID id,
            int reviewNumber,
            IncidentCase incident,
            IncidentClosureReadinessResponse readiness,
            IncidentCommandPulseResponse pulse,
            IncidentClosureReviewMetrics metrics,
            int peakCommandScore,
            long durationMinutes,
            String executiveSummary
    ) {
        UUID reviewId =
                jdbcClient
                        .sql("""
                                INSERT INTO incident_after_action_reviews (
                                    id,
                                    incident_id,
                                    review_number,
                                    incident_title,
                                    hazard_type,
                                    incident_severity,
                                    opened_at,
                                    closed_at,
                                    duration_minutes,
                                    closure_status,
                                    closure_ready,
                                    final_command_score,
                                    final_command_band,
                                    peak_command_score,
                                    sitrep_count,
                                    timeline_event_count,
                                    action_count,
                                    resource_request_count,
                                    evacuation_zone_count,
                                    shelter_count,
                                    lifeline_asset_count,
                                    damage_assessment_count,
                                    unresolved_item_count,
                                    executive_summary
                                )
                                VALUES (
                                    :id,
                                    :incidentId,
                                    :reviewNumber,
                                    :incidentTitle,
                                    :hazardType,
                                    :incidentSeverity,
                                    :openedAt,
                                    :closedAt,
                                    :durationMinutes,
                                    :closureStatus,
                                    :closureReady,
                                    :finalCommandScore,
                                    :finalCommandBand,
                                    :peakCommandScore,
                                    :sitrepCount,
                                    :timelineEventCount,
                                    :actionCount,
                                    :resourceRequestCount,
                                    :evacuationZoneCount,
                                    :shelterCount,
                                    :lifelineAssetCount,
                                    :damageAssessmentCount,
                                    :unresolvedItemCount,
                                    :executiveSummary
                                )
                                RETURNING id
                                """)
                        .param(
                                "id",
                                id
                        )
                        .param(
                                "incidentId",
                                incident.id()
                        )
                        .param(
                                "reviewNumber",
                                reviewNumber
                        )
                        .param(
                                "incidentTitle",
                                incident.title()
                        )
                        .param(
                                "hazardType",
                                incident.hazardType()
                        )
                        .param(
                                "incidentSeverity",
                                incident.severity().name()
                        )
                        .param(
                                "openedAt",
                                OffsetDateTime.ofInstant(
                                        incident.openedAt(),
                                        java.time.ZoneOffset.UTC
                                )
                        )
                        .param(
                                "closedAt",
                                OffsetDateTime.ofInstant(
                                        incident.closedAt(),
                                        java.time.ZoneOffset.UTC
                                )
                        )
                        .param(
                                "durationMinutes",
                                durationMinutes
                        )
                        .param(
                                "closureStatus",
                                readiness.status().name()
                        )
                        .param(
                                "closureReady",
                                readiness.ready()
                        )
                        .param(
                                "finalCommandScore",
                                pulse.score()
                        )
                        .param(
                                "finalCommandBand",
                                pulse.band().name()
                        )
                        .param(
                                "peakCommandScore",
                                peakCommandScore
                        )
                        .param(
                                "sitrepCount",
                                metrics.sitrepCount()
                        )
                        .param(
                                "timelineEventCount",
                                metrics.timelineEventCount()
                        )
                        .param(
                                "actionCount",
                                metrics.actionCount()
                        )
                        .param(
                                "resourceRequestCount",
                                metrics.resourceRequestCount()
                        )
                        .param(
                                "evacuationZoneCount",
                                metrics.evacuationZoneCount()
                        )
                        .param(
                                "shelterCount",
                                metrics.shelterCount()
                        )
                        .param(
                                "lifelineAssetCount",
                                metrics.lifelineAssetCount()
                        )
                        .param(
                                "damageAssessmentCount",
                                metrics.damageAssessmentCount()
                        )
                        .param(
                                "unresolvedItemCount",
                                readiness.unresolvedItemCount()
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
                reviewId
        );
    }

    public List<IncidentAfterActionReview> findHistory(
            UUID incidentId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_after_action_reviews
                        WHERE incident_id = :incidentId
                        ORDER BY
                            review_number DESC,
                            created_at DESC
                        LIMIT 20
                        """)
                .param(
                        "incidentId",
                        incidentId
                )
                .query(
                        this::mapReview
                )
                .list();
    }

    private IncidentAfterActionReview findById(
            UUID reviewId
    ) {
        return jdbcClient
                .sql("""
                        SELECT
                        """
                        + SELECT_COLUMNS
                        + """
                        FROM incident_after_action_reviews
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

    private IncidentAfterActionReview mapReview(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new IncidentAfterActionReview(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "incident_id",
                        UUID.class
                ),
                resultSet.getInt(
                        "review_number"
                ),

                resultSet.getString(
                        "incident_title"
                ),
                resultSet.getString(
                        "hazard_type"
                ),
                IncidentSeverity.valueOf(
                        resultSet.getString(
                                "incident_severity"
                        )
                ),

                resultSet
                        .getObject(
                                "opened_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),

                resultSet
                        .getObject(
                                "closed_at",
                                OffsetDateTime.class
                        )
                        .toInstant(),

                resultSet.getLong(
                        "duration_minutes"
                ),

                ClosureReadinessStatus.valueOf(
                        resultSet.getString(
                                "closure_status"
                        )
                ),

                resultSet.getBoolean(
                        "closure_ready"
                ),

                resultSet.getInt(
                        "final_command_score"
                ),

                CommandPressureBand.valueOf(
                        resultSet.getString(
                                "final_command_band"
                        )
                ),

                resultSet.getInt(
                        "peak_command_score"
                ),

                resultSet.getInt(
                        "sitrep_count"
                ),

                resultSet.getInt(
                        "timeline_event_count"
                ),

                resultSet.getInt(
                        "action_count"
                ),

                resultSet.getInt(
                        "resource_request_count"
                ),

                resultSet.getInt(
                        "evacuation_zone_count"
                ),

                resultSet.getInt(
                        "shelter_count"
                ),

                resultSet.getInt(
                        "lifeline_asset_count"
                ),

                resultSet.getInt(
                        "damage_assessment_count"
                ),

                resultSet.getInt(
                        "unresolved_item_count"
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
