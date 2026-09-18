package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceAssuranceReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptCheckpointHistoryResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptCheckpointVerificationResponse;
import com.terrascope.platform.service.GovernanceAssuranceReceiptCheckpointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints"
)
public class GovernanceAssuranceReceiptCheckpointController {

    private final GovernanceAssuranceReceiptCheckpointService service;

    public GovernanceAssuranceReceiptCheckpointController(
            GovernanceAssuranceReceiptCheckpointService service
    ) {
        this.service = service;
    }

    @GetMapping
    public GovernanceAssuranceReceiptCheckpointHistoryResponse history() {
        return service.history();
    }

    @PostMapping
    public GovernanceAssuranceReceiptCheckpoint create() {
        return service.create();
    }

    @GetMapping("/verify")
    public GovernanceAssuranceReceiptCheckpointVerificationResponse verify() {
        return service.verify();
    }
}
