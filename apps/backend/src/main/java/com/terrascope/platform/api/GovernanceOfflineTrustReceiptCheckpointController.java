package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpointHistoryResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpointVerificationResponse;

import com.terrascope.platform.service.GovernanceOfflineTrustReceiptCheckpointService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoints"
)
public class GovernanceOfflineTrustReceiptCheckpointController {

    private final GovernanceOfflineTrustReceiptCheckpointService service;

    public GovernanceOfflineTrustReceiptCheckpointController(
            GovernanceOfflineTrustReceiptCheckpointService service
    ) {
        this.service = service;
    }

    @PostMapping
    public GovernanceOfflineTrustReceiptCheckpoint create() {
        return service.create();
    }

    @GetMapping
    public GovernanceOfflineTrustReceiptCheckpointHistoryResponse history() {
        return service.history();
    }

    @GetMapping("/verify")
    public GovernanceOfflineTrustReceiptCheckpointVerificationResponse verify() {
        return service.verify();
    }
}
