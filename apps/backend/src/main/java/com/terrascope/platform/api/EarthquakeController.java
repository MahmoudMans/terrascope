package com.terrascope.platform.api;

import com.terrascope.platform.api.model.EarthquakeFeedResponse;
import com.terrascope.platform.domain.EarthquakePeriod;
import com.terrascope.platform.domain.EarthquakeSort;
import com.terrascope.platform.service.EarthquakeService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/earthquakes")
@Validated
public class EarthquakeController {

    private final EarthquakeService earthquakeService;

    public EarthquakeController(
            EarthquakeService earthquakeService
    ) {
        this.earthquakeService = earthquakeService;
    }

    @GetMapping
    public EarthquakeFeedResponse listEarthquakes(
            @RequestParam(defaultValue = "day")
            String period,

            @RequestParam(defaultValue = "2.5")
            @DecimalMin("-2.0")
            @DecimalMax("10.0")
            double minMagnitude,

            @RequestParam(defaultValue = "1000")
            @DecimalMin("0.0")
            @DecimalMax("1000.0")
            double maxDepth,

            @RequestParam(defaultValue = "300")
            @Min(1)
            @Max(1000)
            int limit,

            @RequestParam(defaultValue = "")
            @Size(max = 120)
            String search,

            @RequestParam(defaultValue = "time")
            String sort
    ) {
        return earthquakeService.query(
                EarthquakePeriod.from(period),
                minMagnitude,
                maxDepth,
                limit,
                search,
                EarthquakeSort.from(sort)
        );
    }
}
