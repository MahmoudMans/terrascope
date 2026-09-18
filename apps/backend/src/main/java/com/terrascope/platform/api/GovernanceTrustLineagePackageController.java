package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceTrustLineagePackage;
import com.terrascope.platform.api.model.GovernanceTrustLineagePackageHistoryResponse;
import com.terrascope.platform.api.model.GovernanceTrustLineageVerificationResponse;
import com.terrascope.platform.service.GovernanceTrustLineagePackageService;
import com.terrascope.platform.service.GovernanceTrustLineagePortableVerifier;
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
@RequestMapping(
        "/api/v1/resilience-portfolio/governance-closure/trust-lineage"
)
public class GovernanceTrustLineagePackageController {

    private final GovernanceTrustLineagePackageService service;

    private final GovernanceTrustLineagePortableVerifier verifier;

    public GovernanceTrustLineagePackageController(
            GovernanceTrustLineagePackageService service,
            GovernanceTrustLineagePortableVerifier verifier
    ) {
        this.service = service;
        this.verifier = verifier;
    }

    @PostMapping("/packages")
    public GovernanceTrustLineagePackage createPackage() {
        return service.create();
    }

    @GetMapping("/packages")
    public GovernanceTrustLineagePackageHistoryResponse history() {
        return service.history();
    }

    @GetMapping(
            value = "/packages/export/{packageId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<byte[]> export(
            @PathVariable
            UUID packageId
    ) {
        byte[] bytes =
                service.export(
                        packageId
                );

        return ResponseEntity
                .ok()
                .contentType(
                        MediaType.APPLICATION_JSON
                )
                .contentLength(
                        bytes.length
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,

                        ContentDisposition
                                .attachment()
                                .filename(
                                        "terrascope-trust-lineage-"
                                                + packageId
                                                + ".json",
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .body(
                        bytes
                );
    }

    @PostMapping(
            value = "/verify-envelope",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public GovernanceTrustLineageVerificationResponse verifyEnvelope(
            @RequestBody
            byte[] envelopeBytes
    ) {
        return verifier.verify(
                envelopeBytes
        );
    }
}
