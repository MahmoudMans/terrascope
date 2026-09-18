package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceAssuranceEnvelopeVerificationResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceVerificationReceipt;
import com.terrascope.platform.api.model.GovernanceAssuranceVerificationReceiptHistoryResponse;
import com.terrascope.platform.service.GovernanceAssuranceEnvelopeService;
import com.terrascope.platform.service.GovernanceAssuranceStatelessVerifier;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resilience-portfolio/governance-closure/airgap")
public class GovernanceAssuranceEnvelopeController {

    private final GovernanceAssuranceEnvelopeService service;

    private final GovernanceAssuranceStatelessVerifier verifier;

    public GovernanceAssuranceEnvelopeController(
            GovernanceAssuranceEnvelopeService service,
            GovernanceAssuranceStatelessVerifier verifier
    ) {
        this.service = service;
        this.verifier = verifier;
    }

    @GetMapping(
            value = "/export/{bundleId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<byte[]> export(
            @PathVariable
            UUID bundleId
    ) {
        byte[] payload =
                service.export(
                        bundleId
                );

        return ResponseEntity
                .ok()
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .contentLength(
                        payload.length
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,

                        ContentDisposition
                                .attachment()
                                .filename(
                                        "terrascope-governance-assurance-"
                                                + bundleId
                                                + ".json",
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .body(
                        payload
                );
    }

    @PostMapping(
            value = "/verify-envelope",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public GovernanceAssuranceEnvelopeVerificationResponse verify(
            @RequestBody
            byte[] envelopeBytes
    ) {
        return verifier.verify(
                envelopeBytes
        );
    }

    @PostMapping(
            value = "/verification-receipts",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public GovernanceAssuranceVerificationReceipt createReceipt(
            @RequestBody
            byte[] envelopeBytes
    ) {
        return service.createReceipt(
                envelopeBytes
        );
    }

    @GetMapping("/verification-receipts")
    public GovernanceAssuranceVerificationReceiptHistoryResponse receiptHistory() {
        return service.receiptHistory();
    }
}
