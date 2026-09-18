package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceipt;
import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceiptHistoryResponse;
import com.terrascope.platform.api.model.ImportGovernanceOfflineTrustVerificationReceiptRequest;
import com.terrascope.platform.service.GovernanceOfflineTrustVerificationReceiptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification"
)
public class GovernanceOfflineTrustVerificationReceiptController {

    private final GovernanceOfflineTrustVerificationReceiptService service;

    public GovernanceOfflineTrustVerificationReceiptController(
            GovernanceOfflineTrustVerificationReceiptService service
    ) {
        this.service = service;
    }

    @PostMapping("/imports")
    public GovernanceOfflineTrustVerificationReceipt importReceipt(
            @RequestBody
            ImportGovernanceOfflineTrustVerificationReceiptRequest request
    ) {
        return service.importReceipt(
                request
        );
    }

    @GetMapping("/imports")
    public GovernanceOfflineTrustVerificationReceiptHistoryResponse history() {
        return service.history();
    }
}
