package com.terrascope.platform.api;

import com.terrascope.platform.api.model.BenefitRealizationHistoryResponse;
import com.terrascope.platform.api.model.BenefitRealizationResponse;
import com.terrascope.platform.api.model.BenefitRealizationSnapshot;
import com.terrascope.platform.service.BenefitRealizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resilience-portfolio/benefit-realization")
public class BenefitRealizationController {

    private final BenefitRealizationService service;

    public BenefitRealizationController(
            BenefitRealizationService service
    ) {
        this.service = service;
    }

    @GetMapping
    public BenefitRealizationResponse realization() {
        return service.realization();
    }

    @GetMapping("/history")
    public BenefitRealizationHistoryResponse history() {
        return service.history();
    }

    @PostMapping("/snapshots")
    public BenefitRealizationSnapshot createSnapshot() {
        return service.createSnapshot();
    }
}
