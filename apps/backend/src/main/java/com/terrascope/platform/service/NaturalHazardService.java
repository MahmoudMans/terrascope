package com.terrascope.platform.service;

import com.terrascope.platform.api.model.NaturalHazardEvent;
import com.terrascope.platform.api.model.NaturalHazardFeedResponse;
import com.terrascope.platform.api.model.NaturalHazardSummary;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.infrastructure.eonet.EonetSourceUnavailableException;
import com.terrascope.platform.infrastructure.eonet.NasaEonetClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class NaturalHazardService {

    private final NasaEonetClient eonetClient;
    private final DemoNaturalHazardFallback demoFallback;

    public NaturalHazardService(
            NasaEonetClient eonetClient,
            DemoNaturalHazardFallback demoFallback
    ) {
        this.eonetClient = eonetClient;
        this.demoFallback = demoFallback;
    }

    public NaturalHazardFeedResponse query(
            HazardType hazardType,
            String requestedStatus,
            int days,
            int limit,
            String search
    ) {
        if (!hazardType.isGenericEonetHazard()) {
            throw new IllegalArgumentException(
                    "Hazard type '"
                            + hazardType.key()
                            + "' is not available from this endpoint."
            );
        }

        String status =
                normalizeStatus(
                        requestedStatus
                );

        SourceResult sourceResult =
                fetchSource(
                        hazardType,
                        status,
                        days
                );

        String normalizedSearch =
                search == null
                        ? ""
                        : search
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        List<NaturalHazardEvent> matching =
                sourceResult
                        .events()
                        .stream()
                        .filter(event ->
                                matchesSearch(
                                        event,
                                        normalizedSearch
                                )
                        )
                        .sorted(
                                Comparator.comparing(
                                                NaturalHazardEvent::observedAt
                                        )
                                        .reversed()
                        )
                        .toList();

        List<NaturalHazardEvent> returned =
                matching
                        .stream()
                        .limit(limit)
                        .toList();

        return new NaturalHazardFeedResponse(
                hazardType.key(),
                sourceResult.source(),
                sourceResult.live(),
                Instant.now(),
                status,
                days,
                search == null
                        ? ""
                        : search.trim(),
                sourceResult.events().size(),
                matching.size(),
                returned.size(),
                summarize(
                        matching
                ),
                returned
        );
    }

    private SourceResult fetchSource(
            HazardType hazardType,
            String status,
            int days
    ) {
        try {
            return new SourceResult(
                    "NASA_EONET",
                    true,
                    eonetClient.fetchNaturalHazardEvents(
                            hazardType,
                            status,
                            days
                    )
            );

        } catch (
                EonetSourceUnavailableException exception
        ) {
            return new SourceResult(
                    "DEMO_FALLBACK",
                    false,
                    demoFallback.create(
                            hazardType
                    )
            );
        }
    }

    private boolean matchesSearch(
            NaturalHazardEvent event,
            String search
    ) {
        if (search.isBlank()) {
            return true;
        }

        String haystack =
                (
                        event.title()
                                + " "
                                + event.description()
                                + " "
                                + event.primarySource()
                                + " "
                                + event.magnitudeDescription()
                )
                        .toLowerCase(
                                Locale.ROOT
                        );

        return haystack.contains(
                search
        );
    }

    private NaturalHazardSummary summarize(
            List<NaturalHazardEvent> events
    ) {
        if (events.isEmpty()) {
            return new NaturalHazardSummary(
                    0,
                    0,
                    0,
                    0,
                    null
            );
        }

        int totalObservations =
                events
                        .stream()
                        .mapToInt(
                                NaturalHazardEvent::observationCount
                        )
                        .sum();

        int sourceReferences =
                events
                        .stream()
                        .mapToInt(
                                NaturalHazardEvent::sourceCount
                        )
                        .sum();

        int magnitudeEvents =
                (int) events
                        .stream()
                        .filter(event ->
                                event.magnitudeValue() != null
                        )
                        .count();

        Instant latest =
                events
                        .stream()
                        .map(
                                NaturalHazardEvent::observedAt
                        )
                        .max(
                                Comparator.naturalOrder()
                        )
                        .orElse(null);

        return new NaturalHazardSummary(
                events.size(),
                totalObservations,
                sourceReferences,
                magnitudeEvents,
                latest
        );
    }

    private String normalizeStatus(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            return "open";
        }

        String normalized =
                value
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (
                !normalized.equals("open")
                && !normalized.equals("closed")
                && !normalized.equals("all")
        ) {
            throw new IllegalArgumentException(
                    "Unsupported status '"
                            + value
                            + "'. Expected one of: "
                            + "open, closed, all."
            );
        }

        return normalized;
    }

    private record SourceResult(
            String source,
            boolean live,
            List<NaturalHazardEvent> events
    ) {
    }
}
