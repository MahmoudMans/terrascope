package com.terrascope.platform.api;

import com.terrascope.platform.api.model.NaturalHazardFeedResponse;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.service.NaturalHazardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hazards/events")
@Validated
public class NaturalHazardController {

    private final NaturalHazardService service;

    public NaturalHazardController(
            NaturalHazardService service
    ) {
        this.service = service;
    }

    @GetMapping
    public NaturalHazardFeedResponse events(
            @RequestParam
            String type,

            @RequestParam(defaultValue = "open")
            String status,

            @RequestParam(defaultValue = "90")
            @Min(1)
            @Max(3650)
            int days,

            @RequestParam(defaultValue = "120")
            @Min(1)
            @Max(500)
            int limit,

            @RequestParam(defaultValue = "")
            @Size(max = 120)
            String search
    ) {
        return service.query(
                HazardType.fromKey(type),
                status,
                days,
                limit,
                search
        );
    }
}
