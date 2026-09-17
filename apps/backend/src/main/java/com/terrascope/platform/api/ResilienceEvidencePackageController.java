package com.terrascope.platform.api;

import com.terrascope.platform.api.model.EvidencePackageVerificationResponse;
import com.terrascope.platform.api.model.ResilienceEvidencePackage;
import com.terrascope.platform.api.model.ResilienceEvidencePackageHistoryResponse;
import com.terrascope.platform.api.model.ResilienceEvidencePackagePreview;
import com.terrascope.platform.service.ResilienceEvidencePackageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/resilience-evidence")
public class ResilienceEvidencePackageController {

    private final ResilienceEvidencePackageService service;

    public ResilienceEvidencePackageController(
            ResilienceEvidencePackageService service
    ) {
        this.service = service;
    }

    @GetMapping("/preview")
    public ResilienceEvidencePackagePreview preview(
            @PathVariable
            UUID incidentId
    ) {
        return service.preview(
                incidentId
        );
    }

    @GetMapping("/packages")
    public ResilienceEvidencePackageHistoryResponse history(
            @PathVariable
            UUID incidentId
    ) {
        return service.history(
                incidentId
        );
    }

    @PostMapping("/packages")
    public ResilienceEvidencePackage create(
            @PathVariable
            UUID incidentId
    ) {
        return service.create(
                incidentId
        );
    }

    @GetMapping("/packages/{packageId}/verify")
    public EvidencePackageVerificationResponse verify(
            @PathVariable
            UUID incidentId,

            @PathVariable
            UUID packageId
    ) {
        return service.verify(
                incidentId,
                packageId
        );
    }
}
