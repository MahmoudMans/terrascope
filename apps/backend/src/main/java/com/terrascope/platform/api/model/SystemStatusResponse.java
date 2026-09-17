package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.List;

public record SystemStatusResponse(
        String application,
        String status,
        String version,
        Instant timestamp,
        List<DataSourceStatus> dataSources
) {

    public record DataSourceStatus(
            String id,
            String name,
            String category,
            String status
    ) {
    }
}
