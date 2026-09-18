package com.terrascope.platform.api;

import com.terrascope.platform.api.model.SystemStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SystemStatusController {

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return new SystemStatusResponse(
                "TerraScope",
                "operational",
                "0.32.0",
                Instant.now(),
                List.of(
                        new SystemStatusResponse.DataSourceStatus(
                                "usgs-earthquakes",
                                "USGS Earthquake Hazards Program",
                                "earthquakes",
                                "configured"
                        ),
                        new SystemStatusResponse.DataSourceStatus(
                                "nasa-eonet-volcanoes",
                                "NASA EONET",
                                "volcanoes",
                                "configured"
                        ),
                        new SystemStatusResponse.DataSourceStatus(
                                "nasa-eonet-wildfires",
                                "NASA EONET",
                                "wildfires",
                                "configured"
                        ),
                        new SystemStatusResponse.DataSourceStatus(
                                "nasa-eonet-storms",
                                "NASA EONET",
                                "severe-storms",
                                "configured"
                        )
                )
        );
    }
}
