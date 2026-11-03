package com.terrascope.platform.infrastructure.usgs;

import com.terrascope.platform.api.model.EarthquakeEvent;
import com.terrascope.platform.config.UsgsProperties;
import com.terrascope.platform.domain.EarthquakePeriod;
import com.terrascope.platform.infrastructure.FeedCache;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
public class UsgsEarthquakeClient {

    private final RestClient restClient;

    private final FeedCache<String, List<EarthquakeEvent>> cache =
            new FeedCache<>(
                    Duration.ofSeconds(90),
                    Duration.ofMinutes(10)
            );

    public UsgsEarthquakeClient(
            RestClient.Builder restClientBuilder,
            UsgsProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.feedBaseUrl())
                .build();
    }

    public List<EarthquakeEvent> fetch(
            EarthquakePeriod period,
            double requestedMinimumMagnitude
    ) {
        String feedName = selectFeedPrefix(requestedMinimumMagnitude)
                + "_"
                + period.feedSuffix()
                + ".geojson";

        return cache.get(
                feedName,
                () -> download(feedName)
        );
    }

    private List<EarthquakeEvent> download(
            String feedName
    ) {
        try {
            UsgsFeatureCollection payload = restClient
                    .get()
                    .uri("/" + feedName)
                    .retrieve()
                    .body(UsgsFeatureCollection.class);

            if (payload == null || payload.features() == null) {
                return List.of();
            }

            return payload.features()
                    .stream()
                    .map(this::toEarthquakeEvent)
                    .filter(Objects::nonNull)
                    .toList();

        } catch (RestClientException | IllegalStateException exception) {
            throw new DisasterSourceUnavailableException(
                    "USGS earthquake feed is currently unavailable.",
                    exception
            );
        }
    }

    private String selectFeedPrefix(double minimumMagnitude) {
        if (minimumMagnitude >= 4.5) {
            return "4.5";
        }

        if (minimumMagnitude >= 2.5) {
            return "2.5";
        }

        if (minimumMagnitude >= 1.0) {
            return "1.0";
        }

        return "all";
    }

    private EarthquakeEvent toEarthquakeEvent(
            UsgsFeatureCollection.Feature feature
    ) {
        if (feature == null
                || feature.id() == null
                || feature.properties() == null
                || feature.geometry() == null
                || feature.geometry().coordinates() == null
                || feature.geometry().coordinates().size() < 3) {
            return null;
        }

        UsgsFeatureCollection.Properties properties =
                feature.properties();

        List<Double> coordinates =
                feature.geometry().coordinates();

        long eventTime = properties.time() == null
                ? 0L
                : properties.time();

        long updatedTime = properties.updated() == null
                ? eventTime
                : properties.updated();

        return new EarthquakeEvent(
                feature.id(),
                safeDouble(properties.mag()),
                safeText(properties.place(), "Unknown location"),
                Instant.ofEpochMilli(eventTime),
                Instant.ofEpochMilli(updatedTime),
                safeDouble(coordinates.get(0)),
                safeDouble(coordinates.get(1)),
                safeDouble(coordinates.get(2)),
                properties.sig() == null ? 0 : properties.sig(),
                properties.felt(),
                properties.tsunami() != null
                        && properties.tsunami() == 1,
                normalizeAlert(properties.alert()),
                safeText(properties.status(), "unknown"),
                safeText(properties.url(), "")
        );
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private String safeText(
            String value,
            String fallback
    ) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value;
    }

    private String normalizeAlert(String alert) {
        if (alert == null || alert.isBlank()) {
            return "none";
        }

        return alert;
    }
}
