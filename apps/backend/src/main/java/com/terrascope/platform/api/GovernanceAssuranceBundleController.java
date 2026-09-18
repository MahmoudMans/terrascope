package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceAssuranceBundle;
import com.terrascope.platform.api.model.GovernanceAssuranceBundleHistoryResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceBundlePreview;
import com.terrascope.platform.api.model.GovernanceAssuranceBundleVerificationResponse;
import com.terrascope.platform.service.GovernanceAssuranceBundleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resilience-portfolio/governance-closure/assurance-bundles")
public class GovernanceAssuranceBundleController {

    private final GovernanceAssuranceBundleService service;

    public GovernanceAssuranceBundleController(
            GovernanceAssuranceBundleService service
    ) {
        this.service = service;
    }

    @GetMapping("/preview/{certificateId}")
    public GovernanceAssuranceBundlePreview preview(
            @PathVariable
            UUID certificateId
    ) {
        return service.preview(
                certificateId
        );
    }

    @GetMapping
    public GovernanceAssuranceBundleHistoryResponse history() {
        return service.history();
    }

    @PostMapping("/{certificateId}")
    public GovernanceAssuranceBundle create(
            @PathVariable
            UUID certificateId
    ) {
        return service.create(
                certificateId
        );
    }

    @GetMapping("/{bundleId}/verify")
    public GovernanceAssuranceBundleVerificationResponse verify(
            @PathVariable
            UUID bundleId
    ) {
        return service.verify(
                bundleId
        );
    }
}
