package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CreateGovernanceTrustAnchorTransitionRequest;
import com.terrascope.platform.api.model.GovernanceTrustAnchorTransition;
import com.terrascope.platform.api.model.GovernanceTrustAnchorTransitionHistoryResponse;
import com.terrascope.platform.api.model.GovernanceTrustAnchorTransitionVerificationResponse;
import com.terrascope.platform.service.GovernanceTrustAnchorTransitionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/resilience-portfolio/governance-closure/trust-transitions"
)
public class GovernanceTrustAnchorTransitionController {

    private final GovernanceTrustAnchorTransitionService service;

    public GovernanceTrustAnchorTransitionController(
            GovernanceTrustAnchorTransitionService service
    ) {
        this.service = service;
    }

    @GetMapping
    public GovernanceTrustAnchorTransitionHistoryResponse history() {
        return service.history();
    }

    @PostMapping("/rotate")
    public GovernanceTrustAnchorTransition rotate(
            @RequestBody
            CreateGovernanceTrustAnchorTransitionRequest request
    ) {
        return service.rotate(
                request
        );
    }

    @GetMapping("/verify")
    public GovernanceTrustAnchorTransitionVerificationResponse verify() {
        return service.verify();
    }
}
