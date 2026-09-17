package com.terrascope.platform.service;

import com.terrascope.platform.api.model.EarthquakeEvent;
import com.terrascope.platform.api.model.EarthquakeFeedResponse;
import com.terrascope.platform.api.model.EarthquakeFilters;
import com.terrascope.platform.api.model.EarthquakeSummary;
import com.terrascope.platform.domain.EarthquakePeriod;
import com.terrascope.platform.domain.EarthquakeSort;
import com.terrascope.platform.infrastructure.usgs.DisasterSourceUnavailableException;
import com.terrascope.platform.infrastructure.usgs.UsgsEarthquakeClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class EarthquakeService {

    private final UsgsEarthquakeClient usgsEarthquakeClient;
    private final DemoEarthquakeFallback demoFallback;

    public EarthquakeService(
            UsgsEarthquakeClient usgsEarthquakeClient,
            DemoEarthquakeFallback demoFallback
    ) {
        this.usgsEarthquakeClient = usgsEarthquakeClient;
        this.demoFallback = demoFallback;
    }

    public EarthquakeFeedResponse query(
            EarthquakePeriod period,
            double minimumMagnitude,
            double maximumDepthKm,
            int limit,
            String search,
            EarthquakeSort sort
    ) {
        SourceResult sourceResult = fetchSource(
                period,
                minimumMagnitude
        );

        String normalizedSearch = search == null
                ? ""
                : search.trim().toLowerCase(Locale.ROOT);

        Comparator<EarthquakeEvent> comparator =
                comparatorFor(sort);

        List<EarthquakeEvent> matchingEvents =
                sourceResult.events()
                        .stream()
                        .filter(event ->
                                event.magnitude()
                                        >= minimumMagnitude
                        )
                        .filter(event ->
                                event.depthKm()
                                        <= maximumDepthKm
                        )
                        .filter(event ->
                                normalizedSearch.isBlank()
                                        || event.place()
                                        .toLowerCase(Locale.ROOT)
                                        .contains(normalizedSearch)
                        )
                        .sorted(comparator)
                        .toList();

        List<EarthquakeEvent> returnedEvents =
                matchingEvents
                        .stream()
                        .limit(limit)
                        .toList();

        EarthquakeSummary summary =
                summarize(matchingEvents);

        return new EarthquakeFeedResponse(
                sourceResult.source(),
                sourceResult.live(),
                Instant.now(),
                period.name().toLowerCase(Locale.ROOT),
                new EarthquakeFilters(
                        minimumMagnitude,
                        maximumDepthKm,
                        limit,
                        search == null ? "" : search.trim(),
                        sort.name().toLowerCase(Locale.ROOT)
                ),
                sourceResult.events().size(),
                matchingEvents.size(),
                returnedEvents.size(),
                summary,
                returnedEvents
        );
    }

    private SourceResult fetchSource(
            EarthquakePeriod period,
            double minimumMagnitude
    ) {
        try {
            List<EarthquakeEvent> events =
                    usgsEarthquakeClient.fetch(
                            period,
                            minimumMagnitude
                    );

            return new SourceResult(
                    "USGS",
                    true,
                    events
            );

        } catch (DisasterSourceUnavailableException exception) {
            return new SourceResult(
                    "DEMO_FALLBACK",
                    false,
                    demoFallback.create()
            );
        }
    }

    private Comparator<EarthquakeEvent> comparatorFor(
            EarthquakeSort sort
    ) {
        return switch (sort) {
            case MAGNITUDE ->
                    Comparator.comparingDouble(
                                    EarthquakeEvent::magnitude
                            )
                            .reversed()
                            .thenComparing(
                                    EarthquakeEvent::occurredAt,
                                    Comparator.reverseOrder()
                            );

            case DEPTH ->
                    Comparator.comparingDouble(
                                    EarthquakeEvent::depthKm
                            )
                            .reversed()
                            .thenComparing(
                                    EarthquakeEvent::occurredAt,
                                    Comparator.reverseOrder()
                            );

            case TIME ->
                    Comparator.comparing(
                                    EarthquakeEvent::occurredAt
                            )
                            .reversed();
        };
    }

    private EarthquakeSummary summarize(
            List<EarthquakeEvent> events
    ) {
        if (events.isEmpty()) {
            return new EarthquakeSummary(
                    0,
                    0.0,
                    0.0,
                    0,
                    0,
                    null
            );
        }

        double strongestMagnitude =
                events.stream()
                        .mapToDouble(EarthquakeEvent::magnitude)
                        .max()
                        .orElse(0.0);

        double averageDepth =
                events.stream()
                        .mapToDouble(EarthquakeEvent::depthKm)
                        .average()
                        .orElse(0.0);

        int significantEvents =
                (int) events.stream()
                        .filter(event ->
                                event.significance() >= 600
                                        || event.magnitude() >= 5.0
                        )
                        .count();

        int tsunamiPotentialEvents =
                (int) events.stream()
                        .filter(
                                EarthquakeEvent::tsunamiPotential
                        )
                        .count();

        Instant latestEvent =
                events.stream()
                        .map(EarthquakeEvent::occurredAt)
                        .max(Comparator.naturalOrder())
                        .orElse(null);

        return new EarthquakeSummary(
                events.size(),
                round(strongestMagnitude, 1),
                round(averageDepth, 1),
                significantEvents,
                tsunamiPotentialEvents,
                latestEvent
        );
    }

    private double round(
            double value,
            int scale
    ) {
        return BigDecimal
                .valueOf(value)
                .setScale(
                        scale,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private record SourceResult(
            String source,
            boolean live,
            List<EarthquakeEvent> events
    ) {
    }
}
