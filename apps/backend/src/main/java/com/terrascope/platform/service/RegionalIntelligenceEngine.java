package com.terrascope.platform.service;

import com.terrascope.platform.api.model.RegionalAnalysisRequest;
import com.terrascope.platform.api.model.RegionalHazardBreakdown;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.domain.RegionalActivityBand;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RegionalIntelligenceEngine {

    private static final double EARTH_RADIUS_KM =
            6371.0088;

    public Calculation calculate(
            RegionalAnalysisRequest request
    ) {
        List<MatchedPoint> matched =
                new ArrayList<>();

        for (
                RegionalHazardPoint point
                : request.points()
        ) {
            if (point == null) {
                continue;
            }

            HazardType hazardType =
                    HazardType.fromKey(
                            point.hazardType()
                    );

            double distanceKm =
                    haversineKm(
                            request.latitude(),
                            request.longitude(),
                            point.latitude(),
                            point.longitude()
                    );

            if (
                    distanceKm
                    > request.radiusKm()
            ) {
                continue;
            }

            matched.add(
                    new MatchedPoint(
                            point,
                            hazardType,
                            distanceKm,
                            normalizedIntensity(
                                    hazardType,
                                    point.intensity()
                            )
                    )
            );
        }

        int earthquakeCount =
                count(
                        matched,
                        HazardType.EARTHQUAKE
                );

        int volcanoCount =
                count(
                        matched,
                        HazardType.VOLCANO
                );

        int wildfireCount =
                count(
                        matched,
                        HazardType.WILDFIRE
                );

        int severeStormCount =
                count(
                        matched,
                        HazardType.SEVERE_STORM
                );

        RegionalHazardBreakdown breakdown =
                new RegionalHazardBreakdown(
                        earthquakeCount,
                        volcanoCount,
                        wildfireCount,
                        severeStormCount
                );

        MatchedPoint nearest =
                matched
                        .stream()
                        .min(
                                Comparator.comparingDouble(
                                        MatchedPoint::distanceKm
                                )
                        )
                        .orElse(null);

        double averageDistanceKm =
                matched
                        .stream()
                        .mapToDouble(
                                MatchedPoint::distanceKm
                        )
                        .average()
                        .orElse(0.0);

        double pressure =
                0.0;

        Set<HazardType> hazardDiversity =
                new HashSet<>();

        for (MatchedPoint point : matched) {
            double proximity =
                    1.0
                            - clamp(
                            point.distanceKm()
                                    / request.radiusKm(),
                            0.0,
                            1.0
                    );

            double contribution =
                    point.normalizedIntensity()
                            * 0.58
                            + proximity
                            * 0.42;

            pressure +=
                    contribution;

            hazardDiversity.add(
                    point.hazardType()
            );
        }

        double densityPressure =
                matched.isEmpty()
                        ? 0.0
                        : (
                        1.0
                                - Math.exp(
                                -pressure
                                        / 3.25
                        )
                )
                        * 84.0;

        double diversityBonus =
                hazardDiversity.size()
                        * 4.0;

        double activityIndex =
                round2(
                        clamp(
                                densityPressure
                                        + diversityBonus,
                                0.0,
                                100.0
                        )
                );

        return new Calculation(
                request.points().size(),
                matched.size(),
                breakdown,
                nearest == null
                        ? null
                        : nearest.point(),
                nearest == null
                        ? null
                        : round2(
                        nearest.distanceKm()
                ),
                round2(
                        averageDistanceKm
                ),
                activityIndex,
                RegionalActivityBand.fromIndex(
                        activityIndex
                )
        );
    }

    public double haversineKm(
            double latitudeA,
            double longitudeA,
            double latitudeB,
            double longitudeB
    ) {
        double lat1 =
                Math.toRadians(
                        latitudeA
                );

        double lat2 =
                Math.toRadians(
                        latitudeB
                );

        double deltaLat =
                Math.toRadians(
                        latitudeB
                                - latitudeA
                );

        double deltaLon =
                Math.toRadians(
                        longitudeB
                                - longitudeA
                );

        double a =
                Math.sin(
                        deltaLat / 2.0
                )
                        * Math.sin(
                        deltaLat / 2.0
                )
                        + Math.cos(lat1)
                        * Math.cos(lat2)
                        * Math.sin(
                        deltaLon / 2.0
                )
                        * Math.sin(
                        deltaLon / 2.0
                );

        double c =
                2.0
                        * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(
                                1.0 - a
                        )
                );

        return EARTH_RADIUS_KM
                * c;
    }

    private int count(
            List<MatchedPoint> points,
            HazardType hazardType
    ) {
        return (int) points
                .stream()
                .filter(point ->
                        point.hazardType()
                                == hazardType
                )
                .count();
    }

    private double normalizedIntensity(
            HazardType hazardType,
            Double value
    ) {
        if (value == null) {
            return switch (hazardType) {
                case EARTHQUAKE -> 0.42;
                case VOLCANO -> 0.72;
                case WILDFIRE -> 0.56;
                case SEVERE_STORM -> 0.62;
            };
        }

        return switch (hazardType) {
            case EARTHQUAKE ->
                    clamp(
                            value / 8.0,
                            0.0,
                            1.0
                    );

            case VOLCANO ->
                    clamp(
                            value / 10.0,
                            0.0,
                            1.0
                    );

            case WILDFIRE ->
                    clamp(
                            value / 10.0,
                            0.0,
                            1.0
                    );

            case SEVERE_STORM -> {
                double denominator =
                        value <= 10.0
                                ? 10.0
                                : 160.0;

                yield clamp(
                        value / denominator,
                        0.0,
                        1.0
                );
            }
        };
    }

    private double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }

    private double round2(
            double value
    ) {
        return Math.round(
                value
                        * 100.0
        )
                / 100.0;
    }

    private record MatchedPoint(
            RegionalHazardPoint point,
            HazardType hazardType,
            double distanceKm,
            double normalizedIntensity
    ) {
    }

    public record Calculation(
            int sourcePointCount,
            int matchedEventCount,
            RegionalHazardBreakdown breakdown,
            RegionalHazardPoint nearestEvent,
            Double nearestEventDistanceKm,
            double averageDistanceKm,
            double activityIndex,
            RegionalActivityBand activityBand
    ) {
    }
}
