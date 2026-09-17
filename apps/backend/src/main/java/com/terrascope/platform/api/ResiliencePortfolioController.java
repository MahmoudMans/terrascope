package com.terrascope.platform.api;

import com.terrascope.platform.api.model.ResiliencePortfolioHistoryResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.service.ResiliencePortfolioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resilience-portfolio")
public class ResiliencePortfolioController {

    private final ResiliencePortfolioService service;

    public ResiliencePortfolioController(
            ResiliencePortfolioService service
    ) {
        this.service = service;
    }

    @GetMapping
    public ResiliencePortfolioResponse portfolio() {
        return service.portfolio();
    }

    @GetMapping("/history")
    public ResiliencePortfolioHistoryResponse history() {
        return service.history();
    }

    @PostMapping("/snapshots")
    public ResiliencePortfolioSnapshot createSnapshot() {
        return service.createSnapshot();
    }
}
