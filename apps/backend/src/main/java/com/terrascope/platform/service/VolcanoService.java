package com.terrascope.platform.service;

import com.terrascope.platform.api.model.VolcanoEvent;
import com.terrascope.platform.api.model.VolcanoFeedResponse;
import com.terrascope.platform.api.model.VolcanoSummary;
import com.terrascope.platform.infrastructure.eonet.EonetSourceUnavailableException;
import com.terrascope.platform.infrastructure.eonet.NasaEonetClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class VolcanoService {

    private final NasaEonetClient eonetClient;
    private final DemoVolcanoFallback demoFallback;

    public VolcanoService(
            NasaEonetClient eonetClient,
            DemoVolcanoFallback demoFallback
    ) {
        this.eonetClient = eonetClient;
        this.demoFallback = demoFallback;
    }

    public VolcanoFeedResponse query(
            String requestedStatus,
            int days,
            int limit,
            String search
    ) {
        String status =
                normalizeStatus(
                        requestedStatus
                );

        SourceResult sourceResult =
                fetchSource(
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

        List<VolcanoEvent> matchingEvents =
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
                                                VolcanoEvent::observedAt
                                        )
                                        .reversed()
                        )
                        .toList();

        List<VolcanoEvent> returnedEvents =
                matchingEvents
                        .stream()
                        .limit(limit)
                        .toList();

        return new VolcanoFeedResponse(
                sourceResult.source(),
                sourceResult.live(),
                Instant.now(),
                status,
                days,
                search == null
                        ? ""
                        : search.trim(),
                sourceResult.events().size(),
                matchingEvents.size(),
                returnedEvents.size(),
                summarize(
                        matchingEvents
                ),
                returnedEvents
        );
    }

    private SourceResult fetchSource(
            String status,
            int days
    ) {
        try {
            return new SourceResult(
                    "NASA_EONET",
                    true,
                    eonetClient.fetchVolcanoEvents(
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
                    demoFallback.create()
            );
        }
    }

    private boolean matchesSearch(
            VolcanoEvent event,
            String normalizedSearch
    ) {
        if (normalizedSearch.isBlank()) {
            return true;
        }

        String haystack =
                (
                        event.title()
                                + " "
                                + event.description()
                                + " "
                                + event.primarySource()
                )
                        .toLowerCase(
                                Locale.ROOT
                        );

        return haystack.contains(
                normalizedSearch
        );
    }

    private VolcanoSummary summarize(
            List<VolcanoEvent> events
    ) {
        if (events.isEmpty()) {
            return new VolcanoSummary(
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
                                VolcanoEvent::observationCount
                        )
                        .sum();

        int sourceReferences =
                events
                        .stream()
                        .mapToInt(
                                VolcanoEvent::sourceCount
                        )
                        .sum();

        Instant latest =
                events
                        .stream()
                        .map(
                                VolcanoEvent::observedAt
                        )
                        .max(
                                Comparator.naturalOrder()
                        )
                        .orElse(null);

        return new VolcanoSummary(
                events.size(),
                totalObservations,
                sourceReferences,
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
                    "Unsupported volcano status '"
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
            List<VolcanoEvent> events
    ) {
    }
}
