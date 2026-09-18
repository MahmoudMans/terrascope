package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerHistoryResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerVerificationResponse;
import com.terrascope.platform.service.GovernanceOfflineTrustReceiptLedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger"
)
public class GovernanceOfflineTrustReceiptLedgerController {

    private final GovernanceOfflineTrustReceiptLedgerService service;

    public GovernanceOfflineTrustReceiptLedgerController(
            GovernanceOfflineTrustReceiptLedgerService service
    ) {
        this.service = service;
    }

    @GetMapping
    public GovernanceOfflineTrustReceiptLedgerHistoryResponse history() {
        return service.history();
    }

    @GetMapping("/verify")
    public GovernanceOfflineTrustReceiptLedgerVerificationResponse verify() {
        return service.verify();
    }
}
