package com.terrascope.platform.infrastructure.eonet;

import com.terrascope.platform.api.model.NaturalHazardEvent;
import com.terrascope.platform.api.model.VolcanoEvent;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.infrastructure.FeedCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class NasaEonetClient {

    private static final int SOURCE_FETCH_LIMIT = 500;

    private final RestClient restClient;

    private final FeedCache<String, List<EonetEventCollection.Event>> cache =
            new FeedCache<>(
                    Duration.ofSeconds(90),
                    Duration.ofMinutes(10)
            );

    public NasaEonetClient(
            RestClient.Builder restClientBuilder,
            @Value(
                    "${terrascope.eonet.base-url:"
                            + "https://eonet.gsfc.nasa.gov/api/v3}"
            )
            String baseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public List<VolcanoEvent> fetchVolcanoEvents(
            String status,
            int days
    ) {
        return fetchCategoryEvents(
                HazardType.VOLCANO,
                status,
                days
        )
                .stream()
                .map(this::toVolcanoEvent)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<NaturalHazardEvent> fetchNaturalHazardEvents(
            HazardType hazardType,
            String status,
            int days
    ) {
        if (!hazardType.isGenericEonetHazard()) {
            throw new IllegalArgumentException(
                    "Hazard type "
                            + hazardType
                            + " is not handled by the "
                            + "generic EONET event API."
            );
        }

        return fetchCategoryEvents(
                hazardType,
                status,
                days
        )
                .stream()
                .map(event ->
                        toNaturalHazardEvent(
                                hazardType,
                                event
                        )
                )
                .filter(Objects::nonNull)
                .toList();
    }

    private List<EonetEventCollection.Event> fetchCategoryEvents(
            HazardType hazardType,
            String status,
            int days
    ) {
        return cache.get(
                hazardType.name() + "|" + status + "|" + days,
                () -> downloadCategoryEvents(
                        hazardType,
                        status,
                        days
                )
        );
    }

    private List<EonetEventCollection.Event> downloadCategoryEvents(
            HazardType hazardType,
            String status,
            int days
    ) {
        if (!hazardType.hasEonetCategory()) {
            throw new IllegalArgumentException(
                    "Hazard type "
                            + hazardType
                            + " has no NASA EONET category."
            );
        }

        try {
            EonetEventCollection payload =
                    restClient
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path("/events")
                                            .queryParam(
                                                    "category",
                                                    hazardType
                                                            .eonetCategory()
                                            )
                                            .queryParam(
                                                    "status",
                                                    status
                                            )
                                            .queryParam(
                                                    "days",
                                                    days
                                            )
                                            .queryParam(
                                                    "limit",
                                                    SOURCE_FETCH_LIMIT
                                            )
                                            .build()
                            )
                            .retrieve()
                            .body(
                                    EonetEventCollection.class
                            );

            if (
                    payload == null
                    || payload.events() == null
            ) {
                return List.of();
            }

            return payload.events();

        } catch (
                RestClientException
                | IllegalStateException exception
        ) {
            throw new EonetSourceUnavailableException(
                    "NASA EONET "
                            + hazardType.label()
                            + " feed is currently unavailable.",
                    exception
            );
        }
    }

    private VolcanoEvent toVolcanoEvent(
            EonetEventCollection.Event event
    ) {
        ResolvedGeometry geometry =
                latestGeometry(event);

        if (
                event == null
                || event.id() == null
                || event.title() == null
                || geometry == null
        ) {
            return null;
        }

        SourceInfo source =
                sourceInfo(event);

        return new VolcanoEvent(
                event.id(),
                safeText(
                        event.title(),
                        "Unnamed volcanic event"
                ),
                safeText(
                        event.description(),
                        ""
                ),
                geometry.observedAt(),
                geometry.longitude(),
                geometry.latitude(),
                event.geometry() == null
                        ? 0
                        : event.geometry().size(),
                source.sourceCount(),
                eventStatus(event),
                source.primarySource(),
                source.sourceUrl(),
                safeText(
                        event.link(),
                        ""
                )
        );
    }

    private NaturalHazardEvent toNaturalHazardEvent(
            HazardType hazardType,
            EonetEventCollection.Event event
    ) {
        ResolvedGeometry geometry =
                latestGeometry(event);

        if (
                event == null
                || event.id() == null
                || event.title() == null
                || geometry == null
        ) {
            return null;
        }

        SourceInfo source =
                sourceInfo(event);

        return new NaturalHazardEvent(
                event.id(),
                hazardType.key(),
                safeText(
                        event.title(),
                        "Unnamed "
                                + hazardType.label()
                                + " event"
                ),
                safeText(
                        event.description(),
                        ""
                ),
                geometry.observedAt(),
                geometry.longitude(),
                geometry.latitude(),
                event.geometry() == null
                        ? 0
                        : event.geometry().size(),
                source.sourceCount(),
                eventStatus(event),
                source.primarySource(),
                source.sourceUrl(),
                safeText(
                        event.link(),
                        ""
                ),
                geometry.magnitudeValue(),
                safeText(
                        geometry.magnitudeUnit(),
                        ""
                ),
                safeText(
                        geometry.magnitudeDescription(),
                        ""
                )
        );
    }

    private ResolvedGeometry latestGeometry(
            EonetEventCollection.Event event
    ) {
        if (
                event == null
                || event.geometry() == null
                || event.geometry().isEmpty()
        ) {
            return null;
        }

        return event
                .geometry()
                .stream()
                .filter(Objects::nonNull)
                .map(this::resolveGeometry)
                .filter(Objects::nonNull)
                .max(
                        Comparator.comparing(
                                ResolvedGeometry::observedAt
                        )
                )
                .orElse(null);
    }

    private ResolvedGeometry resolveGeometry(
            EonetEventCollection.Geometry geometry
    ) {
        Coordinate coordinate =
                extractRepresentativeCoordinate(
                        geometry.coordinates()
                );

        if (coordinate == null) {
            return null;
        }

        return new ResolvedGeometry(
                geometryInstant(
                        geometry
                ),
                coordinate.longitude(),
                coordinate.latitude(),
                geometry.magnitudeValue(),
                geometry.magnitudeUnit(),
                geometry.magnitudeDescription()
        );
    }

    private Coordinate extractRepresentativeCoordinate(
            Object coordinates
    ) {
        if (!(coordinates instanceof List<?> values)) {
            return null;
        }

        if (
                values.size() >= 2
                && values.get(0) instanceof Number longitude
                && values.get(1) instanceof Number latitude
        ) {
            return new Coordinate(
                    longitude.doubleValue(),
                    latitude.doubleValue()
            );
        }

        for (Object child : values) {
            Coordinate nested =
                    extractRepresentativeCoordinate(
                            child
                    );

            if (nested != null) {
                return nested;
            }
        }

        return null;
    }

    private SourceInfo sourceInfo(
            EonetEventCollection.Event event
    ) {
        List<EonetEventCollection.Source> sources =
                event.sources() == null
                        ? List.of()
                        : event.sources();

        EonetEventCollection.Source primary =
                sources
                        .stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

        return new SourceInfo(
                sources.size(),
                primary == null
                        ? "NASA EONET"
                        : safeText(
                                primary.id(),
                                "NASA EONET"
                        ),
                primary == null
                        ? ""
                        : safeText(
                                primary.url(),
                                ""
                        )
        );
    }

    private String eventStatus(
            EonetEventCollection.Event event
    ) {
        return event.closed() == null
                || event.closed().isBlank()
                ? "open"
                : "closed";
    }

    private Instant geometryInstant(
            EonetEventCollection.Geometry geometry
    ) {
        if (
                geometry.date() == null
                || geometry.date().isBlank()
        ) {
            return Instant.EPOCH;
        }

        try {
            return Instant.parse(
                    geometry.date()
            );

        } catch (
                DateTimeParseException exception
        ) {
            return Instant.EPOCH;
        }
    }

    private String safeText(
            String value,
            String fallback
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            return fallback;
        }

        return value;
    }

    private record Coordinate(
            double longitude,
            double latitude
    ) {
    }

    private record SourceInfo(
            int sourceCount,
            String primarySource,
            String sourceUrl
    ) {
    }

    private record ResolvedGeometry(
            Instant observedAt,
            double longitude,
            double latitude,
            Double magnitudeValue,
            String magnitudeUnit,
            String magnitudeDescription
    ) {
    }
}
