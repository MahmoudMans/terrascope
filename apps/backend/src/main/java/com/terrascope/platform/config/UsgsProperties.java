package com.terrascope.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terrascope.usgs")
public record UsgsProperties(
        String feedBaseUrl
) {
}
