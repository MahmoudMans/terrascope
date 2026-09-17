package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AlertRule;
import com.terrascope.platform.api.model.RegionalHazardPoint;
import com.terrascope.platform.domain.HazardType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HazardAlertEngine {

    private final RegionalIntelligenceEngine spatialEngine;

    public HazardAlertEngine(
            RegionalIntelligenceEngine spatialEngine
    ) {
        this.spatialEngine = spatialEngine;
    }

    public Optional<Match> evaluate(
            AlertRule rule,
            RegionalHazardPoint point
    ) {
        if (!rule.enabled()) {
            return Optional.empty();
        }

        HazardType pointHazard =
                HazardType.fromKey(
                        point.hazardType()
                );

        if (
                !"all".equals(
                        rule.hazardType()
                )
                && !rule
                .hazardType()
                .equals(
                        pointHazard.key()
                )
        ) {
            return Optional.empty();
        }

        double distanceKm =
                spatialEngine.haversineKm(
                        rule.latitude(),
                        rule.longitude(),
                        point.latitude(),
                        point.longitude()
                );

        if (
                distanceKm
                > rule.radiusKm()
        ) {
            return Optional.empty();
        }

        if (
                rule.minimumIntensity()
                > 0.0
        ) {
            if (
                    point.intensity()
                    == null
            ) {
                return Optional.empty();
            }

            if (
                    point.intensity()
                    < rule.minimumIntensity()
            ) {
                return Optional.empty();
            }
        }

        return Optional.of(
                new Match(
                        round2(
                                distanceKm
                        )
                )
        );
    }

    private double round2(
            double value
    ) {
        return Math.round(
                value * 100.0
        )
                / 100.0;
    }

    public record Match(
            double distanceKm
    ) {
    }
}
