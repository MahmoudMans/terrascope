package com.terrascope.platform.api;

import com.terrascope.platform.api.model.PortfolioObjectiveOutcomePreview;
import com.terrascope.platform.api.model.PortfolioObjectiveValidation;
import com.terrascope.platform.api.model.PortfolioObjectiveValidationHistoryResponse;
import com.terrascope.platform.service.PortfolioObjectiveOutcomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resilience-portfolio/objectives/{objectiveId}/outcomes")
public class PortfolioObjectiveOutcomeController {

    private final PortfolioObjectiveOutcomeService service;

    public PortfolioObjectiveOutcomeController(
            PortfolioObjectiveOutcomeService service
    ) {
        this.service = service;
    }

    @GetMapping("/preview")
    public PortfolioObjectiveOutcomePreview preview(
            @PathVariable
            UUID objectiveId
    ) {
        return service.preview(
                objectiveId
        );
    }

    @GetMapping
    public PortfolioObjectiveValidationHistoryResponse history(
            @PathVariable
            UUID objectiveId
    ) {
        return service.history(
                objectiveId
        );
    }

    @PostMapping
    public PortfolioObjectiveValidation validate(
            @PathVariable
            UUID objectiveId
    ) {
        return service.validate(
                objectiveId
        );
    }
}
