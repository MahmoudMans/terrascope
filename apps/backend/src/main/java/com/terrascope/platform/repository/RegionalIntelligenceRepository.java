package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.RegionalAnalysisRequest;
import com.terrascope.platform.api.model.RegionalAnalysisResult;
import com.terrascope.platform.api.model.RegionalHazardBreakdown;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import com.terrascope.platform.api.model.RegionalNearestEvent;
import com.terrascope.platform.domain.RegionalActivityBand;
import com.terrascope.platform.service.RegionalIntelligenceEngine;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class RegionalIntelligenceRepository {

    private final JdbcClient jdbcClient;

    public RegionalIntelligenceRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public RegionalAnalysisResult save(
            UUID id,
            RegionalAnalysisRequest request,
            RegionalIntelligenceEngine.Calculation calculation
    ) {
        RegionalHazardPoint nearest =
                calculation.nearestEvent();

        return jdbcClient
                .sql("""
                        INSERT INTO regional_analysis_runs (
                            id,
                            region_name,
                            center_latitude,
                            center_longitude,
                            radius_km,
                            source_point_count,
                            matched_event_count,
                            earthquake_count,
                            volcano_count,
                            wildfire_count,
                            severe_storm_count,
                            nearest_event_id,
                            nearest_event_type,
                            nearest_event_title,
                            nearest_event_distance_km,
                            average_distance_km,
                            activity_index,
                            activity_band
                        )
                        VALUES (
                            :id,
                            :regionName,
                            :latitude,
                            :longitude,
                            :radiusKm,
                            :sourcePointCount,
                            :matchedEventCount,
                            :earthquakeCount,
                            :volcanoCount,
                            :wildfireCount,
                            :severeStormCount,
                            :nearestEventId,
                            :nearestEventType,
                            :nearestEventTitle,
                            :nearestEventDistanceKm,
                            :averageDistanceKm,
                            :activityIndex,
                            :activityBand
                        )
                        RETURNING
                            id,
                            region_name,
                            center_latitude,
                            center_longitude,
                            radius_km,
                            source_point_count,
                            matched_event_count,
                            earthquake_count,
                            volcano_count,
                            wildfire_count,
                            severe_storm_count,
                            nearest_event_id,
                            nearest_event_type,
                            nearest_event_title,
                            nearest_event_distance_km,
                            average_distance_km,
                            activity_index,
                            activity_band,
                            analyzed_at
                        """)
                .param(
                        "id",
                        id
                )
                .param(
                        "regionName",
                        request
                                .regionName()
                                .trim()
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
                        "sourcePointCount",
                        calculation.sourcePointCount()
                )
                .param(
                        "matchedEventCount",
                        calculation.matchedEventCount()
                )
                .param(
                        "earthquakeCount",
                        calculation
                                .breakdown()
                                .earthquakeCount()
                )
                .param(
                        "volcanoCount",
                        calculation
                                .breakdown()
                                .volcanoCount()
                )
                .param(
                        "wildfireCount",
                        calculation
                                .breakdown()
                                .wildfireCount()
                )
                .param(
                        "severeStormCount",
                        calculation
                                .breakdown()
                                .severeStormCount()
                )
                .param(
                        "nearestEventId",
                        nearest == null
                                ? null
                                : nearest.id(),
                        Types.VARCHAR
                )
                .param(
                        "nearestEventType",
                        nearest == null
                                ? null
                                : nearest.hazardType(),
                        Types.VARCHAR
                )
                .param(
                        "nearestEventTitle",
                        nearest == null
                                ? null
                                : nearest.title(),
                        Types.VARCHAR
                )
                .param(
                        "nearestEventDistanceKm",
                        calculation.nearestEventDistanceKm(),
                        Types.DOUBLE
                )
                .param(
                        "averageDistanceKm",
                        calculation.averageDistanceKm()
                )
                .param(
                        "activityIndex",
                        calculation.activityIndex()
                )
                .param(
                        "activityBand",
                        calculation
                                .activityBand()
                                .name()
                )
                .query(
                        this::mapResult
                )
                .single();
    }

    public List<RegionalAnalysisResult> history() {
        return jdbcClient
                .sql("""
                        SELECT
                            id,
                            region_name,
                            center_latitude,
                            center_longitude,
                            radius_km,
                            source_point_count,
                            matched_event_count,
                            earthquake_count,
                            volcano_count,
                            wildfire_count,
                            severe_storm_count,
                            nearest_event_id,
                            nearest_event_type,
                            nearest_event_title,
                            nearest_event_distance_km,
                            average_distance_km,
                            activity_index,
                            activity_band,
                            analyzed_at
                        FROM regional_analysis_runs
                        ORDER BY
                            analyzed_at DESC,
                            id DESC
                        LIMIT 100
                        """)
                .query(
                        this::mapResult
                )
                .list();
    }

    private RegionalAnalysisResult mapResult(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        String nearestEventId =
                resultSet.getString(
                        "nearest_event_id"
                );

        RegionalNearestEvent nearestEvent =
                null;

        if (nearestEventId != null) {
            nearestEvent =
                    new RegionalNearestEvent(
                            nearestEventId,
                            resultSet.getString(
                                    "nearest_event_type"
                            ),
                            resultSet.getString(
                                    "nearest_event_title"
                            ),
                            resultSet.getDouble(
                                    "nearest_event_distance_km"
                            )
                    );
        }

        RegionalHazardBreakdown breakdown =
                new RegionalHazardBreakdown(
                        resultSet.getInt(
                                "earthquake_count"
                        ),
                        resultSet.getInt(
                                "volcano_count"
                        ),
                        resultSet.getInt(
                                "wildfire_count"
                        ),
                        resultSet.getInt(
                                "severe_storm_count"
                        )
                );

        return new RegionalAnalysisResult(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getString(
                        "region_name"
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
                resultSet.getInt(
                        "source_point_count"
                ),
                resultSet.getInt(
                        "matched_event_count"
                ),
                breakdown,
                nearestEvent,
                resultSet.getDouble(
                        "average_distance_km"
                ),
                resultSet.getDouble(
                        "activity_index"
                ),
                RegionalActivityBand.valueOf(
                        resultSet.getString(
                                "activity_band"
                        )
                ),
                resultSet
                        .getObject(
                                "analyzed_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }
}
