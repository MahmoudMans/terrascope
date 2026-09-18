package com.terrascope.platform.api;

import com.terrascope.platform.api.model.GovernanceClosureCertificate;
import com.terrascope.platform.api.model.GovernanceClosureCertificateHistoryResponse;
import com.terrascope.platform.api.model.GovernanceClosureReadinessResponse;
import com.terrascope.platform.api.model.GovernanceClosureVerificationResponse;
import com.terrascope.platform.service.GovernanceClosureCertificateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resilience-portfolio/governance-closure")
public class GovernanceClosureCertificateController {

    private final GovernanceClosureCertificateService service;

    public GovernanceClosureCertificateController(
            GovernanceClosureCertificateService service
    ) {
        this.service = service;
    }

    @GetMapping("/readiness")
    public GovernanceClosureReadinessResponse readiness() {
        return service.readiness();
    }

    @GetMapping("/certificates")
    public GovernanceClosureCertificateHistoryResponse history() {
        return service.history();
    }

    @PostMapping("/certificates")
    public GovernanceClosureCertificate certify() {
        return service.certify();
    }

    @GetMapping("/verify")
    public GovernanceClosureVerificationResponse verify() {
        return service.verify();
    }
}
