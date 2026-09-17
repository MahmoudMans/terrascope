package com.terrascope.platform.api;

import com.terrascope.platform.api.model.ResilienceAuditLedgerHistoryResponse;
import com.terrascope.platform.api.model.ResilienceAuditLedgerPreview;
import com.terrascope.platform.api.model.ResilienceAuditLedgerVerificationResponse;
import com.terrascope.platform.api.model.ResilienceAuditReceipt;
import com.terrascope.platform.service.ResilienceAuditLedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/resilience-audit-ledger")
public class ResilienceAuditLedgerController {

    private final ResilienceAuditLedgerService service;

    public ResilienceAuditLedgerController(
            ResilienceAuditLedgerService service
    ) {
        this.service = service;
    }

    @GetMapping("/preview")
    public ResilienceAuditLedgerPreview preview(
            @PathVariable
            UUID incidentId
    ) {
        return service.preview(
                incidentId
        );
    }

    @GetMapping("/receipts")
    public ResilienceAuditLedgerHistoryResponse history(
            @PathVariable
            UUID incidentId
    ) {
        return service.history(
                incidentId
        );
    }

    @PostMapping("/receipts")
    public ResilienceAuditReceipt createReceipt(
            @PathVariable
            UUID incidentId
    ) {
        return service.createReceipt(
                incidentId
        );
    }

    @GetMapping("/verify")
    public ResilienceAuditLedgerVerificationResponse verify(
            @PathVariable
            UUID incidentId
    ) {
        return service.verify(
                incidentId
        );
    }
}
