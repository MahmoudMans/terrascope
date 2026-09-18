package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceTrustLineagePinReceipt;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinReceiptHistoryResponse;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationRequest;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationResponse;
import com.terrascope.platform.service.GovernanceTrustLineagePinReceiptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/resilience-portfolio/governance-closure/trust-lineage/pinning"
)
public class GovernanceTrustLineagePinController {

    private final GovernanceTrustLineagePinReceiptService service;

    public GovernanceTrustLineagePinController(
            GovernanceTrustLineagePinReceiptService service
    ) {
        this.service = service;
    }

    @PostMapping("/verify")
    public GovernanceTrustLineagePinnedVerificationResponse verify(
            @RequestBody
            GovernanceTrustLineagePinnedVerificationRequest request
    ) {
        return service.verify(
                request
        );
    }

    @PostMapping("/receipts")
    public GovernanceTrustLineagePinReceipt createReceipt(
            @RequestBody
            GovernanceTrustLineagePinnedVerificationRequest request
    ) {
        return service.createReceipt(
                request
        );
    }

    @GetMapping("/receipts")
    public GovernanceTrustLineagePinReceiptHistoryResponse history() {
        return service.history();
    }
}
