package com.terrascope.platform.infrastructure.eonet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EonetEventCollection(
        List<Event> events
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            String id,
            String title,
            String description,
            String link,
            String closed,
            List<Category> categories,
            List<Source> sources,
            List<Geometry> geometry
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(
            String id,
            String title
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(
            String id,
            String url
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(
            Double magnitudeValue,
            String magnitudeUnit,
            String magnitudeDescription,
            String date,
            String type,
            List<Double> coordinates
    ) {
    }
}
