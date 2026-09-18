package com.terrascope.platform.api;

import com.terrascope.platform.api.model.CompletePortfolioObjectiveRequest;
import com.terrascope.platform.api.model.CreatePortfolioObjectiveRequest;
import com.terrascope.platform.api.model.PortfolioObjectiveBoardResponse;
import com.terrascope.platform.api.model.PortfolioObjectiveHistoryResponse;
import com.terrascope.platform.api.model.UpdatePortfolioObjectiveOwnerRequest;
import com.terrascope.platform.api.model.UpdatePortfolioObjectiveProgressRequest;
import com.terrascope.platform.api.model.UpdatePortfolioObjectiveStatusRequest;
import com.terrascope.platform.api.model.UpdatePortfolioObjectiveTargetRequest;
import com.terrascope.platform.service.ResiliencePortfolioObjectiveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resilience-portfolio/objectives")
public class ResiliencePortfolioObjectiveController {

    private final ResiliencePortfolioObjectiveService service;

    public ResiliencePortfolioObjectiveController(
            ResiliencePortfolioObjectiveService service
    ) {
        this.service = service;
    }

    @GetMapping
    public PortfolioObjectiveBoardResponse board() {
        return service.board();
    }

    @PostMapping
    public PortfolioObjectiveBoardResponse create(
            @Valid
            @RequestBody
            CreatePortfolioObjectiveRequest request
    ) {
        return service.create(
                request
        );
    }

    @GetMapping("/{objectiveId}/history")
    public PortfolioObjectiveHistoryResponse history(
            @PathVariable
            UUID objectiveId
    ) {
        return service.history(
                objectiveId
        );
    }

    @PatchMapping("/{objectiveId}/status")
    public PortfolioObjectiveBoardResponse updateStatus(
            @PathVariable
            UUID objectiveId,

            @Valid
            @RequestBody
            UpdatePortfolioObjectiveStatusRequest request
    ) {
        return service.updateStatus(
                objectiveId,
                request
        );
    }

    @PatchMapping("/{objectiveId}/owner")
    public PortfolioObjectiveBoardResponse updateOwner(
            @PathVariable
            UUID objectiveId,

            @Valid
            @RequestBody
            UpdatePortfolioObjectiveOwnerRequest request
    ) {
        return service.updateOwner(
                objectiveId,
                request
        );
    }

    @PatchMapping("/{objectiveId}/target")
    public PortfolioObjectiveBoardResponse updateTarget(
            @PathVariable
            UUID objectiveId,

            @RequestBody
            UpdatePortfolioObjectiveTargetRequest request
    ) {
        return service.updateTarget(
                objectiveId,
                request
        );
    }

    @PatchMapping("/{objectiveId}/progress")
    public PortfolioObjectiveBoardResponse updateProgress(
            @PathVariable
            UUID objectiveId,

            @Valid
            @RequestBody
            UpdatePortfolioObjectiveProgressRequest request
    ) {
        return service.updateProgress(
                objectiveId,
                request
        );
    }

    @PostMapping("/{objectiveId}/complete")
    public PortfolioObjectiveBoardResponse complete(
            @PathVariable
            UUID objectiveId,

            @Valid
            @RequestBody
            CompletePortfolioObjectiveRequest request
    ) {
        return service.complete(
                objectiveId,
                request
        );
    }
}
