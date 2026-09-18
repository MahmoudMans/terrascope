package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceOfflineTrustCheckpointBundle;
import com.terrascope.platform.api.model.GovernanceOfflineTrustCheckpointBundleHistoryResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustCheckpointBundleVerificationResponse;
import com.terrascope.platform.api.model.VerifyGovernanceOfflineTrustCheckpointBundleRequest;

import com.terrascope.platform.domain.GovernanceOfflineTrustCheckpointPortableEnvelope;

import com.terrascope.platform.service.GovernanceOfflineTrustCheckpointBundleService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles"
)
public class GovernanceOfflineTrustCheckpointBundleController {

    private final GovernanceOfflineTrustCheckpointBundleService service;

    public GovernanceOfflineTrustCheckpointBundleController(
            GovernanceOfflineTrustCheckpointBundleService service
    ) {
        this.service = service;
    }

    @PostMapping
    public GovernanceOfflineTrustCheckpointBundle create() {
        return service.create();
    }

    @GetMapping
    public GovernanceOfflineTrustCheckpointBundleHistoryResponse history() {
        return service.history();
    }

    @GetMapping("/export/{id}")
    public GovernanceOfflineTrustCheckpointPortableEnvelope export(
            @PathVariable UUID id
    ) {
        return service.export(
                id
        );
    }

    @PostMapping("/verify")
    public GovernanceOfflineTrustCheckpointBundleVerificationResponse verify(
            @RequestBody VerifyGovernanceOfflineTrustCheckpointBundleRequest request
    ) {
        return service.verify(
                request
        );
    }
}
