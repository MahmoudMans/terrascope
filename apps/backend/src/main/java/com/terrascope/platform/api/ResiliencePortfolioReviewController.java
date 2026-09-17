package com.terrascope.platform.api;

import com.terrascope.platform.api.model.ResiliencePortfolioReview;
import com.terrascope.platform.api.model.ResiliencePortfolioReviewHistoryResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioReviewPreview;
import com.terrascope.platform.service.ResiliencePortfolioReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resilience-portfolio")
public class ResiliencePortfolioReviewController {

    private final ResiliencePortfolioReviewService service;

    public ResiliencePortfolioReviewController(
            ResiliencePortfolioReviewService service
    ) {
        this.service = service;
    }

    @GetMapping("/review-preview")
    public ResiliencePortfolioReviewPreview preview() {
        return service.preview();
    }

    @GetMapping("/reviews")
    public ResiliencePortfolioReviewHistoryResponse history() {
        return service.history();
    }

    @PostMapping("/reviews")
    public ResiliencePortfolioReview createReview() {
        return service.createReview();
    }
}
