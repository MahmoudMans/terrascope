package com.terrascope.platform.infrastructure.usgs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsgsFeatureCollection(
        String type,
        Metadata metadata,
        List<Feature> features
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            Long generated,
            String title,
            Integer count,
            Integer status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(
            String type,
            Properties properties,
            Geometry geometry,
            String id
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            Double mag,
            String place,
            Long time,
            Long updated,
            String url,
            Integer felt,
            String alert,
            String status,
            Integer tsunami,
            Integer sig,
            String magType,
            String title
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(
            String type,
            List<Double> coordinates
    ) {
    }
}
