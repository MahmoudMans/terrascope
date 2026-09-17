package com.terrascope.platform.infrastructure.eonet;

import com.terrascope.platform.api.model.VolcanoEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class NasaEonetClient {

    private static final int SOURCE_FETCH_LIMIT = 500;

    private final RestClient restClient;

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
        try {
            EonetEventCollection payload =
                    restClient
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path("/events")
                                            .queryParam(
                                                    "category",
                                                    "volcanoes"
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

            return payload
                    .events()
                    .stream()
                    .map(this::toVolcanoEvent)
                    .filter(Objects::nonNull)
                    .toList();

        } catch (
                RestClientException
                | IllegalStateException exception
        ) {
            throw new EonetSourceUnavailableException(
                    "NASA EONET volcano feed is "
                            + "currently unavailable.",
                    exception
            );
        }
    }

    private VolcanoEvent toVolcanoEvent(
            EonetEventCollection.Event event
    ) {
        if (
                event == null
                || event.id() == null
                || event.title() == null
                || event.geometry() == null
                || event.geometry().isEmpty()
        ) {
            return null;
        }

        EonetEventCollection.Geometry latestGeometry =
                event
                        .geometry()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(this::isPointGeometry)
                        .max(
                                Comparator.comparing(
                                        this::geometryInstant
                                )
                        )
                        .orElse(null);

        if (latestGeometry == null) {
            return null;
        }

        List<Double> coordinates =
                latestGeometry.coordinates();

        List<EonetEventCollection.Source> sources =
                event.sources() == null
                        ? List.of()
                        : event.sources();

        EonetEventCollection.Source primarySource =
                sources
                        .stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

        String status =
                event.closed() == null
                        || event.closed().isBlank()
                        ? "open"
                        : "closed";

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
                geometryInstant(
                        latestGeometry
                ),
                safeDouble(
                        coordinates.get(0)
                ),
                safeDouble(
                        coordinates.get(1)
                ),
                event.geometry().size(),
                sources.size(),
                status,
                primarySource == null
                        ? "NASA EONET"
                        : safeText(
                                primarySource.id(),
                                "NASA EONET"
                        ),
                primarySource == null
                        ? ""
                        : safeText(
                                primarySource.url(),
                                ""
                        ),
                safeText(
                        event.link(),
                        ""
                )
        );
    }

    private boolean isPointGeometry(
            EonetEventCollection.Geometry geometry
    ) {
        return geometry.coordinates() != null
                && geometry.coordinates().size() >= 2
                && (
                        geometry.type() == null
                        || "Point".equalsIgnoreCase(
                                geometry.type()
                        )
                );
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

    private double safeDouble(
            Double value
    ) {
        return value == null
                ? 0.0
                : value;
    }
}
