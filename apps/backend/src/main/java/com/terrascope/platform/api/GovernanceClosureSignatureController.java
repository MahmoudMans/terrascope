package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceClosureSignature;
import com.terrascope.platform.api.model.GovernanceClosureSignatureHistoryResponse;
import com.terrascope.platform.api.model.GovernanceSignatureVerificationResponse;
import com.terrascope.platform.api.model.GovernanceSigningReadinessResponse;
import com.terrascope.platform.api.model.GovernanceTrustAnchorHistoryResponse;
import com.terrascope.platform.api.model.GovernanceTrustAnchorView;
import com.terrascope.platform.api.model.RegisterGovernanceTrustAnchorRequest;
import com.terrascope.platform.api.model.RetireGovernanceTrustAnchorRequest;
import com.terrascope.platform.service.GovernanceClosureSignatureService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resilience-portfolio/governance-closure")
public class GovernanceClosureSignatureController {

    private final GovernanceClosureSignatureService service;

    public GovernanceClosureSignatureController(
            GovernanceClosureSignatureService service
    ) {
        this.service = service;
    }

    @GetMapping("/signing-readiness")
    public GovernanceSigningReadinessResponse readiness() {
        return service.readiness();
    }

    @GetMapping("/signatures")
    public GovernanceClosureSignatureHistoryResponse history() {
        return service.history();
    }

    @PostMapping("/signatures/{certificateId}")
    public GovernanceClosureSignature sign(
            @PathVariable
            UUID certificateId
    ) {
        return service.sign(
                certificateId
        );
    }

    @GetMapping("/verify-signatures")
    public GovernanceSignatureVerificationResponse verify() {
        return service.verify();
    }

    @GetMapping("/trust-anchors")
    public GovernanceTrustAnchorHistoryResponse trustAnchors() {
        return service.trustAnchorHistory();
    }

    @PostMapping("/trust-anchors")
    public GovernanceTrustAnchorView registerTrustAnchor(
            @Valid
            @RequestBody
            RegisterGovernanceTrustAnchorRequest request
    ) {
        return service.registerTrustAnchor(
                request
        );
    }

    @PostMapping("/trust-anchors/{keyId}/retire")
    public GovernanceTrustAnchorView retireTrustAnchor(
            @PathVariable
            String keyId,

            @Valid
            @RequestBody
            RetireGovernanceTrustAnchorRequest request
    ) {
        return service.retireTrustAnchor(
                keyId,
                request
        );
    }
}
