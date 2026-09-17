package com.terrascope.platform.api;

import com.terrascope.platform.api.model.AfterActionReviewHistoryResponse;
import com.terrascope.platform.api.model.IncidentAfterActionReview;
import com.terrascope.platform.api.model.IncidentClosureReadinessResponse;
import com.terrascope.platform.service.IncidentClosureReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}")
public class IncidentClosureReviewController {

    private final IncidentClosureReviewService service;

    public IncidentClosureReviewController(
            IncidentClosureReviewService service
    ) {
        this.service = service;
    }

    @GetMapping("/closure-readiness")
    public IncidentClosureReadinessResponse readiness(
            @PathVariable
            UUID incidentId
    ) {
        return service.readiness(
                incidentId
        );
    }

    @GetMapping("/after-action-reviews")
    public AfterActionReviewHistoryResponse history(
            @PathVariable
            UUID incidentId
    ) {
        return service.history(
                incidentId
        );
    }

    @PostMapping("/after-action-reviews")
    public IncidentAfterActionReview createReview(
            @PathVariable
            UUID incidentId
    ) {
        return service.createReview(
                incidentId
        );
    }
}
