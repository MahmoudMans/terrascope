package com.terrascope.platform.api;

import com.terrascope.platform.api.model.IncidentResilienceCertification;
import com.terrascope.platform.api.model.ResilienceCertificationHistoryResponse;
import com.terrascope.platform.api.model.ResilienceCertificationReadinessResponse;
import com.terrascope.platform.service.ResilienceCertificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/resilience-certification")
public class ResilienceCertificationController {

    private final ResilienceCertificationService service;

    public ResilienceCertificationController(
            ResilienceCertificationService service
    ) {
        this.service = service;
    }

    @GetMapping("/readiness")
    public ResilienceCertificationReadinessResponse readiness(
            @PathVariable
            UUID incidentId
    ) {
        return service.readiness(
                incidentId
        );
    }

    @GetMapping("/history")
    public ResilienceCertificationHistoryResponse history(
            @PathVariable
            UUID incidentId
    ) {
        return service.history(
                incidentId
        );
    }

    @PostMapping
    public IncidentResilienceCertification certify(
            @PathVariable
            UUID incidentId
    ) {
        return service.certify(
                incidentId
        );
    }
}
