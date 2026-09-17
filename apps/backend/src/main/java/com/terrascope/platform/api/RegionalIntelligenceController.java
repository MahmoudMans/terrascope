package com.terrascope.platform.api;

import com.terrascope.platform.api.model.RegionalAnalysisHistoryResponse;
import com.terrascope.platform.api.model.RegionalAnalysisRequest;
import com.terrascope.platform.api.model.RegionalAnalysisResult;
import com.terrascope.platform.service.RegionalIntelligenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regional-intelligence")
public class RegionalIntelligenceController {

    private final RegionalIntelligenceService service;

    public RegionalIntelligenceController(
            RegionalIntelligenceService service
    ) {
        this.service = service;
    }

    @GetMapping("/history")
    public RegionalAnalysisHistoryResponse history() {
        return service.history();
    }

    @PostMapping("/analyze")
    public RegionalAnalysisResult analyze(
            @Valid
            @RequestBody
            RegionalAnalysisRequest request
    ) {
        return service.analyze(
                request
        );
    }
}
