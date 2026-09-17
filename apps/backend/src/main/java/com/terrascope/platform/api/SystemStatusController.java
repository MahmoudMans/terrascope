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
                "0.1.0",
                Instant.now(),
                List.of(
                        new SystemStatusResponse.DataSourceStatus(
                                "usgs-earthquakes",
                                "USGS Earthquake Hazards Program",
                                "earthquakes",
                                "configured"
                        ),
                        new SystemStatusResponse.DataSourceStatus(
                                "volcano-monitor",
                                "Volcano Monitoring",
                                "volcanoes",
                                "planned"
                        ),
                        new SystemStatusResponse.DataSourceStatus(
                                "wildfire-monitor",
                                "Wildfire Monitoring",
                                "wildfires",
                                "planned"
                        ),
                        new SystemStatusResponse.DataSourceStatus(
                                "storm-monitor",
                                "Storm Monitoring",
                                "storms",
                                "planned"
                        )
                )
        );
    }
}
