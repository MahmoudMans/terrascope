package com.terrascope.platform.api;

import com.terrascope.platform.api.model.VolcanoFeedResponse;
import com.terrascope.platform.service.VolcanoService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/volcanoes")
@Validated
public class VolcanoController {

    private final VolcanoService volcanoService;

    public VolcanoController(
            VolcanoService volcanoService
    ) {
        this.volcanoService = volcanoService;
    }

    @GetMapping
    public VolcanoFeedResponse listVolcanoEvents(
            @RequestParam(defaultValue = "open")
            String status,

            @RequestParam(defaultValue = "3650")
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
        return volcanoService.query(
                status,
                days,
                limit,
                search
        );
    }
}
